#!/usr/bin/env bash
# One-time provisioning for the sdlc dev container (postCreateCommand).
# Idempotent — safe to re-run:  bash .devcontainer/post-create.sh
set -euo pipefail

echo "==> sdlc dev container: provisioning"

# 1. Volume ownership. Named volumes are created root-owned. Chown the SHARED
#    volumes NON-recursively (/cache/gradle, ~/.claude, ~/.config/gh) — they are
#    shared with every other dev container on this machine.
#    (Do NOT chown /var/lib/docker — the docker-in-docker feature owns it.)
echo "==> Fixing volume ownership"
sudo mkdir -p /cache/gradle ~/.claude ~/.config/gh
sudo chown vscode:vscode /cache /cache/gradle ~/.claude ~/.config ~/.config/gh

# 1b. Migrate a pre-CLAUDE_CONFIG_DIR login onto the shared volume (see
#     devcontainer.json for why the account file otherwise escapes the mount).
if [ -f ~/.claude.json ] && [ ! -f ~/.claude/.claude.json ]; then
  echo "==> Migrating ~/.claude.json onto the shared claude volume"
  mv ~/.claude.json ~/.claude/.claude.json
fi

# 2. Toolchain. Sync Node to the repo pin (fnm reads .node-version).
export FNM_DIR="$HOME/.fnm"
export PATH="$FNM_DIR:$PATH"
eval "$(fnm env)"
fnm use --install-if-missing
fnm default "$(fnm current)"
echo "==> Toolchain: node $(node --version), openspec $(openspec --version), redocly $(redocly --version)"
echo "==> Java: $(java -version 2>&1 | head -1)"

# 3. Repo-managed git hooks (Spotless on commit) into .git/hooks.
echo "==> Installing git hooks"
./gradlew --quiet installGitHooks || echo "  ! installGitHooks skipped (run ./gradlew installGitHooks later)"

# 4. Warm the Gradle build — resolves dependencies into the shared /cache/gradle
#    so the first real build is fast. Best effort; don't fail provisioning.
echo "==> Warming Gradle (this resolves dependencies into /cache/gradle)"
./gradlew --quiet classes testClasses --parallel || \
  echo "  ! Gradle warm skipped — run ./gradlew build later."

echo "==> Done."
