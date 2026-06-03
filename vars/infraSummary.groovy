// ============================================================
//  vars/infraSummary.groovy
//  Stage 10 · Deployment Summary Report
//  Prints ArgoCD URL, Jenkins URL, cluster info after apply
// ============================================================

private String resolveKubectl() {
  return '''
    KUBECTL_BIN=""
    if   [ -x /usr/local/bin/kubectl   ]; then KUBECTL_BIN=/usr/local/bin/kubectl
    elif [ -x /usr/bin/kubectl         ]; then KUBECTL_BIN=/usr/bin/kubectl
    elif [ -x /tmp/kubectl-bin/kubectl ]; then KUBECTL_BIN=/tmp/kubectl-bin/kubectl
    else
      mkdir -p /tmp/kubectl-bin
      curl -fsSL "https://dl.k8s.io/release/v1.30.0/bin/linux/amd64/kubectl" \
        -o /tmp/kubectl-bin/kubectl
      chmod +x /tmp/kubectl-bin/kubectl
      KUBECTL_BIN=/tmp/kubectl-bin/kubectl
    fi
    export PATH=$(dirname "$KUBECTL_BIN"):$PATH
  '''
}

def call() {
  withCredentials([usernamePassword(
    credentialsId   : 'aws-creds',
    usernameVariable: 'AWS_ACCESS_KEY_ID',
    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
  )]) {
    sh """
      export PATH=/usr/local/bin:/usr/bin:/bin:\$PATH
      aws eks update-kubeconfig \
        --name \${CLUSTER_NAME} --region \${AWS_DEFAULT_REGION} 2>/dev/null || true

      ${resolveKubectl()}

      ARGOCD_LB=\$(\$KUBECTL_BIN get svc argocd-server -n argocd \
        -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Pending")
      JENKINS_IP=\$(aws ec2 describe-addresses \
        --filters "Name=tag:Name,Values=wanderlust-\${ENV_NAME}-cicd-eip" \
        --query 'Addresses[0].PublicIp' --output text \
        --region \${AWS_DEFAULT_REGION} 2>/dev/null || echo "Not found")

      echo ""
      echo "╔══════════════════════════════════════════════════════╗"
      echo "║         WANDERLUST INFRA — DEPLOYMENT SUMMARY        ║"
      echo "╚══════════════════════════════════════════════════════╝"
      echo "  Environment : \${ENV_NAME}"
      echo "  Cluster     : \${CLUSTER_NAME}"
      echo "  Region      : \${AWS_DEFAULT_REGION}"
      echo "  ArgoCD URL  : https://\$ARGOCD_LB"
      echo "  Jenkins URL : http://\$JENKINS_IP:8080"
      echo "══════════════════════════════════════════════════════"
      echo "✅ Deployment complete."
    """
  }
}
