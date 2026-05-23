// ============================================================
//  src/org/wanderlust/Utils.groovy
//  Reusable utility methods for all pipeline modules.
// ============================================================
package org.wanderlust

class Utils implements Serializable {

  def script  // Reference to Jenkins pipeline script context

  Utils(def script) {
    this.script = script
  }

  // ── Print a styled section header ───────────────────────
  void sectionHeader(String title) {
    script.echo """
\033[1;36m
── ${title} ${'─' * (55 - title.length())}
\033[0m""".stripIndent()
  }

  // ── Check if a file exists in the workspace ─────────────
  boolean fileExists(String path) {
    return script.fileExists(path)
  }

  // ── Safely run a shell command, return stdout ────────────
  String shOutput(String cmd) {
    return script.sh(script: cmd, returnStdout: true).trim()
  }

  // ── Validate required Jenkins credentials exist ─────────
  void validateCredentials(List<String> credIds) {
    credIds.each { id ->
      try {
        script.withCredentials([script.string(credentialsId: id, variable: 'TEST')]) {
          script.echo "  ✔ Credential '${id}': found"
        }
      } catch (e) {
        script.error "  ✘ Credential '${id}': NOT FOUND in Jenkins. Please add it."
      }
    }
  }

  // ── Check required CLI tools are available ──────────────
  void validateTools(List<String> tools) {
    tools.each { tool ->
      def result = script.sh(
        script: "command -v ${tool} >/dev/null 2>&1 && echo OK || echo MISSING",
        returnStdout: true
      ).trim()
      if (result == 'MISSING') {
        script.error "[VALIDATE] Required tool not found: ${tool}"
      }
      script.echo "  ✔ ${tool}: ${script.sh(script: "${tool} --version 2>&1 | head -1", returnStdout: true).trim()}"
    }
  }

  // ── Write a build summary to the reports directory ──────
  void writeSummary(String reportsDir, String content) {
    script.writeFile(
      file: "${reportsDir}/pipeline-summary.txt",
      text: content
    )
  }
}
