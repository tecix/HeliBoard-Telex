#!/usr/bin/env bash
# Merge upstream HeliBoard into the current branch, then run the Vietnamese Telex tests.
#
# Usage: scripts/sync-upstream.sh [upstream-ref]
#   upstream-ref: a branch (default: main) or a release tag such as v4.4
#
# Environment:
#   UPSTREAM_URL       upstream repository (default: https://github.com/HeliBorg/HeliBoard.git)
#   ABORT_ON_CONFLICT  1 = abort the merge on conflicts (CI); default leaves it for manual resolution
#   SKIP_TESTS         1 = only merge
#
# Exit codes: 0 merged and tests passed, or already up to date
#             2 merge conflict    3 Vietnamese tests failed    1 other error
# Writes status/target_sha/new_commits/conflicts/tests to $GITHUB_OUTPUT when set.
# See docs/UPSTREAM_SYNC.md for the hook points to check when resolving conflicts.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/HeliBorg/HeliBoard.git}"
REF="${1:-main}"

output() {
    echo "$1=$2"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "$1=$2" >> "$GITHUB_OUTPUT"; fi
}

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "error: working tree has uncommitted changes" >&2
    exit 1
fi

if git remote get-url upstream >/dev/null 2>&1; then
    git remote set-url upstream "$UPSTREAM_URL"
else
    git remote add upstream "$UPSTREAM_URL"
fi

# Tags go to a separate namespace so they never clash with this fork's own tags.
if git ls-remote --exit-code --tags upstream "refs/tags/$REF" >/dev/null 2>&1; then
    git fetch --no-tags upstream "+refs/tags/$REF:refs/upstream-tags/$REF"
    TARGET="refs/upstream-tags/$REF"
else
    git fetch --no-tags upstream "+refs/heads/$REF:refs/remotes/upstream/$REF"
    TARGET="refs/remotes/upstream/$REF"
fi
TARGET_SHA="$(git rev-parse "$TARGET^{commit}")"
output target_sha "$TARGET_SHA"

if git merge-base --is-ancestor "$TARGET_SHA" HEAD; then
    output status up-to-date
    echo "Already contains upstream $REF (${TARGET_SHA:0:8})."
    exit 0
fi
output new_commits "$(git rev-list --count "HEAD..$TARGET_SHA")"

if ! git merge --no-ff --no-edit -m "Merge upstream HeliBoard $REF (${TARGET_SHA:0:8})" "$TARGET_SHA"; then
    conflicts="$(git diff --name-only --diff-filter=U | paste -sd ' ' -)"
    output status conflict
    output conflicts "$conflicts"
    echo "Merge conflicts in: $conflicts" >&2
    if [ "${ABORT_ON_CONFLICT:-0}" = 1 ]; then
        git merge --abort
    else
        echo "Resolve them (see docs/UPSTREAM_SYNC.md), commit, then run the tests again." >&2
    fi
    exit 2
fi
output status merged

if [ "${SKIP_TESTS:-0}" = 1 ]; then exit 0; fi

# Engine tests, Vietnamese integration tests and the end-to-end typing tests through InputLogic.
if ./gradlew --no-daemon :viettelex-core:test :app:testRunTestsUnitTest \
        --tests 'helium314.keyboard.viettelex.*' \
        --tests 'helium314.keyboard.latin.VietTelexInputLogicTest'; then
    output tests passed
else
    output tests failed
    exit 3
fi
