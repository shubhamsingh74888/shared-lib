// ============================================================
//  vars/pipelineBuild.groovy
//  Build Stage Module
//  Exposes:
//    pipelineBuild.buildImage(cfg, utils, service)
//    pipelineBuild.pushImages(cfg, utils)
// ============================================================

// ── Build a Docker image for a given service ───────────────
def buildImage(def cfg, def utils, String service) {
  utils.sectionHeader("Stage 08 · Build · ${service.capitalize()} Docker Image")

  def serviceDir = service == 'frontend' ? cfg.frontendDir : cfg.backendDir
  def imageTag   = service == 'frontend' ? cfg.frontendImageTag : cfg.backendImageTag
  def latestTag  = service == 'frontend'
    ? "${cfg.imageFrontend}:frontend-latest"
    : "${cfg.imageBackend}:backend-latest"

  dir("${serviceDir}") {
    sh """
      echo "[BUILD] Building ${service} image: ${imageTag}"

      docker build \
        --label "org.opencontainers.image.title=wanderlust-${service}" \
        --label "org.opencontainers.image.version=b${cfg.buildNumber}" \
        --label "org.opencontainers.image.revision=${cfg.gitCommitShort}" \
        --label "org.opencontainers.image.created=\$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        --label "org.opencontainers.image.source=${cfg.gitRepoUrl}" \
        --label "build.branch=${cfg.gitBranch}" \
        --label "build.author=${cfg.gitAuthor}" \
        -t ${imageTag} \
        -t ${latestTag} \
        .

      echo "[BUILD] ✔ Image built: ${imageTag}"
      echo "[BUILD] ✔ Image size : \$(docker image inspect ${imageTag} --format='{{.Size}}' | awk '{printf \"%.1f MB\", \$1/1048576}')"
    """
  }
}

// ── Push both images to registry ──────────────────────────
def pushImages(def cfg, def utils) {
  utils.sectionHeader('Stage 10 · Artefact Registry Push')

  // Secure login — never expose password in process list
  sh 'echo $DOCKERHUB_CREDS_PSW | docker login -u $DOCKERHUB_CREDS_USR --password-stdin'
  echo "[PUSH] ✔ Authenticated with Docker Hub as: ${env.DOCKERHUB_CREDS_USR}"

  parallel(
    'Push Frontend': {
      _pushImage(cfg.frontendImageTag, "${cfg.imageFrontend}:frontend-latest", 'frontend')
    },
    'Push Backend': {
      _pushImage(cfg.backendImageTag, "${cfg.imageBackend}:backend-latest", 'backend')
    }
  )
}

// ── Private helper: push a single image + its latest tag ──
private def _pushImage(String versionTag, String latestTag, String service) {
  sh """
    echo "[PUSH] Pushing ${service} version tag: ${versionTag}"
    docker push ${versionTag}

    echo "[PUSH] Pushing ${service} latest  tag: ${latestTag}"
    docker push ${latestTag}

    echo "[PUSH] ✔ ${service.capitalize()} images pushed successfully."
  """
}
