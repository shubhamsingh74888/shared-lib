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
      NVD_API_KEY     = credentials("${args.nvdApiKeyId  ?: 'nvd-api-token'}")
    }

    stages {

      // ── 01 · Init ──────────────────────────────────────
      stage('01 · Pipeline Init') {
        steps {
          script {
            cfg   = new PipelineConfig(args, env, params)
            utils = new Utils(this)
            pipelineInit(cfg, utils)
          }
        }
      }

      // ── 02 · Checkout ──────────────────────────────────
      stage('02 · Source Checkout') {
        steps {
          script {
            pipelineCheckout(cfg, utils)
          }
        }
      }

      // ── 03 · Dependencies ──────────────────────────────
      stage('03 · Install Dependencies') {
        parallel {
          stage('Frontend · npm ci') {
            steps {
              script { pipelineDeps(this, cfg, utils, 'frontend') }
            }
          }
          stage('Backend · npm ci') {
            steps {
              script { pipelineDeps(this, cfg, utils, 'backend') }
            }
          }
        }
      }

      // ── 03.5 · NPM Security Audit Fix ──────────────────
      // Runs BEFORE tests and OWASP scan so patched packages
      // flow through every downstream stage automatically.
      stage('03.5 · NPM Security Audit Fix') {
        when { not { expression { cfg.skipSecurityScan } } }
        parallel {
          stage('Frontend · Audit Fix') {
            steps {
              script { pipelineSecurity.npmAuditFix(cfg, utils, 'frontend') }
            }
          }
          stage('Backend · Audit Fix') {
            steps {
              script { pipelineSecurity.npmAuditFix(cfg, utils, 'backend') }
            }
          }
        }
      }

      // ── 04 · Unit Tests ────────────────────────────────
      stage('04 · Unit Tests') {
        when { not { expression { cfg.skipTests } } }
        parallel {
          stage('Frontend · Tests') {
            steps {
              script { pipelineTest(this, cfg, utils, 'frontend') }
            }
          }
          stage('Backend · Tests') {
            steps {
              script { pipelineTest(this, cfg, utils, 'backend') }
            }
          }
        }
      }

      // ── 05 · SAST ──────────────────────────────────────
      stage('05 · SAST · SonarQube') {
        when { not { expression { cfg.skipSecurityScan } } }
        steps {
          script { pipelineSecurity.sonarScan(cfg, utils) }
        }
      }

      // ── 06 · Quality Gate ──────────────────────────────
      stage('06 · Quality Gate') {
        when { not { expression { cfg.skipSecurityScan } } }
        steps {
          script { pipelineSecurity.qualityGate(cfg, utils) }
        }
      }

      // ── 07 · SCA + Filesystem Scans ────────────────────
      stage('07 · SCA & Filesystem Security') {
        when { not { expression { cfg.skipSecurityScan } } }
        parallel {
          stage('OWASP · Dependency Check') {
            steps {
              script { pipelineSecurity.owaspScan(cfg, utils) }
            }
          }
          stage('Trivy · Filesystem Scan') {
            steps {
              script { pipelineSecurity.trivyFsScan(cfg, utils) }
            }
          }
        }
      }

      // ── 08 · Build Docker Images ──────────────────────
      stage('08 · Build Docker Images') {
        parallel {
          stage('Build · Frontend') {
            steps {
              script { pipelineBuild.buildImage(cfg, utils, 'frontend') }
            }
          }
          stage('Build · Backend') {
            steps {
              script { pipelineBuild.buildImage(cfg, utils, 'backend') }
            }
          }
        }
      }

      // ── 09 · Image Security Scans ──────────────────────
      stage('09 · Container Image Scan') {
        when { not { expression { cfg.skipSecurityScan } } }
        parallel {
          stage('Trivy · Frontend Image') {
            steps {
              script { pipelineSecurity.trivyImageScan(cfg, utils, 'frontend') }
            }
          }
          stage('Trivy · Backend Image') {
            steps {
              script { pipelineSecurity.trivyImageScan(cfg, utils, 'backend') }
            }
          }
        }
      }

      // ── 10 · Push to Registry ──────────────────────────
      stage('10 · Push to Registry') {
        steps {
          script { pipelineBuild.pushImages(cfg, utils) }
        }
        post {
          always {
            sh 'docker logout || true'
          }
        }
      }

      // ── 11 · GitOps Manifest Update ────────────────────
      stage('11 · GitOps · Manifest Update') {
        when {
          anyOf {
            branch 'main'
            branch 'staging'
            expression { cfg.deployEnvironment == 'production' }
          }
        }
        steps {
          script { pipelineDeploy.gitopsUpdate(cfg, utils) }
        }
      }

    }

    post {
      always {
        script { pipelinePost.always(cfg, utils, currentBuild) }
      }
      success {
        script { pipelinePost.onSuccess(cfg, utils) }
      }
      failure {
        script { pipelinePost.onFailure(cfg, utils) }
      }
      unstable {
        script { pipelinePost.onUnstable(cfg, utils) }
      }
    }

  }
}
