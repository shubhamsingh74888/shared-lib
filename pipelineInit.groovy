// ============================================================
//  vars/pipelineInit.groovy
//  Stage 01 · Pipeline Initialisation
//  - Validates tooling and credentials
//  - Sets build display name and description
//  - Creates report output directory
// ============================================================
def call(def cfg, def utils) {

  utils.sectionHeader('Stage 01 · Pipeline Initialisation')

  // ── Set build display name ──────────────────────────────
  currentBuild.displayName = "#${cfg.buildNumber} · ${cfg.deployEnvironment.toUpperCase()} · ${env.BRANCH_NAME ?: 'main'}"
  currentBuild.description = "Frontend: frontend-b${cfg.buildNumber} | Backend: backend-b${cfg.buildNumber}"

  // ── Validate required tools ─────────────────────────────
  echo "[INIT] Validating required CLI tools..."
  utils.validateTools(['docker', 'trivy', 'java', 'aws', 'git'])

  // ── Create reports directory ────────────────────────────
  sh "mkdir -p ${cfg.reportsDir}"
  echo "[INIT] Reports directory: ${cfg.reportsDir}"

  echo "[INIT] ✔ Pipeline initialisation complete."
  echo "[INIT] Build tag: b${cfg.buildNumber} | Environment: ${cfg.deployEnvironment}"
}
