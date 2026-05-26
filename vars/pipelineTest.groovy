// ============================================================
//  vars/pipelineTest.groovy
//  Stage 04 · Testing & Analysis
//  - Ensures report directory exists before writing logs
// ============================================================
def call(def script, def cfg, def utils, String service) {

  utils.sectionHeader("Stage 04 · ${service.capitalize()} Testing")

  def workDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir
  
  script.dir(workDir) {
    script.sh """
      echo "[TEST] Running tests for ${service}..."
      
      # ── Ensure report directory exists ───────────────────
      # The -p flag prevents errors if the directory already exists
      mkdir -p "\${PWD}/../${cfg.reportsDir}"
      
      # ── Execute tests in container ───────────────────────
      docker run --rm \\
        -u \$(id -u):\$(id -g) \\
        -v "\${PWD}:/app" \\
        -w /app \\
        node:21-alpine \\
        npm test 2>&1 | tee "../${cfg.reportsDir}/${service}-test.log"

      echo "[TEST] ✔ ${service.capitalize()} tests complete."
    """
  }
}
