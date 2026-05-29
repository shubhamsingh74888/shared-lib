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
      // NVD_API_KEY is loaded optionally inside owaspScan() — not here.
      // Keeping it here would crash the entire pipeline if the credential is missing.
      DOCKERHUB_CREDS = credentials("${args.dockerCredId ?: 'dockerhub-creds'}")
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
          script { pipelineCheckout(cfg, utils) }
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

      // ── 05 · SAST ────────────────
