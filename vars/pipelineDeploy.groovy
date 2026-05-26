def gitopsUpdate(def cfg, def utils) {
  utils.sectionHeader('Stage 11 · GitOps · Kubernetes Manifest Update')

  withCredentials([usernamePassword(
    credentialsId   : cfg.gitCredId,
    usernameVariable: 'GIT_USER',
    passwordVariable: 'GIT_TOKEN'
  )]) {
    sh """
      git config --global user.email "jenkins@wanderlust.ci"
      git config --global user.name  "Wanderlust Jenkins Bot"

      GITOPS_DIR=\$(mktemp -d)
      REPO_URL_WITH_CREDS="${cfg.gitopsRepoUrl.replace('https://', 'https://\${GIT_USER}:\${GIT_TOKEN}@')}"
      git clone --depth=1 "\$REPO_URL_WITH_CREDS" "\$GITOPS_DIR"
      echo "[GITOPS] Cloned to: \$GITOPS_DIR"

      MANIFEST_BASE="\$GITOPS_DIR/${cfg.k8sManifestsDir}/production"

      # Resolve frontend manifest (try both naming conventions)
      FRONTEND_MANIFEST=""
      for FE_NAME in frontend-deployment.yaml frontend.yaml; do
        if [ -f "\$MANIFEST_BASE/\$FE_NAME" ]; then
          FRONTEND_MANIFEST="\$MANIFEST_BASE/\$FE_NAME"
          break
        fi
      done

      # Resolve backend manifest
      BACKEND_MANIFEST=""
      for BE_NAME in backend-deployment.yaml backend.yaml backend.yml; do
        if [ -f "\$MANIFEST_BASE/\$BE_NAME" ]; then
          BACKEND_MANIFEST="\$MANIFEST_BASE/\$BE_NAME"
          break
        fi
      done

      # Hard fail with useful debug output if not found
      if [ -z "\$FRONTEND_MANIFEST" ] || [ -z "\$BACKEND_MANIFEST" ]; then
        echo "[GITOPS] ERROR: Could not resolve manifests under \$MANIFEST_BASE"
        echo "[GITOPS] Files present:"
        ls -la "\$MANIFEST_BASE/" || echo "  (directory does not exist)"
        exit 1
      fi

      echo "[GITOPS] Frontend: \$FRONTEND_MANIFEST"
      echo "[GITOPS] Backend : \$BACKEND_MANIFEST"

      # Update image tags (replaces any existing tag with new build tag)
      sed -i "s|image: ${cfg.imageFrontend}:.*|image: ${cfg.frontendImageTag}|g" "\$FRONTEND_MANIFEST"
      sed -i "s|image: ${cfg.imageBackend}:.*|image: ${cfg.backendImageTag}|g"   "\$BACKEND_MANIFEST"

      # Verify
      echo "[GITOPS] Post-update image lines:"
      grep "image:" "\$FRONTEND_MANIFEST"
      grep "image:" "\$BACKEND_MANIFEST"

      # Commit and push only if there are changes
      cd "\$GITOPS_DIR"
      git add ${cfg.k8sManifestsDir}/

      if git diff --cached --quiet; then
        echo "[GITOPS] Images already up to date — nothing to commit."
      else
        git commit -m "ci(gitops): update image tags → b${cfg.buildNumber} [skip ci]

Environment : ${cfg.deployEnvironment}
Frontend    : ${cfg.frontendImageTag}
Backend     : ${cfg.backendImageTag}
Commit      : ${cfg.gitCommitShort}
Author      : ${cfg.gitAuthor}"

        git push "\$REPO_URL_WITH_CREDS" HEAD:main
        echo "[GITOPS] ✔ Manifests pushed. ArgoCD will auto-sync."
      fi

      rm -rf "\$GITOPS_DIR"
      echo "[GITOPS] ✔ Cleanup done."
    """
  }
}
