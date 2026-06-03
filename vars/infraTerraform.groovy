// ============================================================
//  vars/infraTerraform.groovy
//  Terraform stage implementations
//  Exposes:
//    infraTerraform.init()
//    infraTerraform.reconcileAndPlan()
//    infraTerraform.apply()
//    infraTerraform.destroy()
// ============================================================

def init() {
  withCredentials([usernamePassword(
    credentialsId   : 'aws-creds',
    usernameVariable: 'AWS_ACCESS_KEY_ID',
    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
  )]) {
    dir('terraform') {
      sh '''
        export PATH=/usr/local/bin:/usr/bin:/bin:$PATH
        terraform init -backend-config=${BACKEND_CONFIG} -reconfigure
        terraform workspace select ${ENV_NAME} || terraform workspace new ${ENV_NAME}
        echo "[INIT] ✔ Workspace: ${ENV_NAME}"
      '''
    }
  }
}

def reconcileAndPlan() {
  withCredentials([usernamePassword(
    credentialsId   : 'aws-creds',
    usernameVariable: 'AWS_ACCESS_KEY_ID',
    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
  )]) {
    dir('terraform') {
      sh '''
        export PATH=/usr/local/bin:/usr/bin:/bin:$PATH
        rm -f tfplan

        ROLE_NAME="wanderlust-${ENV_NAME}-jenkins-role"
        PROFILE_NAME="wanderlust-${ENV_NAME}-jenkins-profile"
        CUSTOM_POLICY="${ROLE_NAME}:wanderlust-${ENV_NAME}-jenkins-custom"
        SERVER_TAG="wanderlust-${ENV_NAME}-cicd-server"
        EIP_TAG="wanderlust-${ENV_NAME}-cicd-eip"

        echo "[RECONCILE] Removing stale helm state entries..."
        terraform state rm 'module.eks[0].helm_release.prometheus[0]' || true
        terraform state rm 'module.eks[0].helm_release.argocd[0]'     || true

        terraform state show 'module.cicd_server.aws_iam_role.jenkins' > /dev/null 2>&1 || \
          terraform import -var-file="${TF_VAR_FILE}" \
            'module.cicd_server.aws_iam_role.jenkins' "$ROLE_NAME" || true

        terraform state show 'module.cicd_server.aws_iam_instance_profile.jenkins' > /dev/null 2>&1 || \
          terraform import -var-file="${TF_VAR_FILE}" \
            'module.cicd_server.aws_iam_instance_profile.jenkins' "$PROFILE_NAME" || true

        terraform state show 'module.cicd_server.aws_iam_role_policy.jenkins_custom' > /dev/null 2>&1 || \
          terraform import -var-file="${TF_VAR_FILE}" \
            'module.cicd_server.aws_iam_role_policy.jenkins_custom' "$CUSTOM_POLICY" || true

        terraform state show 'module.cicd_server.aws_iam_role_policy_attachment.ssm' > /dev/null 2>&1 || \
          terraform import -var-file="${TF_VAR_FILE}" \
            'module.cicd_server.aws_iam_role_policy_attachment.ssm' \
            "$ROLE_NAME/arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore" || true

        terraform state show 'module.cicd_server.aws_iam_role_policy_attachment.ecr' > /dev/null 2>&1 || \
          terraform import -var-file="${TF_VAR_FILE}" \
            'module.cicd_server.aws_iam_role_policy_attachment.ecr' \
            "$ROLE_NAME/arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryFullAccess" || true

        INSTANCE_ID=$(aws ec2 describe-instances \
          --filters "Name=tag:Name,Values=$SERVER_TAG" \
                    "Name=instance-state-name,Values=running,stopped" \
          --query 'Reservations[0].Instances[0].InstanceId' \
          --output text --region ${AWS_DEFAULT_REGION} 2>/dev/null || echo "")

        if [ -n "$INSTANCE_ID" ] && [ "$INSTANCE_ID" != "None" ]; then
          terraform state show 'module.cicd_server.aws_instance.cicd' > /dev/null 2>&1 || \
            terraform import -var-file="${TF_VAR_FILE}" \
              'module.cicd_server.aws_instance.cicd' "$INSTANCE_ID" || true
        fi

        EIP_ALLOC=$(aws ec2 describe-addresses \
          --filters "Name=tag:Name,Values=$EIP_TAG" \
          --query 'Addresses[0].AllocationId' \
          --output text --region ${AWS_DEFAULT_REGION} 2>/dev/null || echo "")

        if [ -n "$EIP_ALLOC" ] && [ "$EIP_ALLOC" != "None" ]; then
          terraform state show 'module.cicd_server.aws_eip.cicd' > /dev/null 2>&1 || \
            terraform import -var-file="${TF_VAR_FILE}" \
              'module.cicd_server.aws_eip.cicd' "$EIP_ALLOC" || true
        fi

        echo "[RECONCILE] ✔ State reconciliation complete."

        set +e
        terraform plan -var-file="${TF_VAR_FILE}" -out=tfplan -detailed-exitcode
        PLAN_EXIT=$?
        set -e

        if [ $PLAN_EXIT -eq 1 ]; then
          echo "[PLAN] ✘ Terraform plan failed."
          exit 1
        elif [ $PLAN_EXIT -eq 0 ]; then
          echo "[PLAN] ✔ No changes needed."
        else
          PLAN_SUMMARY=$(terraform show -no-color tfplan | grep -E '^Plan:|^No changes' || echo "Changes pending")
          echo "[PLAN] ✔ $PLAN_SUMMARY"
          echo "$PLAN_SUMMARY" > /tmp/plan-summary.txt
        fi
      '''
    }
  }
}

def apply() {
  withCredentials([usernamePassword(
    credentialsId   : 'aws-creds',
    usernameVariable: 'AWS_ACCESS_KEY_ID',
    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
  )]) {
    dir('terraform') {
      sh '''
        export PATH=/usr/local/bin:/usr/bin:/bin:$PATH
        rm -f /tmp/plan-summary.txt
        [ ! -f tfplan ] && echo "[APPLY] ✘ tfplan not found — run plan first." && exit 1
        echo "[APPLY] Applying plan to ${ENV_NAME}..."
        terraform apply -auto-approve tfplan
        echo "[APPLY] ✔ Infrastructure applied."
      '''
    }
  }
}

def destroy() {
  withCredentials([usernamePassword(
    credentialsId   : 'aws-creds',
    usernameVariable: 'AWS_ACCESS_KEY_ID',
    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
  )]) {
    dir('terraform') {
      sh '''
        export PATH=/usr/local/bin:/usr/bin:/bin:$PATH
        echo "[DESTROY] ⚠ Destroying ${ENV_NAME} infrastructure..."
        terraform destroy -var-file="${TF_VAR_FILE}" -auto-approve
        echo "[DESTROY] ✔ Destroy complete."
      '''
    }
  }
}
