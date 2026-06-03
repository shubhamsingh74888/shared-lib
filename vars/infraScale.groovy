// ============================================================
//  vars/infraScale.groovy
//  Stage 09 · Ensure minimum node count
//  Scales nodegroup to desiredSize=2 if below threshold
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

      NODEGROUP=\$(aws eks list-nodegroups \
        --cluster-name \${CLUSTER_NAME} --region \${AWS_DEFAULT_REGION} \
        --query 'nodegroups[0]' --output text 2>/dev/null || echo "")

      if [ -n "\$NODEGROUP" ] && [ "\$NODEGROUP" != "None" ]; then
        CURRENT=\$(aws eks describe-nodegroup \
          --cluster-name \${CLUSTER_NAME} --nodegroup-name "\$NODEGROUP" \
          --region \${AWS_DEFAULT_REGION} \
          --query 'nodegroup.scalingConfig.desiredSize' \
          --output text 2>/dev/null || echo "0")
        echo "[SCALE] Current desired nodes: \$CURRENT"
        if [ "\$CURRENT" -lt 2 ]; then
          echo "[SCALE] Scaling up to 2 nodes..."
          aws eks update-nodegroup-config \
            --cluster-name \${CLUSTER_NAME} --nodegroup-name "\$NODEGROUP" \
            --scaling-config minSize=1,maxSize=3,desiredSize=2 \
            --region \${AWS_DEFAULT_REGION} 2>/dev/null || true
          ${resolveKubectl()}
          \$KUBECTL_BIN wait --for=condition=Ready nodes --all --timeout=300s || true
        else
          echo "[SCALE] ✔ Already at \$CURRENT nodes — no scaling needed."
        fi
      fi
    """
  }
}
