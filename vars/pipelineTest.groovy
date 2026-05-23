def call(def script, def cfg, def utils, String service) {
  utils.sectionHeader("Stage 04 · ${service.capitalize()} Unit Tests")
  def serviceDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir
  script.dir(serviceDir) {
    try {
      if (service == 'frontend') {
        script.sh """
          docker run --rm -u \$(id -u):\$(id -g) -v "\${PWD}:/app" -w /app node:21-alpine \
            sh -c "npm ci --prefer-offline --no-audit --loglevel=error && npm run test -- --coverage --watchAll=false --ci" \
            2>&1 | tee "../${cfg.reportsDir}/frontend-test.log"
        """
      } else {
        script.sh """
          docker run --rm -u \$(id -u):\$(id -g) -v "\${PWD}:/app" -w /app node:21-alpine \
            sh -c "npm ci --prefer-offline --no-audit --loglevel=error && npm run test" \
            2>&1 | tee "../${cfg.reportsDir}/backend-test.log"
        """
      }
      script.echo "[TEST] ✔ ${service.capitalize()} tests passed."
    } catch (e) {
      script.currentBuild.result = 'UNSTABLE'
      script.echo "[TEST] ⚠ ${service.capitalize()} tests failed."
    }
  }
}
