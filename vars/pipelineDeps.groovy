// ============================================================
//  vars/pipelineDeps.groovy
//  Stage 03 · Install Dependencies
// ============================================================
def call(def script, def cfg, def utils, String service) {
  utils.sectionHeader("Stage 03 · ${service.capitalize()} Dependencies")

  def workDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir

  script.dir(workDir) {
    script.sh """
      echo "[DEPS] Installing ${service} dependencies safely..."
      mkdir -p .npm-cache

      docker run --rm \
        -u \$(id -u):\$(id -g) \
        -v "\${PWD}:/app" \
        -w /app \
        -e npm_config_cache=/app/.npm-cache \
        node:21-alpine \
        sh -c "${service == 'frontend' ?
          'npm install --prefer-offline --no-audit --loglevel=error --legacy-peer-deps' :
          'npm install --prefer-offline --no-audit --loglevel=error'
        }"

      echo "[DEPS] ✔ ${service.capitalize()} dependencies installed."
    """
  }
}
