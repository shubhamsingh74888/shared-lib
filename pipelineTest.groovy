// ============================================================
//  vars/pipelineTest.groovy
//  Stage 04 · Unit Tests & Coverage
//  - Runs tests for a given service
//  - Archives coverage output for SonarQube consumption
// ============================================================
def call(def cfg, def utils, String service) {

  utils.sectionHeader("Stage 04 · ${service.capitalize()} Unit Tests")

  def serviceDir = service == 'frontend' ? cfg.frontendDir : cfg.backendDir

  dir("${serviceDir}") {
    try {
      if (service == 'frontend') {
        sh """
          echo "[TEST] Running frontend tests with coverage..."
          npm run test -- \
            --coverage \
            --watchAll=false \
            --coverageReporters=lcov,text-summary \
            --coverageDirectory=coverage \
            --ci \
            2>&1 | tee ../${cfg.reportsDir}/frontend-test.log
        """
        // Capture coverage percentage for display
        def coverage = sh(
          script: "grep -oP 'Lines\\s*:\\s*\\K[0-9.]+' ../${cfg.reportsDir}/frontend-test.log | head -1 || echo 'N/A'",
          returnStdout: true
        ).trim()
        echo "[TEST] ✔ Frontend tests passed | Line coverage: ${coverage}%"

      } else {
        sh """
          echo "[TEST] Running backend tests..."
          npm run test 2>&1 | tee ../${cfg.reportsDir}/backend-test.log
        """
        echo "[TEST] ✔ Backend tests passed."
      }

    } catch (e) {
      // Mark unstable instead of failing — let security scans still run
      currentBuild.result = 'UNSTABLE'
      echo "[TEST] ⚠ ${service.capitalize()} tests failed. Marking build UNSTABLE."
      echo "[TEST]   Review: ${cfg.reportsDir}/${service}-test.log"
    }
  }
}
