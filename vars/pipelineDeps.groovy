// ============================================================
//  vars/pipelineDeps.groovy
//  Stage 03 · Dependency Installation
// ============================================================
def call(def cfg, def utils, String service) {

  utils.sectionHeader("Stage 03 · ${service.capitalize()} Dependencies")

  def workDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir

  dir(workDir) {
    sh """
      echo "[DEPS] Installing ${service} dependencies..."
      npm ci --prefer-offline --no-audit --loglevel=error
      echo "[DEPS] ✔ ${service.capitalize()} dependencies installed."
    """
  }
}
