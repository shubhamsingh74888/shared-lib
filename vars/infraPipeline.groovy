// ============================================================
//  vars/infraPipeline.groovy
//  Infrastructure Pipeline orchestrator — shared library entry point.
//  Handles: Terraform apply + destroy for EKS + Jenkins EC2 + ArgoCD
// ============================================================

def call(Map args = [:]) {

  pipeline {

    agent any

    tools {
      terraform 'terraform-latest'
    }

    environment {
      AWS_DEFAULT_REGION = "${params.AWS_REGION}"
      ENV_NAME           = "${params.ENVIRONMENT}"
      CLUSTER_NAME       = "wanderlust-${params.ENVIRONMENT}-eks"
      TF_VAR_FILE        = "terraform/env/${params.ENVIRONMENT}.tfvars"
      BACKEND_CONFIG     = "terraform/backend-configs/${params.ENVIRONMENT}.hcl"
      INFRA_REPO         = "${params.INFRA_REPO_URL}"
      PROJECT_REPO       = "${params.PROJECT_REPO_URL}"
      PATH               = "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${env.PATH}"
    }

    options {
      buildDiscarder(logRotator(numToKeepStr: '10'))
      timestamps()
      timeout(time: 90, unit: 'MINUTES')
      disableConcurrentBuilds()
      ansiColor('xterm')
    }

    parameters {
      choice(
        name: 'ACTION',
        choices: ['apply', 'plan-only', 'destroy'],
        description: 'Pipeline execution action'
      )
      choice(
        name: 'ENVIRONMENT',
        choices: ['prod', 'staging', 'dev'],
        description: 'Target environment'
      )
      string(
        name: 'AWS_REGION',
        defaultValue: 'ap-south-1',
        description: 'Target AWS Region'
      )
      string(
        name: 'INFRA_REPO_URL',
        defaultValue: args.infraRepoUrl ?: 'https://github.com/shubhamsingh74888/remote-infra.git',
        description: 'Repository for remote backend bootstrap'
      )
      string(
        name: 'PROJECT_REPO_URL',
        defaultValue: args.projectRepoUrl ?: 'https://github.com/shubhamsingh74888/wanderlust-infra.git',
        description: 'Main project repository'
      )
      booleanParam(
        name: 'SKIP_BOOTSTRAP',
        defaultValue: false,
        description: 'Skip ArgoCD bootstrap'
      )
      booleanParam(
        name: 'BOOTSTRAP_REMOTE_INFRA',
        defaultValue: false,
        description: 'Run Stage 00 — create S3 + DynamoDB (first-time only)'
      )
    }

    stages {

      // ── Stage 00 · Remote Backend Bootstrap ──────────────
      stage('00 · Remote Backend · Bootstrap') {
        when { expression { params.BOOTSTRAP_REMOTE_INFRA == true } }
        steps {
          script { infraRemoteBackend() }
        }
      }

      // ── Stage 01 · Checkout ───────────────────────────────
      stage('01 · Checkout · wanderlust-infra') {
        steps {
          git branch: 'main', credentialsId: 'github', url: "${env.PROJECT_REPO}"
          echo "[CHECKOUT] ✔ Checked out wanderlust-infra — env: ${env.ENV_NAME}"
        }
      }

      // ── Stage 02 · Terraform Init ─────────────────────────
      stage('02 · Terraform · Init') {
        steps {
          script { infraTerraform.init() }
        }
      }

      // ── Stage 03 · Reconcile + Plan ───────────────────────
      stage('03 · Terraform · Reconcile + Plan') {
        steps {
          script { infraTerraform.reconcileAndPlan() }
        }
      }

      // ── Stage 04 · Approval Gate ──────────────────────────
      stage('04 · Approval Gate') {
        when {
          expression { params.ACTION == 'apply' || params.ACTION == 'destroy' }
        }
        steps {
          script {
            def summary = sh(
              script: "cat /tmp/plan-summary.txt 2>/dev/null || echo 'No changes'",
              returnStdout: true
            ).trim()
            timeout(time: 15, unit: 'MINUTES') {
              input(
                message: "Approve ${params.ACTION.toUpperCase()} on ${env.ENV_NAME.toUpperCase()}?\n\nPlan: ${summary}",
                ok: "Yes, ${params.ACTION.toUpperCase()} it"
              )
            }
          }
        }
      }

      // ── Stage 05 · Terraform Apply ────────────────────────
      stage('05 · Terraform · Apply') {
        when { expression { params.ACTION == 'apply' } }
        steps {
          script { infraTerraform.apply() }
        }
      }

      // ── Stage 06 · Pre-Destroy Cleanup ────────────────────
      stage('06 · Pre-Destroy · Cleanup') {
        when { expression { params.ACTION == 'destroy' } }
        steps {
          script { infraCleanup() }
        }
      }

      // ── Stage 07 · Terraform Destroy ──────────────────────
      stage('07 · Terraform · Destroy') {
        when { expression { params.ACTION == 'destroy' } }
        steps {
          script { infraTerraform.destroy() }
        }
      }

      // ── Stage 08 · Bootstrap ArgoCD + GitOps ──────────────
      stage('08 · Bootstrap · ArgoCD + GitOps') {
        when {
          allOf {
            expression { params.ACTION == 'apply' }
            expression { params.SKIP_BOOTSTRAP == false }
          }
        }
        steps {
          script { infraBootstrap() }
        }
      }

      // ── Stage 09 · Scale · Ensure 2 Nodes ─────────────────
      stage('09 · Scale · Ensure 2 Nodes') {
        when { expression { params.ACTION == 'apply' } }
        steps {
          script { infraScale() }
        }
      }

      // ── Stage 10 · Deployment Summary ─────────────────────
      stage('10 · Summary · Deployment Report') {
        when { expression { params.ACTION == 'apply' } }
        steps {
          script { infraSummary() }
        }
      }

    }

    post {
      success { echo "✅ Infra pipeline PASSED — Env: ${env.ENV_NAME} | Action: ${params.ACTION}" }
      failure { echo "❌ Infra pipeline FAILED — Env: ${env.ENV_NAME} | Action: ${params.ACTION}" }
      always  { cleanWs() }
    }

  }
}
