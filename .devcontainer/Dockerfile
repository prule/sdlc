# syntax=docker/dockerfile:1
# Dev container image for sdlc.
#
# VSSW dev container standard, trimmed for this repo: a Java 25 / Gradle backend
# whose only Node use is global CLIs (OpenSpec + Redocly). Node via fnm (NOT
# corepack); no pnpm/Playwright (this is not a JS app). JDK 25 and nested Docker
# (for Testcontainers) are layered as Dev Container Features in devcontainer.json.
#
# Node is driven by .node-version (fnm reads it). Bump the ARG below in sync.

FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# --- Pinned versions (keep in sync with the repo's version files) ------------
ARG NODE_VERSION=22
# OpenSpec CLI — the committed .claude/skills/openspec-* skills require it. Use
# the SCOPED package; the bare `openspec` on npm is an empty placeholder.
ARG OPENSPEC_VERSION=1.9.0
# Redocly CLI — bundles the split OpenAPI spec before openapi-generator (PLAT-002)
# and lints it in CI. Pinned for reproducible bundling.
ARG REDOCLY_VERSION=2.4.0

# The base image ships a non-root `vscode` user with passwordless sudo; build the
# JS toolchain under that user so file ownership matches at runtime.
USER vscode
ENV FNM_DIR=/home/vscode/.fnm

# --- fnm + Node --------------------------------------------------------------
RUN curl -fsSL https://fnm.vercel.app/install | bash -s -- --install-dir "$FNM_DIR" --skip-shell \
    && "$FNM_DIR/fnm" install "$NODE_VERSION" \
    && "$FNM_DIR/fnm" default "$NODE_VERSION"

# --- Shell wiring ------------------------------------------------------------
# fnm on PATH for every interactive shell; auto-selects Node on `cd`.
RUN <<'EOF'
set -e
for shell in bash zsh; do
  rc="/home/vscode/.${shell}rc"
  touch "$rc"
  {
    echo ''
    echo '# --- sdlc dev container: fnm ---'
    echo 'export FNM_DIR="$HOME/.fnm"'
    echo 'export PATH="$FNM_DIR:$PATH"'
    echo "eval \"\$(fnm env --use-on-cd --shell ${shell})\""
  } >> "$rc"
done
EOF

# Make Node available to non-interactive RUN/postCreate steps too.
ENV PATH="${FNM_DIR}:/home/vscode/.fnm/aliases/default/bin:${PATH}"

# --- Global CLIs (OpenSpec + Redocly) ----------------------------------------
# npm (into the fnm Node's bin, already on PATH). Container dev tools, not app deps.
RUN npm install -g @fission-ai/openspec@${OPENSPEC_VERSION} @redocly/cli@${REDOCLY_VERSION}
