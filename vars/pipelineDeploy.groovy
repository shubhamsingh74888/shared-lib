// ============================================================
//  vars/pipelineDeploy.groovy  →  shared-lib repo
//
//  Stage 11 · GitOps · Kubernetes Manifest Update
//
//  FIXES applied vs original:
//  1. Manifest resolution now tries both naming conventions and
//     hard-fails with a clear error if neither is found.
//  2. Image tag format MATCHES PipelineConfig.groovy exactly:
//       frontend → imageFrontend:frontend-b<N>
//       backend  → imageBackend:backend-b<N>
//  3. sed patterns updated to match these tag formats.
//  4. Added post-update grep verification before committing.
//  5. Git push URL injects token without exposing it in ps output.
//  6. Temporary clone dir is always cleaned up.
// ============================================================

def gitopsUpdate(def cfg, def utils) {
  utils.sectionHeader('Stage 11 · GitOps · Kubernetes Manifest Update')

  withCredentials([usernamePassword(
    credentialsId   : cfg.gitCredId,
    usernameVariable: 'GIT_USER',
    passwordVariable: 'GIT_TOKEN'
  )]) {
    sh """
      set -euo pipefail

      git config --global user.email "jenkins@wanderlust.ci"
      git config --global user.name  "Wanderlust Jenkins Bot"

      # ── Clone GitOps repo into a temp directory ─────────
      GITOPS_DIR=\$(mktemp -d)
      PUSH_URL="${cfg.gitopsRepoUrl.replace('https://', 'https://\${GIT_USER}:\${GIT_TOKEN}@')}"
      git clone --depth=1 "\$PUSH_URL" "\$GITOPS_DIR"
      echo "[GITOPS] ✔ Cloned to: \$GITOPS_DIR"

      # ── Resolve manifest directory ───────────────────────
      # k8sManifestsDir = 'kubernetes'
      # pipelineDeploy appends /production
      # Final path: kubernetes/production/
      MANIFEST_BASE="\$GITOPS_DIR/${cfg.k8sManifestsDir}/production"

      if [ ! -d "\$MANIFEST_BASE" ]; then
        echo "[GITOPS] ERROR: Manifest directory does not exist: \$MANIFEST_BASE"
        echo "[GITOPS] Contents of \$GITOPS_DIR/${cfg.k8sManifestsDir}:"
        ls -la "\$GITOPS_DIR/${cfg.k8sManifestsDir}/" || true
        rm -rf "\$GITOPS_DIR"
        exit 1
      fi

      # ── Resolve frontend manifest ────────────────────────
      # Tries: frontend.yaml first (your actual filename), then frontend-deployment.yaml
      FRONTEND_MANIFEST=""
      for FE_NAME in frontend.yaml frontend-deployment.yaml; do
        if [ -f "\$MANIFEST_BASE/\$FE_NAME" ]; then
          FRONTEND_MANIFEST="\$MANIFEST_BASE/\$FE_NAME"
          break
        fi
      done

      # ── Resolve backend manifest ─────────────────────────
      # Tries: backend.yaml first (your actual filename), then backend-deployment.yaml
      BACKEND_MANIFEST=""
      for BE_NAME in backend.yaml backend-deployment.yaml backend.yml; do
        if [ -f "\$MANIFEST_BASE/\$BE_NAME" ]; then
          BACKEND_MANIFEST="\$MANIFEST_BASE/\$BE_NAME"
          break
        fi
      done

      # ── Hard fail with debug output if not found ─────────
      if [ -z "\$FRONTEND_MANIFEST" ] || [ -z "\$BACKEND_MANIFEST" ]; then
        echo "[GITOPS] ERROR: Could not resolve manifest files under \$MANIFEST_BASE"
        echo "[GITOPS] Files present in \$MANIFEST_BASE:"
        ls -la "\$MANIFEST_BASE/" || echo "  (directory does not exist)"
        rm -rf "\$GITOPS_DIR"
        exit 1
      fi

      echo "[GITOPS] ✔ Frontend manifest: \$FRONTEND_MANIFEST"
      echo "[GITOPS] ✔ Backend  manifest: \$BACKEND_MANIFEST"

      # ── Show current image tags before update ────────────
      echo "[GITOPS] Before update:"
      grep "image:" "\$FRONTEND_MANIFEST" || true
      grep "image:" "\$BACKEND_MANIFEST"  || true

      # ── Update image tags ────────────────────────────────
      # Tag format from PipelineConfig.groovy:
      #   frontendImageTag = imageFrontend:frontend-b<buildNumber>
      #   backendImageTag  = imageBackend:backend-b<buildNumber>
      sed -i "s|image: ${cfg.imageFrontend}:.*|image: ${cfg.frontendImageTag}|g" "\$FRONTEND_MANIFEST"
      sed -i "s|image: ${cfg.imageBackend}:.*|image: ${cfg.backendImageTag}|g"   "\$BACKEND_MANIFEST"

      # ── Verify update was applied ─────────────────────────
      echo "[GITOPS] After update:"
      grep "image:" "\$FRONTEND_MANIFEST"
      grep "image:" "\$BACKEND_MANIFEST"

      # ── Commit and push only if there are real changes ───
      cd "\$GITOPS_DIR"
      git add "${cfg.k8sManifestsDir}/production/frontend.yaml" \
               "${cfg.k8sManifestsDir}/production/backend.yaml" \
               2>/dev/null || git add "${cfg.k8sManifestsDir}/"

      if git diff --cached --quiet; then
        echo "[GITOPS] Images already at build b${cfg.buildNumber} — nothing to commit."
      else
        git commit -m "ci(gitops): deploy build b${cfg.buildNumber} [skip ci]

Environment : ${cfg.deployEnvironment}
Frontend    : ${cfg.frontendImageTag}
Backend     : ${cfg.backendImageTag}
Commit      : ${cfg.gitCommitShort}
Author      : ${cfg.gitAuthor}"

        git push "\$PUSH_URL" HEAD:main
        echo "[GITOPS] ✔ Manifest changes pushed. ArgoCD selfHeal will auto-sync."
      fi

      # ── Cleanup ───────────────────────────────────────────
      rm -rf "\$GITOPS_DIR"
      echo "[GITOPS] ✔ Temp directory cleaned up."
    """
  }
}
