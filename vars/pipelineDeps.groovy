// ============================================================
//  vars/pipelineDeps.groovy
//  Stage 03 · Dependency Installation
//  - Runs npm ci for a given service (frontend or backend)
//  - Uses npm ci for reproducible, CI-safe installs
// ============================================================
def call(def cfg, def utils, String service) {

  utils.sectionHeader("Stage 03 · ${service.capitalize()} Dependencies")

  def dir = service == 'frontend' ? cfg.frontendDir : cfg.backendDir

  dir("${dir}") {
    sh """
      echo "[DEPS] Installing ${service} dependencies..."
      npm ci --prefer-offline --no-audit --loglevel=error
      echo "[DEPS] ✔ ${service.capitalize()} dependencies installed."
      echo "[DEPS]   Packages: \$(cat node_modules/.package-lock.json 2>/dev/null | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d.get(\"packages\",{})))' 2>/dev/null || echo 'N/A')"
    """
  }
}
