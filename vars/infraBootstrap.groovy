// ============================================================
//  vars/infraBootstrap.groovy
//  Stage 08 · Bootstrap ArgoCD + GitOps
//  Runs bootstrap-gitops.sh after EKS cluster is ready
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
      export PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:\$PATH

      STATUS=\$(aws eks describe-cluster \
        --name \${CLUSTER_NAME} --region \${AWS_DEFAULT_REGION} \
        --query 'cluster.status' --output text 2>/dev/null || echo "NOT_FOUND")
      echo "[BOOTSTRAP] Cluster status: \$STATUS"
      [ "\$STATUS" != "ACTIVE" ] && echo "[BOOTSTRAP] ⚠ Not ACTIVE — skipping." && exit 0

      aws eks update-kubeconfig --name \${CLUSTER_NAME} --region \${AWS_DEFAULT_REGION}

      ${resolveKubectl()}

      echo "[BOOTSTRAP] kubectl: \$(\$KUBECTL_BIN version --client --short 2>/dev/null)"
      \$KUBECTL_BIN wait --for=condition=Ready nodes --all --timeout=300s || true

      chmod +x bootstrap-gitops.sh
      KUBECTL_PATH="\$KUBECTL_BIN" ./bootstrap-gitops.sh
      echo "[BOOTSTRAP] ✔ ArgoCD bootstrap complete."
    """
  }
}
