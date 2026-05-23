// ============================================================
//  vars/pipelineTest.groovy
//  Stage 04 · Unit Tests — runs inside Docker
// ============================================================
def call(def cfg, def utils, String service) {

  utils.sectionHeader("Stage 04 · ${service.capitalize()} Unit Tests")

  def serviceDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir

  dir(serviceDir) {
    docker.image('node:21-alpine').inside('--user root') {
      try {
        if (service == 'frontend') {
          sh """
            echo "[TEST] Running frontend tests..."
            npm run test -- --coverage --watchAll=false --ci 2>&1 | tee ../${cfg.reportsDir}/frontend-test.log
          """
        } else {
          sh """
            echo "[TEST] Running backend tests..."
            npm run test 2>&1 | tee ../${cfg.reportsDir}/backend-test.log
          """
        }
        echo "[TEST] ✔ ${service.capitalize()} tests passed."
      } catch (e) {
        currentBuild.result = 'UNSTABLE'
        echo "[TEST] ⚠ ${service.capitalize()} tests failed. Marking build UNSTABLE."
      }
    }
  }
}
