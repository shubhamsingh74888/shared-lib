// ============================================================
//  vars/pipelinePost.groovy
//  Post-Pipeline Actions Module
//  Exposes:
//    pipelinePost.always(cfg, utils, currentBuild)
//    pipelinePost.onSuccess(cfg, utils)
//    pipelinePost.onFailure(cfg, utils)
//    pipelinePost.onUnstable(cfg, utils)
// ============================================================

// ── Always runs — cleanup, archive, summary ────────────────
def always(def cfg, def utils, def currentBuild) {

  // ── Print final build summary banner ───────────────────
  if (cfg != null) {
    echo cfg.getSummaryBanner(currentBuild.currentResult ?: 'UNKNOWN')
  }

  // ── Archive all security reports ────────────────────────
  if (cfg != null) {
    archiveArtifacts(
      artifacts        : "${cfg.reportsDir}/**/*",
      allowEmptyArchive: true,
      fingerprint      : true
    )
  }

  // ── Docker image cleanup ─────────────────────────────────
  if (cfg != null) {
    sh """
      echo "[CLEANUP] Removing local Docker images to reclaim disk..."
      docker rmi ${cfg.frontendImageTag}                        || true
      docker rmi ${cfg.imageBackend}:frontend-latest            || true
      docker rmi ${cfg.backendImageTag}                         || true
      docker rmi ${cfg.imageBackend}:backend-latest             || true
      docker image prune -f                                     || true
      docker logout                                             || true
      echo "[CLEANUP] ✔ Docker cleanup complete."
    """
  }
}

// ── Success notification ────────────────────────────────────

def onSuccess(def cfg, def utils) {
  if (cfg == null) { echo "[NOTIFY] Build SUCCEEDED (cfg unavailable)"; return }
  echo "[NOTIFY] ✅ Build #${cfg.buildNumber} SUCCEEDED"
}

// ── Failure notification ────────────────────────────────────

def onFailure(def cfg, def utils) {
  if (cfg == null) { echo "[NOTIFY] Build FAILED (cfg unavailable)"; return }
  echo "[NOTIFY] ❌ Build #${cfg.buildNumber} FAILED"
}

// ── Unstable notification ───────────────────────────────────

def onUnstable(def cfg, def utils) {
  if (cfg == null) { echo "[NOTIFY] Build UNSTABLE (cfg unavailable)"; return }
  echo "[NOTIFY] ⚠ Build #${cfg.buildNumber} UNSTABLE"
}

// ── Private: build Slack message payload ───────────────────
private String _buildSlackMessage(def cfg, String status) {
  return """
*Wanderlust · Build #${cfg.buildNumber} · ${status}*
> Branch      : '${cfg.gitBranch}'
> Environment : '${cfg.deployEnvironment}'
> Commit      : '${cfg.gitCommitShort}' by ${cfg.gitAuthor}
> Message     : ${cfg.gitCommitMsg}
> Frontend    : '${cfg.frontendImageTag}'
> Backend     : '${cfg.backendImageTag}'
> Duration    : ${cfg.getBuildDuration()}
> Build URL   : ${env.BUILD_URL}
  """.stripIndent()

}
