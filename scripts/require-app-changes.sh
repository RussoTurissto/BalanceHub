#!/usr/bin/env bash
# Public releases must contain runtime source changes since their baseline.
# RCs compare with the preceding public RC/stable; stable compares with stable.
# Applies to both automatic and manual builds.
set -euo pipefail

[[ "$RELEASE_CHANNEL" == "rc" || "$RELEASE_CHANNEL" == "stable" ]] || exit 0
[[ "$GITHUB_REF" == "refs/heads/main" ]] || {
  echo "::error::Only main can publish a release"; exit 1;
}
[[ -n "$PREVIOUS_RELEASE_TAG" && -n "$GITHUB_SHA" ]] || {
  echo "::error::Missing previous release tag or source SHA"; exit 1;
}
[[ "$PREVIOUS_RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-rc[1-9][0-9]*)?$ ]] || {
  echo "::error::Invalid previous release tag"; exit 1;
}
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || {
  echo "::error::Checkout does not match intended release commit"; exit 1;
}

# A shallow checkout does not contain earlier release commits. Fetch just
# the baseline tag: git diff can compare trees without full history.
git fetch --quiet --no-tags --depth=1 origin "refs/tags/$PREVIOUS_RELEASE_TAG"
base_sha="$(git rev-parse 'FETCH_HEAD^{commit}')"

# Build pipeline, tests, documentation, release notes and version metadata
# are excluded. Only changes in actual app runtime sources warrant an RC.
runtime_paths=(
  app/src/main/
  library-android/src/main/
  library-compose/src/main/
  library-kotlin/src/main/
)
status=0
git diff --quiet "$base_sha" "$GITHUB_SHA" -- "${runtime_paths[@]}" || status=$?
case "$status" in
  0)
    echo "::error::No runtime app changes since $PREVIOUS_RELEASE_TAG; refusing redundant release"
    exit 1
    ;;
  1)
    echo "Runtime changes since $PREVIOUS_RELEASE_TAG:"
    git diff --name-only "$base_sha" "$GITHUB_SHA" -- "${runtime_paths[@]}"
    ;;
  *)
    echo "::error::Unable to compare source with $PREVIOUS_RELEASE_TAG"
    exit 1
    ;;
esac
