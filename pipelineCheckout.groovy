// ============================================================
//  vars/pipelineCheckout.groovy
//  Stage 02 · Source Code Checkout
//  - Shallow clone for speed
//  - Captures git metadata into cfg for traceability
// ============================================================
def call(def cfg, def utils) {

  utils.sectionHeader('Stage 02 · Source Code Checkout')

  checkout([
    $class           : 'GitSCM',
    branches         : [[name: "*/${env.BRANCH_NAME ?: 'main'}"]],
    userRemoteConfigs: [[
      url          : cfg.gitRepoUrl,
      credentialsId: cfg.gitCredId
    ]],
    extensions: [
      [$class: 'CleanBeforeCheckout'],
      [$class: 'CloneOption', depth: 1, shallow: true, noTags: false]
    ]
  ])

  // ── Capture git metadata into cfg ──────────────────────
  cfg.gitCommitShort = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
  cfg.gitCommitMsg   = sh(script: 'git log -1 --format="%s"',   returnStdout: true).trim()
  cfg.gitAuthor      = sh(script: 'git log -1 --format="%an"',  returnStdout: true).trim()
  cfg.gitBranch      = env.BRANCH_NAME ?: sh(
    script: 'git rev-parse --abbrev-ref HEAD',
    returnStdout: true
  ).trim()

  echo "[CHECKOUT] ✔ Commit : ${cfg.gitCommitShort}"
  echo "[CHECKOUT] ✔ Author : ${cfg.gitAuthor}"
  echo "[CHECKOUT] ✔ Message: ${cfg.gitCommitMsg}"
  echo "[CHECKOUT] ✔ Branch : ${cfg.gitBranch}"
}
