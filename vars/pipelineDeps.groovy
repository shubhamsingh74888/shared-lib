// ============================================================
//  vars/pipelineDeps.groovy
//  Stage 03 · Dependency Installation — runs inside Docker
// ============================================================
def call(def script, def cfg, def utils, String service) {

  utils.sectionHeader("Stage 03 · ${service.capitalize()} Dependencies")

  def workDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir

  script.dir(workDir) {
    script.docker.image('node:21-alpine').inside('--user root') {
      script.sh """
        echo "[DEPS] Installing ${service} dependencies..."
        npm ci --prefer-offline --no-audit --loglevel=error
        echo "[DEPS] ✔ ${service.capitalize()} dependencies installed."
      """
    }
  }
}
