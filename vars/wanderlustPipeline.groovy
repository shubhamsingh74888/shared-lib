// ============================================================
//  vars/wanderlustPipeline.groovy
//  Master pipeline orchestrator.
// ============================================================
import org.wanderlust.PipelineConfig
import org.wanderlust.Utils

def call(Map args = [:]) {

  PipelineConfig cfg
  Utils          utils

  pipeline {

    agent any

    triggers {
      pollSCM('H/5 * * * *')
    }

    parameters {
      choice(
        name: 'DEPLOY_ENVIRONMENT',
        choices: ['dev', 'staging', 'production'],
        description: 'Target deployment environment'
      )
      booleanParam(
        name: 'SKIP_TESTS',
        defaultValue: false,
        description: 'Skip unit tests — emergency hotfix only'
      )
      booleanParam(
        name: 'SKIP_SECURITY_SCAN',
        defaultValue: false,
        description: 'Skip all security scans — emergency hotfix only'
      )
      booleanParam(
        name: 'FORCE_PUSH',
        defaultValue: false,
        description: 'Push images even if security scans have warnings'
      )
    }

    options {
      buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
      timestamps()
      timeout(time: args.timeoutMinutes ?: 60, unit: 'MINUTES')
      disableConcurrentBuilds(abortPrevious: true)
      ansiColor('xterm')
    }

    environment {
      DOCKERHUB_CREDS = credentials("${args.dockerCredId ?: 'dockerhub-creds'}")
    }

    stages {

      stage('01 · Pipeline Init') {
        steps {
          script {
            cfg   = new PipelineConfig(args, env, params)
            utils = new Utils(this)
            wanderlustInit(cfg, utils)
          }
        }
      }

      stage('02 · Source Checkout') {
        steps {
          script { wanderlustCheckout(cfg, utils) }
        }
      }

      stage('03 · Install Dependencies') {
        parallel {
          stage('Frontend · npm ci') {
            steps {
              script { wanderlustDeps(this, cfg, utils, 'frontend') }
            }
          }
          stage('Backend · npm ci') {
            steps {
              script { wanderlustDeps(this, cfg, utils, 'backend') }
            }
          }
        }
      }

      stage('03.5 · NPM Security Audit Fix') {
        when { not { expression { cfg.skipSecurityScan } } }
        parallel {
          stage('Frontend · Audit Fix') {
            steps {
              script { wanderlustSecurity.npmAuditFix(cfg, utils, 'frontend') }
            }
          }
          stage('Backend · Audit Fix') {
            steps {
              script { wanderlustSecurity.npmAuditFix(cfg, utils, 'backend') }
            }
          }
        }
      }

      stage('04 · Unit Tests') {
        when { not { expression { cfg.skipTests } } }
        parallel {
          stage('Frontend · Tests') {
            steps {
              script { wanderlustTest(this, cfg, utils, 'frontend') }
            }
          }
          stage('Backend · Tests') {
            steps {
              script { wanderlustTest(this, cfg, utils, 'backend') }
            }
          }
        }
      }

      stage('05 · SAST · SonarQube') {
        when { not { expression { cfg.skipSecurityScan } } }
        steps {
          script { wanderlustSecurity.sonarScan(cfg, utils) }
        }
      }

      stage('06 · Quality Gate') {
        when { not { expression { cfg.skipSecurityScan } } }
        steps {
          script { wanderlustSecurity.qualityGate(cfg, utils) }
        }
      }

      stage('07 · SCA & Filesystem Security') {
        when { not { expression { cfg.skipSecurityScan } } }
        parallel {
          stage('OWASP · Dependency Check') {
            steps {
              script { wanderlustSecurity.owaspScan(cfg, utils) }
            }
          }
          stage('Trivy · Filesystem Scan') {
            steps {
              script { wanderlustSecurity.trivyFsScan(cfg, utils) }
            }
          }
        }
      }

      stage('08 · Build Docker Images') {
        parallel {
          stage('Build · Frontend') {
            steps {
              script { wanderlustBuild.buildImage(cfg, utils, 'frontend') }
            }
          }
          stage('Build · Backend') {
            steps {
              script { wanderlustBuild.buildImage(cfg, utils, 'backend') }
            }
          }
        }
      }

      stage('09 · Container Image Scan') {
        when { not { expression { cfg.skipSecurityScan } } }
        parallel {
          stage('Trivy · Frontend Image') {
            steps {
              script { wanderlustSecurity.trivyImageScan(cfg, utils, 'frontend') }
            }
          }
          stage('Trivy · Backend Image') {
            steps {
              script { wanderlustSecurity.trivyImageScan(cfg, utils, 'backend') }
            }
          }
        }
      }

      stage('10 · Push to Registry') {
        steps {
          script { wanderlustBuild.pushImages(cfg, utils) }
        }
        post {
          always {
            sh 'docker logout || true'
          }
        }
      }

      stage('11 · GitOps · Manifest Update') {
        steps {
          script { wanderlustDeploy.gitopsUpdate(cfg, utils) }
        }
      }

      stage('12 · Trigger CD') {
        steps {
          script {
            build job: 'wanderlust-cd',
                  parameters: [
                    string(name: 'IMAGE_TAG', value: "${cfg.buildNumber}")
                  ],
                  wait: false
          }
        }
      }

    } // Closes stages

    post {
      always {
        script { wanderlustPost.always(cfg, utils, currentBuild) }
      }
      success {
        script { wanderlustPost.onSuccess(cfg, utils) }
      }
      failure {
        script { wanderlustPost.onFailure(cfg, utils) }
      }
      unstable {
        script { wanderlustPost.onUnstable(cfg, utils) }
      }
    }

  } // Closes pipeline
} // Closes def call()
