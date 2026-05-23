// ============================================================
//  vars/pipelineDeploy.groovy
//  Deploy Stage Module
//  Exposes:
//    pipelineDeploy.gitopsUpdate(cfg, utils)
//
//  Updates Kubernetes manifests in the GitOps repo.
//  ArgoCD detects the change and auto-syncs to the cluster.
// ============================================================

def gitopsUpdate(def cfg, def utils) {
  utils.sectionHeader('Stage 11 · GitOps · Kubernetes Manifest Update')

  withCredentials([usernamePassword(
    credentialsId : cfg.gitCredId,
    usernameVariable: 'GIT_USER',
    passwordVariable: 'GIT_TOKEN'
  )]) {
    sh """
      echo "[GITOPS] Configuring git identity..."
      git config --global user.email "jenkins@wanderlust.ci"
      git config --global user.name  "Wanderlust Jenkins Bot"

      # ── Update frontend deployment manifest ──────────────
      FRONTEND_MANIFEST="${cfg.k8sManifestsDir}/${cfg.deployEnvironment}/frontend-deployment.yaml"
      if [ -f "\$FRONTEND_MANIFEST" ]; then
        echo "[GITOPS] Updating frontend image tag in: \$FRONTEND_MANIFEST"
        sed -i 's|${cfg.imageFrontend}:frontend-b[0-9]*|${cfg.frontendImageTag}|g' "\$FRONTEND_MANIFEST"
      else
        echo "[GITOPS] ⚠ Frontend manifest not found: \$FRONTEND_MANIFEST"
      fi

      # ── Update backend deployment manifest ───────────────
      BACKEND_MANIFEST="${cfg.k8sManifestsDir}/${cfg.deployEnvironment}/backend-deployment.yaml"
      if [ -f "\$BACKEND_MANIFEST" ]; then
        echo "[GITOPS] Updating backend image tag in: \$BACKEND_MANIFEST"
        sed -i 's|${cfg.imageBackend}:backend-b[0-9]*|${cfg.backendImageTag}|g' "\$BACKEND_MANIFEST"
      else
        echo "[GITOPS] ⚠ Backend manifest not found: \$BACKEND_MANIFEST"
      fi

      # ── Stage and commit changes ──────────────────────────
      git add ${cfg.k8sManifestsDir}/${cfg.deployEnvironment}/ || true

      # Only commit if there are actual changes
      if git diff --cached --quiet; then
        echo "[GITOPS] No manifest changes detected. Skipping commit."
      else
        git commit -m "ci(gitops): update image tags → b${cfg.buildNumber} [skip ci]

  Environment : ${cfg.deployEnvironment}
  Frontend    : ${cfg.frontendImageTag}
  Backend     : ${cfg.backendImageTag}
  Commit      : ${cfg.gitCommitShort}
  Author      : ${cfg.gitAuthor}
  Build URL   : \${BUILD_URL}"

        REPO_URL_WITH_CREDS="${cfg.gitopsRepoUrl.replace('https://', 'https://\${GIT_USER}:\${GIT_TOKEN}@')}"
        git push "\$REPO_URL_WITH_CREDS" HEAD:main

        echo "[GITOPS] ✔ Manifests pushed. ArgoCD will auto-sync."
      fi
    """
  }
}
