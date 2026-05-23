// ============================================================
//  vars/pipelineTest.groovy
//  Stage 04 · Unit Tests — runs inside Docker
// ============================================================
def call(def script, def cfg, def utils, String service) {

  utils.sectionHeader("Stage 04 · ${service.capitalize()} Unit Tests")

  def serviceDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir

  script.dir(serviceDir) {
    script.docker.image('node:21-alpine').inside('--user root') {
      try {
        if (service == 'frontend') {
          script.sh """
            echo "[TEST] Running frontend tests..."
            npm run test -- --coverage --watchAll=false --ci 2>&1 | tee ../${cfg.reportsDir}/frontend-test.log
          """
        } else {
          script.sh """
            echo "[TEST] Running backend tests..."
            npm run test 2>&1 | tee ../${cfg.reportsDir}/backend-test.log
          """
        }
        script.echo "[TEST] ✔ ${service.capitalize()} tests passed."
      } catch (e) {
        script.currentBuild.result = 'UNSTABLE'
        script.echo "[TEST] ⚠ ${service.capitalize()} tests failed. Marking build UNSTABLE."
      }
    }
  }
}
