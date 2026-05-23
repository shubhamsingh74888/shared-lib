// ============================================================
//  vars/pipelineDeploy.groovy
//  Deploy Stage Module
//  Exposes:
//    pipelineDeploy.gitopsUpdate(cfg, utils)
//
//  Clones the GitOps repo, updates Kubernetes manifests with
//  the new image tags, commits and pushes.
//  ArgoCD detects the change and auto-syncs to the cluster.
// ============================================================

def gitopsUpdate(def cfg, def utils) {
  utils.sectionHeader('Stage 11 · GitOps · Kubernetes Manifest Update')

  withCredentials([usernamePassword(
    credentialsId   : cfg.gitCredId,
    usernameVariable: 'GIT_USER',
    passwordVariable: 'GIT_TOKEN'
  )]) {
    sh """
      echo "[GITOPS] Configuring git identity..."
      git config --global user.email "jenkins@wanderlust.ci"
      git config --global user.name  "Wanderlust Jenkins Bot"

      # ── Clone the GitOps repo into a temp directory ──────
      echo "[GITOPS] Cloning GitOps repo..."
      GITOPS_DIR=\$(mktemp -d)
      REPO_URL_WITH_CREDS="${cfg.gitopsRepoUrl.replace('https://', 'https://\${GIT_USER}:\${GIT_TOKEN}@')}"
      git clone --depth=1 "\$REPO_URL_WITH_CREDS" "\$GITOPS_DIR"
      echo "[GITOPS] ✔ Cloned to: \$GITOPS_DIR"

      # ── Locate manifests ──────────────────────────────────
      # Supports both flat (kubernetes/prod/frontend.yaml)
      # and nested (kubernetes/prod/production/frontend-deployment.yaml)
      FRONTEND_MANIFEST=""
      BACKEND_MANIFEST=""

      # Check nested path first (production/ subfolder)
      if [ -f "\$GITOPS_DIR/${cfg.k8sManifestsDir}/production/frontend-deployment.yaml" ]; then
        FRONTEND_MANIFEST="\$GITOPS_DIR/${cfg.k8sManifestsDir}/production/frontend-deployment.yaml"
        BACKEND_MANIFEST="\$GITOPS_DIR/${cfg.k8sManifestsDir}/production/backend-deployment.yaml"
      # Fall back to flat structure (frontend.yaml directly in prod/)
      elif [ -f "\$GITOPS_DIR/${cfg.k8sManifestsDir}/frontend.yaml" ]; then
        FRONTEND_MANIFEST="\$GITOPS_DIR/${cfg.k8sManifestsDir}/frontend.yaml"
        BACKEND_MANIFEST="\$GITOPS_DIR/${cfg.k8sManifestsDir}/backend.yml"
      fi

      if [ -z "\$FRONTEND_MANIFEST" ]; then
        echo "[GITOPS] ✘ Could not locate frontend manifest. Aborting."
        exit 1
      fi

      echo "[GITOPS] Frontend manifest : \$FRONTEND_MANIFEST"
      echo "[GITOPS] Backend  manifest : \$BACKEND_MANIFEST"

      # ── Update image tags ─────────────────────────────────
      # Replaces any existing tag (latest or versioned) with the new build tag
      echo "[GITOPS] Updating frontend image → ${cfg.frontendImageTag}"
      sed -i "s|image: ${cfg.imageFrontend}:.*|image: ${cfg.frontendImageTag}|g" "\$FRONTEND_MANIFEST"

      echo "[GITOPS] Updating backend  image → ${cfg.backendImageTag}"
      sed -i "s|image: ${cfg.imageBackend}:.*|image: ${cfg.backendImageTag}|g" "\$BACKEND_MANIFEST"

      # ── Verify the sed actually changed something ─────────
      echo "[GITOPS] Verifying frontend manifest:"
      grep "image:" "\$FRONTEND_MANIFEST"
      echo "[GITOPS] Verifying backend manifest:"
      grep "image:" "\$BACKEND_MANIFEST"

      # ── Commit and push ───────────────────────────────────
      cd "\$GITOPS_DIR"
      git add ${cfg.k8sManifestsDir}/

      if git diff --cached --quiet; then
        echo "[GITOPS] No manifest changes detected. Images already up to date."
      else
        git commit -m "ci(gitops): update image tags → b${cfg.buildNumber} [skip ci]

Environment : ${cfg.deployEnvironment}
Frontend    : ${cfg.frontendImageTag}
Backend     : ${cfg.backendImageTag}
Commit      : ${cfg.gitCommitShort}
Author      : ${cfg.gitAuthor}
Build URL   : \${BUILD_URL}"

        git push "\$REPO_URL_WITH_CREDS" HEAD:main
        echo "[GITOPS] ✔ Manifests updated and pushed. ArgoCD will auto-sync."
      fi

      # ── Cleanup temp clone ────────────────────────────────
      rm -rf "\$GITOPS_DIR"
      echo "[GITOPS] ✔ Temp clone cleaned up."
    """
  }
}
