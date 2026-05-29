// ============================================================
//  vars/pipelinePost.groovy  →  shared-lib repo
//
//  Post-Pipeline Actions Module
//
//  FIXES applied vs original:
//  1. Slack notifications are now actually sent (they were defined
//     but never called — _buildSlackMessage was private with no caller).
//  2. onSuccess / onFailure / onUnstable now send Slack messages.
//  3. Added null guards so post block never hard-crashes.
//  4. archiveArtifacts error is caught gracefully.
// ============================================================

def always(def cfg, def utils, def currentBuild) {

  // ── Print summary banner ────────────────────────────────
  if (cfg != null) {
    echo cfg.getSummaryBanner(currentBuild.currentResult ?: 'UNKNOWN')
  }

  // ── Archive security reports ─────────────────────────────
  if (cfg != null) {
    try {
      archiveArtifacts(
        artifacts        : "${cfg.reportsDir}/**/*",
        allowEmptyArchive: true,
        fingerprint      : true
      )
    } catch (e) {
      echo "[POST] ⚠ archiveArtifacts failed (non-fatal): ${e.message}"
    }
  }

  // ── Docker cleanup ───────────────────────────────────────
  if (cfg != null) {
    sh """
      echo "[CLEANUP] Removing local Docker images..."
      docker rmi ${cfg.frontendImageTag}              || true
      docker rmi ${cfg.imageFrontend}:frontend-latest || true
      docker rmi ${cfg.backendImageTag}               || true
      docker rmi ${cfg.imageBackend}:backend-latest   || true
      docker image prune -f                           || true
      docker logout                                   || true
      echo "[CLEANUP] ✔ Docker cleanup complete."
    """
  }
}

def onSuccess(def cfg, def utils) {
  if (cfg == null) {
    echo "[NOTIFY] ✅ Build SUCCEEDED (cfg unavailable)"
    return
  }
  echo "[NOTIFY] ✅ Build #${cfg.buildNumber} SUCCEEDED"
  _sendSlack(cfg, '✅ SUCCESS', 'good')
}

def onFailure(def cfg, def utils) {
  if (cfg == null) {
    echo "[NOTIFY] ❌ Build FAILED (cfg unavailable)"
    return
  }
  echo "[NOTIFY] ❌ Build #${cfg.buildNumber} FAILED"
  _sendSlack(cfg, '❌ FAILED', 'danger')
}

def onUnstable(def cfg, def utils) {
  if (cfg == null) {
    echo "[NOTIFY] ⚠ Build UNSTABLE (cfg unavailable)"
    return
  }
  echo "[NOTIFY] ⚠ Build #${cfg.buildNumber} UNSTABLE"
  _sendSlack(cfg, '⚠️ UNSTABLE', 'warning')
}

// ── Private: send Slack message ─────────────────────────────
private void _sendSlack(def cfg, String statusLabel, String color) {
  try {
    def message = _buildSlackMessage(cfg, statusLabel)
    slackSend(
      channel    : cfg.slackChannel,
      tokenCredentialId: cfg.slackCredId,
      color      : color,
      message    : message
    )
  } catch (e) {
    echo "[NOTIFY] ⚠ Slack notification failed (non-fatal): ${e.message}"
  }
}

// ── Private: build Slack message text ───────────────────────
private String _buildSlackMessage(def cfg, String status) {
  return """
*Wanderlust · Build #${cfg.buildNumber} · ${status}*
> Branch      : `${cfg.gitBranch}`
> Environment : `${cfg.deployEnvironment}`
> Commit      : `${cfg.gitCommitShort}` by ${cfg.gitAuthor}
> Message     : ${cfg.gitCommitMsg}
> Frontend    : `${cfg.frontendImageTag}`
> Backend     : `${cfg.backendImageTag}`
> Duration    : ${cfg.getBuildDuration()}
> Build URL   : ${env.BUILD_URL}
  """.stripIndent()
}
