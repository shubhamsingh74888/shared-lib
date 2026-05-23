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
  echo "[NOTIFY] ✅ Build #${cfg.buildNumber} SUCCEEDED"

  // ── Slack notification (uncomment to enable) ──────────
  // withCredentials([string(credentialsId: cfg.slackCredId, variable: 'SLACK_TOKEN')]) {
  //   slackSend(
  //     channel: cfg.slackChannel,
  //     color  : 'good',
  //     message: _buildSlackMessage(cfg, '✅ PASSED')
  //   )
  // }
}

// ── Failure notification ────────────────────────────────────
def onFailure(def cfg, def utils) {
  echo "[NOTIFY] ❌ Build #${cfg.buildNumber} FAILED"

  // ── Slack notification (uncomment to enable) ──────────
  // withCredentials([string(credentialsId: cfg.slackCredId, variable: 'SLACK_TOKEN')]) {
  //   slackSend(
  //     channel: cfg.slackChannel,
  //     color  : 'danger',
  //     message: _buildSlackMessage(cfg, '❌ FAILED')
  //   )
  // }
}

// ── Unstable notification ───────────────────────────────────
def onUnstable(def cfg, def utils) {
  echo "[NOTIFY] ⚠ Build #${cfg.buildNumber} UNSTABLE — review security reports"

  // ── Slack notification (uncomment to enable) ──────────
  // withCredentials([string(credentialsId: cfg.slackCredId, variable: 'SLACK_TOKEN')]) {
  //   slackSend(
  //     channel: cfg.slackChannel,
  //     color  : 'warning',
  //     message: _buildSlackMessage(cfg, '⚠ UNSTABLE')
  //   )
  // }
}

// ── Private: build Slack message payload ───────────────────
private String _buildSlackMessage(def cfg, String status) {
  return """
*Wanderlust · Build #${cfg.buildNumber} · ${status}*
> Branch      : \`${cfg.gitBranch}\`
> Environment : \`${cfg.deployEnvironment}\`
> Commit      : \`${cfg.gitCommitShort}\` by ${cfg.gitAuthor}
> Message     : ${cfg.gitCommitMsg}
> Frontend    : \`${cfg.frontendImageTag}\`
> Backend     : \`${cfg.backendImageTag}\`
> Duration    : ${cfg.getBuildDuration()}
> Build URL   : ${env.BUILD_URL}
  """.stripIndent()
}
