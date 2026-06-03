// ============================================================
//  vars/infraRemoteBackend.groovy
//  Stage 00 · Remote Backend Bootstrap
//  Creates S3 bucket + DynamoDB table for Terraform state
//  Run once per environment on first-time setup
// ============================================================

def call() {
  withCredentials([usernamePassword(
    credentialsId   : 'aws-creds',
    usernameVariable: 'AWS_ACCESS_KEY_ID',
    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
  )]) {
    dir('remote-backend') {
      sh '''
        export PATH=/usr/local/bin:/usr/bin:/bin:$PATH
        ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
        echo "[REMOTE-BACKEND] Account: $ACCOUNT_ID | Region: $AWS_DEFAULT_REGION"
        terraform init
        terraform workspace select ${ENV_NAME} || terraform workspace new ${ENV_NAME}
        terraform plan  -out=remote-backend.tfplan
        terraform apply -auto-approve remote-backend.tfplan
        rm -f remote-backend.tfplan
        echo "[REMOTE-BACKEND] ✔ S3 bucket + DynamoDB ready."
      '''
    }
  }
}
