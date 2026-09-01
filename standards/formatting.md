# Formatting & Tooling Standard

Formatting is automated and non-negotiable, so it is never a topic in code review. All Java is
formatted with **google-java-format**, applied automatically on commit and enforced in CI.

## 1. google-java-format via Spotless

- Java source is formatted with **google-java-format** (the AOSP/Google style). This is the single
  canonical style; do not hand-format, and do not argue style in review — the tool decides.
- Wire it through the **Spotless** Gradle plugin:

```kotlin
// build.gradle.kts
plugins {
    id("com.diffplug.spotless") version "<pinned>"
}

spotless {
    java {
        googleJavaFormat("<pinned>")   // pin the version for reproducible formatting
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

- Commands:
  - `./gradlew spotlessApply` — reformat the code.
  - `./gradlew spotlessCheck` — verify formatting (fails if anything is unformatted).

## 2. Auto-format on commit (git pre-commit hook)

- Formatting is applied **automatically on commit** so unformatted code never enters history.
- Install a git **pre-commit hook** that formats staged Java files and re-stages them before the
  commit completes. Manage hooks in-repo (e.g. a `git-hooks/` directory wired via a Gradle task, or a
  hook manager) so every clone gets the same hook — don't rely on developers installing it by hand.

Reference pre-commit hook:

```bash
#!/usr/bin/env bash
# .git hook: format staged Java, then re-stage
set -euo pipefail
staged=$(git diff --cached --name-only --diff-filter=ACM -- '*.java' || true)
[ -z "$staged" ] && exit 0
./gradlew --quiet spotlessApply
echo "$staged" | xargs git add
```

- The project provides a one-command setup (e.g. `./gradlew installGitHooks` or a documented script) so
  a fresh clone is formatting-enabled in one step.

## 3. CI enforcement (backstop)

- CI runs `./gradlew spotlessCheck` (part of `./gradlew build`) on every push/PR. If the hook was
  bypassed (`--no-verify`) or missing, CI **fails**. The hook is the convenience; CI is the guarantee.

## 4. Rules

- Never commit unformatted code; never disable Spotless to get a commit through.
- Pin the google-java-format and Spotless versions — an unpinned formatter bump reformats the whole
  tree and produces noisy, unreviewable diffs.
- Formatting-only changes go in their own commit, separate from behavioral changes, to keep diffs
  reviewable.
- This standard covers Java. Apply the same automate-and-enforce approach to other file types (e.g.
  SQL, YAML, Kotlin build scripts) with their own Spotless steps if/when added.
