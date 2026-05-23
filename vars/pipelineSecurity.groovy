// ============================================================
//  vars/pipelineSecurity.groovy
//  Security Stage Module
// ============================================================

// ── SonarQube SAST ─────────────────────────────────────────
def sonarScan(def cfg, def utils) {
  utils.sectionHeader('Stage 05 · SAST · SonarQube Code Scan')

  sh "mkdir -p ${cfg.reportsDir}"

  def scannerHome = tool 'sonar-scanner'
  def lcovPath    = "${cfg.frontendDir}/coverage/lcov.info"
  def lcovArg     = fileExists(lcovPath) ? "-Dsonar.javascript.lcov.reportPaths=${lcovPath}" : ''

  withSonarQubeEnv("${cfg.sonarServerName}") {
    sh """
      echo "[SAST] Submitting source to SonarQube..."
      ${scannerHome}/bin/sonar-scanner \
        -Dsonar.projectKey=${cfg.sonarProjectKey} \
        -Dsonar.projectName=${cfg.projectName} \
        -Dsonar.sources=. \
        -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/build/**,**/*.test.*,**/coverage/**,${cfg.reportsDir}/** \
        ${lcovArg} \
        -Dsonar.qualitygate.wait=false
      echo "[SAST] ✔ SonarQube analysis submitted."
    """
  }
}

// ── Quality Gate ───────────────────────────────────────────
def qualityGate(def cfg, def utils) {
  utils.sectionHeader('Stage 06 · Quality Gate Enforcement')

  timeout(time: 5, unit: 'MINUTES') {
    def qg = waitForQualityGate()
    if (qg.status != 'OK') {
      error "[QG] ✘ Quality Gate FAILED — Status: ${qg.status}. Aborting pipeline."
    }
    echo "[QG] ✔ Quality Gate PASSED — Status: ${qg.status}"
  }
}

// ── NPM Audit Fix ──────────────────────────────────────────
def npmAuditFix(def cfg, def utils, String service) {
  utils.sectionHeader("Stage 03.5 · NPM Audit Fix · ${service.capitalize()}")

  def serviceDir = (service == 'frontend') ? cfg.frontendDir : cfg.backendDir

  // Create reports dir at workspace root BEFORE entering the subdir
  sh "mkdir -p ${cfg.reportsDir}"

  dir(serviceDir) {
    // Also ensure the log target path exists from this relative position
    sh "mkdir -p ../${cfg.reportsDir}"

    sh """
      echo "[AUDIT] Running npm audit fix for ${service}..."

      docker run --rm \
        -u \$(id -u):\$(id -g) \
        -v "\${PWD}:/app" \
        -w /app \
        -e npm_config_cache=/app/.npm-cache \
        node:21-alpine \
        sh -c "
          mkdir -p .npm-cache && \
          echo '[AUDIT] === Vulnerabilities BEFORE fix ===' && \
          npm audit --audit-level=none 2>&1 | tail -10 && \
          echo '[AUDIT] Attempting npm audit fix...' && \
          npm audit fix 2>&1 && \
          echo '[AUDIT] Attempting npm audit fix --force for remaining issues...' && \
          npm audit fix --force 2>&1 && \
          echo '[AUDIT] === Vulnerabilities AFTER fix ===' && \
          npm audit --audit-level=none 2>&1 | tail -10
        " 2>&1 | tee "../${cfg.reportsDir}/${service}-audit-fix.log"

      echo "[AUDIT] ✔ ${service.capitalize()} audit fix complete. Log: ${cfg.reportsDir}/${service}-audit-fix.log"
    """
  }
}

// ── OWASP Dependency Check ─────────────────────────────────
def owaspScan(def cfg, def utils) {
  utils.sectionHeader('Stage 07 · SCA · OWASP Dependency-Check')

  sh "mkdir -p ${cfg.reportsDir}"

  dependencyCheck(
    additionalArguments: [
      "--scan ./${cfg.backendDir}/package.json",
      "--scan ./${cfg.frontendDir}/package.json",
      "--format HTML --format XML --format JSON",
      "--out ${cfg.reportsDir}",
      "--nvdApiKey ${env.NVD_API_KEY}",
      "--failOnCVSS 7",
      "--enableRetired",
    ].join(' '),
    odcInstallation: 'OWASP'
  )

  dependencyCheckPublisher(
    pattern            : "${cfg.reportsDir}/dependency-check-report.xml",
    failedTotalCritical: 1,
    unstableTotalHigh  : 5
  )

  echo "[SCA] ✔ OWASP Dependency-Check complete. Report: ${cfg.reportsDir}/dependency-check-report.html"
}

// ── Trivy Filesystem Scan ──────────────────────────────────
def trivyFsScan(def cfg, def utils) {
  utils.sectionHeader('Stage 07 · SCA · Trivy Filesystem Scan')

  sh """
    mkdir -p ${cfg.reportsDir}
    echo "[TRIVY-FS] Scanning project filesystem..."
    trivy fs . \
      --format table \
      --exit-code 0 \
      --severity HIGH,CRITICAL \
      --ignore-unfixed \
      --skip-dirs node_modules,dist,build,.git,${cfg.reportsDir} \
      -o ${cfg.reportsDir}/trivy-fs-table.txt

    trivy fs . \
      --format json \
      --exit-code 0 \
      --severity HIGH,CRITICAL \
      --ignore-unfixed \
      --skip-dirs node_modules,dist,build,.git,${cfg.reportsDir} \
      -o ${cfg.reportsDir}/trivy-fs-report.json

    echo "[TRIVY-FS] ✔ Filesystem scan complete."
  """
}

// ── Trivy Image Scan ───────────────────────────────────────
def trivyImageScan(def cfg, def utils, String service) {
  utils.sectionHeader("Stage 09 · Trivy · ${service.capitalize()} Image Scan")
  sh "mkdir -p ${cfg.reportsDir}"

  def imageTag = service == 'frontend' ? cfg.frontendImageTag : cfg.backendImageTag

  sh """
    echo "[TRIVY-IMG] Scanning ${service} image: ${imageTag}"
    trivy image \
      --format table \
      --exit-code ${cfg.trivyExitCode} \
      --severity HIGH,CRITICAL \
      --ignore-unfixed \
      -o ${cfg.reportsDir}/trivy-${service}-image-table.txt \
      ${imageTag}
  """
}
