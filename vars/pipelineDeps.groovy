// ============================================================
//  vars/pipelineDeps.groovy
//  Stage 03 · Dependency Installation (Fixed Permissions)
// ============================================================
def call(def script, def cfg, def utils, String service) {

    utils.sectionHeader("Stage 03 · ${service.capitalize()} Dependencies")

    def workDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir

    script.dir(workDir) {
        script.sh """
            echo "[DEPS] Installing ${service} dependencies safely..."
            docker run --rm \
                -u \$(id -u):\$(id -g) \
                -v "\${PWD}:/app" \
                -w /app \
                node:21-alpine \
                sh -c "npm install --prefer-offline --no-audit --loglevel=error"
            echo "[DEPS] ✔ ${service.capitalize()} dependencies installed."
        """
    }
}
