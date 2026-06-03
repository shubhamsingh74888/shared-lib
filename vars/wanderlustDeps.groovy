def call(def script, def cfg, def utils, String service) {

  utils.sectionHeader("Stage 03 · ${service.capitalize()} Dependencies")

  def workDir    = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir
  def extraFlags = (service == 'frontend') ? '--legacy-peer-deps' : ''

  script.dir(workDir) {
    script.sh """
      echo "[DEPS] Installing ${service} dependencies..."
      mkdir -p .npm-cache

      docker run --rm \\
        -u \$(id -u):\$(id -g) \\
        -v "\${PWD}:/app" \\
        -w /app \\
        -e npm_config_cache=/app/.npm-cache \\
        node:21-alpine \\
        sh -c "npm install --prefer-offline --no-audit --loglevel=error ${extraFlags}"

      echo "[DEPS] ✔ ${service.capitalize()} dependencies installed."
    """
  }
}
