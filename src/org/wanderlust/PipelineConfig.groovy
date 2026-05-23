// ============================================================
//  src/org/wanderlust/PipelineConfig.groovy
//  Central configuration object.
//  Instantiated once in wanderlustPipeline.groovy and passed
//  to every module so state is consistent across all stages.
// ============================================================
package org.wanderlust

class PipelineConfig implements Serializable {

  // ── Build Metadata ──────────────────────────────────────
  String buildNumber
  String buildTag
  String gitCommitShort
  String gitCommitMsg
  String gitAuthor
  String gitBranch
  long   pipelineStartTime

  // ── Image Tags ──────────────────────────────────────────
  String frontendImageTag
  String backendImageTag

  // ── Project Config (from Jenkinsfile) ───────────────────
  String projectName
  String gitRepoUrl
  String gitopsRepoUrl
  String registryNamespace
  String imageFrontend
  String imageBackend
  String frontendDir
  String backendDir
  String k8sManifestsDir
  String dockerCredId
  String gitCredId
  String nvdApiKeyId
  String sonarServerName
  String sonarProjectKey
  String slackChannel
  String slackCredId
  int    timeoutMinutes
  int    trivyExitCode
  boolean qualityGateWait

  // ── Runtime State ───────────────────────────────────────
  String  deployEnvironment
  boolean skipTests
  boolean skipSecurityScan
  boolean forcePush
  String  reportsDir = 'security-reports'

  // ── Constructor ─────────────────────────────────────────
  PipelineConfig(Map args, def env, def params) {
    // Populate from Jenkinsfile args
    args.each { k, v -> this."${k}" = v }

    // Populate from runtime
    this.buildNumber       = env.BUILD_NUMBER
    this.buildTag          = "b${env.BUILD_NUMBER}"
    this.pipelineStartTime = System.currentTimeMillis()
    this.deployEnvironment = params.DEPLOY_ENVIRONMENT ?: 'dev'
    this.skipTests         = params.SKIP_TESTS         ?: false
    this.skipSecurityScan  = params.SKIP_SECURITY_SCAN ?: false
    this.forcePush         = params.FORCE_PUSH         ?: false

    // Derive image tags
    this.frontendImageTag  = "${this.imageFrontend}:frontend-b${env.BUILD_NUMBER}"
    this.backendImageTag   = "${this.imageBackend}:backend-b${env.BUILD_NUMBER}"
  }

  // ── Helpers ─────────────────────────────────────────────
  String getBuildDuration() {
    def secs = ((System.currentTimeMillis() - pipelineStartTime) / 1000).toInteger()
    return "${(secs / 60).toInteger()}m ${secs % 60}s"
  }

  String getSummaryBanner(String status) {
    return """
╔══════════════════════════════════════════════════════╗
║       WANDERLUST · BUILD #${buildNumber.padLeft(4,'0')} · ${status.padRight(10)}     ║
╠══════════════════════════════════════════════════════╣
║  Branch   : ${gitBranch?.padRight(42) ?: 'N/A'.padRight(42)}║
║  Commit   : ${gitCommitShort?.padRight(42) ?: 'N/A'.padRight(42)}║
║  Author   : ${gitAuthor?.padRight(42) ?: 'N/A'.padRight(42)}║
║  Env      : ${deployEnvironment?.padRight(42) ?: 'N/A'.padRight(42)}║
║  Duration : ${getBuildDuration().padRight(42)}║
║  Frontend : frontend-b${buildNumber.padRight(36)}║
║  Backend  : backend-b${buildNumber.padRight(37)}║
╚══════════════════════════════════════════════════════╝""".stripIndent()
  }
}
