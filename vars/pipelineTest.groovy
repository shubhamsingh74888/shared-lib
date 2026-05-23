// ============================================================
//  vars/pipelineTest.groovy
//  Stage 04 · Unit Tests (Fixed Arguments and Permissions)
// ============================================================
def call(def script, def cfg, def utils, String service) {

    utils.sectionHeader("Stage 04 · ${service.capitalize()} Unit Tests")

    def serviceDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir

    script.dir(serviceDir) {
        // Create cache dir in the workspace so it is writable
        script.sh "mkdir -p .npm-cache" 
        
        try {
            if (service == 'frontend') {
                script.sh """
                    echo "[TEST] Running frontend tests..."
                    docker run --rm \
                        -u \$(id -u):\$(id -g) \
                        -v "\${PWD}:/app" \
                        -w /app \
                        -e npm_config_cache=/app/.npm-cache \
                        node:21-alpine \
                        sh -c "npm install --prefer-offline --no-audit --loglevel=error && npm run test -- --coverage --watchAll=false --ci" \
                        2>&1 | tee "../${cfg.reportsDir}/frontend-test.log"
                """
            } else {
                script.sh """
                    echo "[TEST] Running backend tests..."
                    docker run --rm \
                        -u \$(id -u):\$(id -g) \
                        -v "\${PWD}:/app" \
                        -w /app \
                        -e npm_config_cache=/app/.npm-cache \
                        node:21-alpine \
                        sh -c "npm install --prefer-offline --no-audit --loglevel=error && npm run test" \
                        2>&1 | tee "../${cfg.reportsDir}/backend-test.log"
                """
            }
            script.echo "[TEST] ✔ ${service.capitalize()} tests passed."
        } catch (e) {
            script.currentBuild.result = 'UNSTABLE'
            script.echo "[TEST] ⚠ ${service.capitalize()} tests failed. Marking build UNSTABLE."
        }
    }
}
