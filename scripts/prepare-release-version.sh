#!/usr/bin/env bash
# Derive the next public RC tag or an explicitly approved stable version.
set -euo pipefail

target="$(sed -n 's/^NEXT_RELEASE_VERSION=//p' gradle.properties | head -1 | tr -d '\r')"
[[ "$target" =~ ^[1-9][0-9]*\.[0-9]+\.[0-9]+$ ]] || {
  echo "::error::NEXT_RELEASE_VERSION must be X.Y.Z"
  exit 1
}

current="$(gh api "repos/$GITHUB_REPOSITORY/releases/latest" --jq '.tag_name')"
[[ "$current" =~ ^v([1-9][0-9]*)\.([0-9]+)\.([0-9]+)$ ]] || {
  echo "::error::Cannot verify latest stable release"
  exit 1
}
old_major="${BASH_REMATCH[1]}"
old_minor="${BASH_REMATCH[2]}"
old_patch="${BASH_REMATCH[3]}"
if [[ "$target" != "$old_major.$old_minor.$((old_patch + 1))" &&
      "$target" != "$old_major.$((old_minor + 1)).0" ]]; then
  echo "::error::Expected next patch or minor version after $current, got $target"
  exit 1
fi

# A stable approval is single-use: only the commit that adds/changes this
# version marker may automatically publish its exact stable version.
approval_path="release/approved-stable-version.txt"
push_approval=false
if [[ "${GITHUB_EVENT_NAME:-}" == "push" &&
      "${GITHUB_REF:-}" == "refs/heads/main" && -s "$approval_path" ]]; then
  # The default Actions checkout is shallow; fetch its parent to verify
  # that this push actually changed the explicit owner-approved marker.
  if ! git rev-parse --verify 'HEAD^' >/dev/null 2>&1; then
    git fetch --quiet --no-tags --depth=2 origin "${GITHUB_SHA:?}"
  fi
  git rev-parse --verify 'HEAD^' >/dev/null 2>&1 || {
    echo "::error::Cannot verify parent commit for stable approval"
    exit 1
  }
  if ! git diff --quiet HEAD^ HEAD -- "$approval_path"; then
    approved_version="$(tr -d '\r\n' < "$approval_path")"
    [[ "$approved_version" == "$target" ]] || {
      echo "::error::Committed stable approval must match NEXT_RELEASE_VERSION"
      exit 1
    }
    push_approval=true
  fi
fi
channel="rc"
previous_tag="$current"
if [[ "${GITHUB_EVENT_NAME:-}" == "workflow_dispatch" &&
      "${INPUT_PUBLISH_STABLE:-false}" == "true" ]]; then
  [[ "${INPUT_APPROVED_VERSION:-}" == "$target" ]] || {
    echo "::error::Stable publication requires a matching approved_version"
    exit 1
  }
  channel="stable"
  version="$target"
  notes="release/notes-$target.md"
elif [[ "$push_approval" == "true" ]]; then
  [[ "${INPUT_PUBLISH_STABLE:-false}" != "true" && -z "${INPUT_APPROVED_VERSION:-}" ]] || {
    echo "::error::Push approval cannot be combined with manual release inputs"
    exit 1
  }
  channel="stable"
  version="$target"
  notes="release/notes-$target.md"
elif [[ "${INPUT_PUBLISH_STABLE:-false}" == "true" ]]; then
  echo "::error::Stable publication requires workflow_dispatch"
  exit 1
else
  [[ -z "${INPUT_APPROVED_VERSION:-}" ]] || {
    echo "::error::approved_version is only accepted for stable publication"
    exit 1
  }
  # Public RC tags are permanent. Private historical drafts with untagged-*
  # names do not count, and cannot alter the monotonic RC sequence.
  max_rc=0
  while IFS= read -r found_tag; do
    if [[ "$found_tag" == "v$target-rc"* ]]; then
      suffix="${found_tag#v$target-rc}"
      if [[ "$suffix" =~ ^[1-9][0-9]*$ ]] && (( 10#$suffix > max_rc )); then
        max_rc=$((10#$suffix))
        previous_tag="$found_tag"
      fi
    fi
  done < <(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" |
    jq -r '.[] | select(.draft == false and .prerelease == true) | .tag_name')
  version="$target-rc$((max_rc + 1))"
  notes="release/notes-$target-rc.md"
fi

[[ -s "$notes" ]] || {
  echo "::error::Release notes not found: $notes"
  exit 1
}
tag="v$version"
if gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$tag" >/dev/null 2>&1 ||
   gh release view "$tag" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  echo "::error::Tag or release already exists: $tag"
  exit 1
fi
if [[ "$channel" == "stable" ]]; then
  previous_count="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" |
    jq -r --arg prefix "v$target-rc" \
      '.[] | select(.draft == false and .prerelease == true and
       (.tag_name | startswith($prefix))) | .tag_name' | wc -l)"
  [[ "$previous_count" -ge 1 ]] || {
    echo "::error::Stable version $target requires a public RC first"
    exit 1
  }
fi

{
  printf 'STABLE_APPROVED_ON_PUSH=%s\n' "$push_approval"
  printf 'BUILD_VERSION_NAME=%s\n' "$version"
  printf 'RELEASE_TAG=%s\n' "$tag"
  printf 'RELEASE_CHANNEL=%s\n' "$channel"
  printf 'RELEASE_NOTES_PATH=%s\n' "$notes"
  printf 'PREVIOUS_RELEASE_TAG=%s\n' "$previous_tag"
} >> "$GITHUB_ENV"
echo "Selected $tag ($channel), notes $notes" >> "$GITHUB_STEP_SUMMARY"
echo "Selected $tag ($channel); current stable is $current"
