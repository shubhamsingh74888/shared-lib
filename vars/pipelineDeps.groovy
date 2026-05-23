def call(def script, def cfg, def utils, String service) {
  utils.sectionHeader("Stage 03 · ${service.capitalize()} Dependencies")
  def workDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir

  script.dir(workDir) {
    // We use the 'docker' variable directly. 
    // Jenkins injects this globally if the plugin is installed.
    docker.image('node:21-alpine').inside('--user root') {
      script.sh """
        echo "[DEPS] Installing ${service} dependencies..."
        npm ci --prefer-offline --no-audit --loglevel=error
        echo "[DEPS] ✔ ${service.capitalize()} dependencies installed."
      """
    }
  }
}
