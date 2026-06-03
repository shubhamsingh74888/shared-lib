// ============================================================
//  vars/infraCleanup.groovy
//  Stage 06 · Pre-Destroy Cleanup
//  Deletes LoadBalancers and cleans security group rules
//  so Terraform can destroy VPC subnets cleanly
// ============================================================

// Shared kubectl resolver — finds kubectl regardless of install path
private String resolveKubectl() {
  return '''
    KUBECTL_BIN=""
    if   [ -x /usr/local/bin/kubectl   ]; then KUBECTL_BIN=/usr/local/bin/kubectl
    elif [ -x /usr/bin/kubectl         ]; then KUBECTL_BIN=/usr/bin/kubectl
    elif [ -x /tmp/kubectl-bin/kubectl ]; then KUBECTL_BIN=/tmp/kubectl-bin/kubectl
    else
      echo "[KUBECTL] Not found — downloading..."
      mkdir -p /tmp/kubectl-bin
      curl -fsSL "https://dl.k8s.io/release/v1.30.0/bin/linux/amd64/kubectl" \
        -o /tmp/kubectl-bin/kubectl
      chmod +x /tmp/kubectl-bin/kubectl
      KUBECTL_BIN=/tmp/kubectl-bin/kubectl
    fi
    export PATH=$(dirname "$KUBECTL_BIN"):$PATH
    echo "[KUBECTL] Using: $KUBECTL_BIN"
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
      echo "[CLEANUP] ══════════════════════════════════════"
      echo "[CLEANUP] Pre-destroy LoadBalancer cleanup"
      echo "[CLEANUP] ══════════════════════════════════════"

      ${resolveKubectl()}

      aws eks update-kubeconfig \
        --name \${CLUSTER_NAME} \
        --region \${AWS_DEFAULT_REGION} 2>/dev/null || \
        echo "[CLEANUP] ⚠ EKS unreachable — using AWS CLI only"

      # ── Delete LB services via kubectl ─────────────────
      for ns in wanderlust argocd monitoring kube-system default; do
        \$KUBECTL_BIN get svc -n \$ns --field-selector spec.type=LoadBalancer \
          --no-headers 2>/dev/null | awk '{print \$1}' | while read svc; do
          echo "[CLEANUP] Deleting svc/\$svc in \$ns"
          \$KUBECTL_BIN delete svc "\$svc" -n "\$ns" --timeout=30s 2>/dev/null || true
        done
      done

      # ── Delete ArgoCD apps ──────────────────────────────
      \$KUBECTL_BIN delete application --all -n argocd --timeout=60s 2>/dev/null || true

      # ── Delete Classic ELBs via AWS CLI ─────────────────
      echo "[CLEANUP] Checking for Classic ELBs..."
      CLASSIC_LBS=\$(aws elb describe-load-balancers \
        --region \${AWS_DEFAULT_REGION} \
        --query 'LoadBalancerDescriptions[*].LoadBalancerName' \
        --output text 2>/dev/null || echo "")
      for lb in \$CLASSIC_LBS; do
        echo "[CLEANUP] Deleting Classic ELB: \$lb"
        aws elb delete-load-balancer \
          --load-balancer-name "\$lb" \
          --region \${AWS_DEFAULT_REGION} 2>/dev/null || true
      done

      # ── Delete ALB/NLBs via AWS CLI ─────────────────────
      echo "[CLEANUP] Checking for ALB/NLBs..."
      ALB_ARNS=\$(aws elbv2 describe-load-balancers \
        --region \${AWS_DEFAULT_REGION} \
        --query 'LoadBalancers[*].LoadBalancerArn' \
        --output text 2>/dev/null || echo "")
      for arn in \$ALB_ARNS; do
        echo "[CLEANUP] Deleting ALB/NLB: \$arn"
        aws elbv2 delete-load-balancer \
          --load-balancer-arn "\$arn" \
          --region \${AWS_DEFAULT_REGION} 2>/dev/null || true
      done

      # ── Wait for ELBs to fully deregister ──────────────
      echo "[CLEANUP] Waiting for ELBs to deregister..."
      MAX_WAIT=180; ELAPSED=0
      while [ \$ELAPSED -lt \$MAX_WAIT ]; do
        CLASSIC=\$(aws elb describe-load-balancers \
          --region \${AWS_DEFAULT_REGION} \
          --query 'length(LoadBalancerDescriptions)' \
          --output text 2>/dev/null || echo "0")
        ALB=\$(aws elbv2 describe-load-balancers \
          --region \${AWS_DEFAULT_REGION} \
          --query 'length(LoadBalancers)' \
          --output text 2>/dev/null || echo "0")
        TOTAL=\$((CLASSIC + ALB))
        echo "[CLEANUP] ELBs remaining: \$TOTAL | elapsed: \${ELAPSED}s"
        [ "\$TOTAL" -eq 0 ] && echo "[CLEANUP] ✔ All ELBs gone." && break
        sleep 15; ELAPSED=\$((ELAPSED + 15))
      done

      # ── Clean SG rules blocking subnet deletion ─────────
      VPC_ID=\$(aws ec2 describe-vpcs \
        --region \${AWS_DEFAULT_REGION} \
        --filters "Name=tag:Name,Values=wanderlust-\${ENV_NAME}-vpc" \
        --query 'Vpcs[0].VpcId' --output text 2>/dev/null || echo "")

      if [ -n "\$VPC_ID" ] && [ "\$VPC_ID" != "None" ]; then
        echo "[CLEANUP] Cleaning SG rules in VPC: \$VPC_ID"
        SGS=\$(aws ec2 describe-security-groups \
          --region \${AWS_DEFAULT_REGION} \
          --filters "Name=vpc-id,Values=\$VPC_ID" \
          --query 'SecurityGroups[?GroupName!=\`default\`].GroupId' \
          --output text 2>/dev/null || echo "")
        for sg in \$SGS; do
          INGRESS=\$(aws ec2 describe-security-groups \
            --group-ids "\$sg" --region \${AWS_DEFAULT_REGION} \
            --query 'SecurityGroups[0].IpPermissions' \
            --output json 2>/dev/null || echo "[]")
          [ "\$INGRESS" != "[]" ] && \
            aws ec2 revoke-security-group-ingress \
              --group-id "\$sg" --region \${AWS_DEFAULT_REGION} \
              --ip-permissions "\$INGRESS" 2>/dev/null || true
        done
        echo "[CLEANUP] ✔ SG rules cleaned."
      fi

      echo "[CLEANUP] ✔ Pre-destroy cleanup complete."
    """
  }
}
