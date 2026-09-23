#!/usr/bin/env bash
# Rebuilds tagged releases from source and checks the APKs against the ones GitHub holds.
#
# The rebuild mirrors .github/workflows/fdroid.yml: a fresh shallow clone of the tag with a stub
# local.properties (so nothing is signed and no developer override reaches BuildConfig), bisq2
# built from the commit pinned in gradle/libs.versions.toml, unsigned per-ABI APKs. The working
# checkout this runs from is never read or modified unless --in-place asks to build it.
#
#   connect_* tags  -> :apps:clientApp:assembleFdroidRelease
#   anode_* tags    -> :apps:nodeApp:assembleRelease
#
# Each rebuilt APK is compared with the published APK of the same file name:
#   unsigned reference (the fdroid.yml tag run artifact)  -> byte for byte
#   signed reference (a GitHub release asset)             -> `apksigcopier compare`, the check
#       F-Droid's reproducible-builds path runs: copy the published signature onto the rebuild
#       and verify it, so everything except the signature has to be identical.
#
# Usage: scripts/verify-reproducible-build.sh [options] <tag> [<tag> ...]
#
#   --source workflow|release  Where the reference APKs come from, via gh (default: workflow).
#                              Workflow artifacts expire after 14 days; release assets do not.
#   --reference <dir|zip|url>  Compare against these APKs instead of asking gh. One tag only.
#                              URLs must be https and are fetched without credentials.
#   --repo <owner/name>        Repository to clone and query (default: bisq-network/bisq-mobile).
#   --abis <list>              ABIs to compare (default: fdroid.yml's armeabi-v7a,arm64-v8a,x86,x86_64).
#                              `universal` is accepted for anode_* tags against a signed release or
#                              --reference; it builds without -Pabi, the way the release is packaged.
#   --work-dir <dir>           Where clones, builds and the report go (default: a new temp dir).
#                              Kept after the run. Re-using one skips an already published bisq2.
#   --diffoscope               Also write a diffoscope report for each APK that differs (slow).
#   --in-place                 Build this checkout instead of a fresh clone. One tag only; HEAD must
#                              be its commit, with no uncommitted or untracked changes, and
#                              local.properties may set nothing but sdk.dir.
#   --bisq2 <dir>              Build bisq2 from this checkout instead of a fresh shallow fetch. It
#                              must be clean and at the pinned commit. bisq2 embeds
#                              `git rev-parse --short HEAD`, whose length grows with the clone's
#                              history, so matching a release built from a full clone needs one.
#   --skip-bisq2-publish       Use the bisq2 jars already in ~/.m2 instead of building them. Their
#                              version is shared by many commits, so the commit hash bisq:common
#                              embeds is checked against the pinned one.
#
# Exit status: 0 every APK reproduced, 1 at least one differs or has no counterpart,
# 2 usage, environment or build error.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

REPO="bisq-network/bisq-mobile"
SOURCE="workflow"
REFERENCE=""
ABIS="armeabi-v7a,arm64-v8a,x86,x86_64"
WORK_DIR=""
DIFFOSCOPE=0
IN_PLACE=0
BISQ2_DIR=""
SKIP_BISQ2_PUBLISH=0
TAGS=()

die() {
  echo "error: $*" >&2
  exit 2
}

warn() {
  echo "warning: $*" >&2
}

log() {
  echo "==> $*" >&2
}

usage() {
  sed -n '/^# Usage:/,/^# 2 usage/{s/^# \{0,1\}//;p}' "${BASH_SOURCE[0]}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source) SOURCE="${2:?--source needs a value}"; shift 2 ;;
    --reference) REFERENCE="${2:?--reference needs a value}"; shift 2 ;;
    --repo) REPO="${2:?--repo needs a value}"; shift 2 ;;
    --abis) ABIS="${2:?--abis needs a value}"; shift 2 ;;
    --work-dir) WORK_DIR="${2:?--work-dir needs a value}"; shift 2 ;;
    --diffoscope) DIFFOSCOPE=1; shift ;;
    --in-place) IN_PLACE=1; shift ;;
    --bisq2) BISQ2_DIR="${2:?--bisq2 needs a value}"; shift 2 ;;
    --skip-bisq2-publish) SKIP_BISQ2_PUBLISH=1; shift ;;
    -h | --help) usage; exit 0 ;;
    -*) usage >&2; die "unknown option $1" ;;
    *) TAGS+=("$1"); shift ;;
  esac
done

[[ ${#TAGS[@]} -gt 0 ]] || { usage >&2; die "no tag given"; }
[[ "$SOURCE" == workflow || "$SOURCE" == release ]] || die "--source must be workflow or release"
[[ "$REPO" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || die "--repo must look like owner/name"
[[ "$ABIS" =~ ^[a-z0-9_-]+(,[a-z0-9_-]+)*$ ]] || die "--abis must be a comma separated ABI list"

# `universal` is not an -Pabi value: AppArtifactsPlugin drops the universal APK as soon as any
# ABI is named. It only comes from a build without -Pabi, which is how the release itself is
# packaged (docs/android.md) and which builds every ABI; the ones not asked for go uncompared.
WANT_UNIVERSAL=0
IFS=, read -ra ABI_LIST <<< "$ABIS"
for abi in "${ABI_LIST[@]}"; do
  [[ $abi != universal ]] || WANT_UNIVERSAL=1
done
if [[ $WANT_UNIVERSAL -eq 1 ]]; then
  [[ -n "$REFERENCE" || "$SOURCE" == release ]] ||
    die "fdroid.yml artifacts carry no universal APK; use --source release or --reference"
  for tag in "${TAGS[@]}"; do
    [[ "$tag" != connect_* ]] || die "$tag: the fdroid flavor ships no universal APK"
  done
fi

if [[ $IN_PLACE -eq 1 && ${#TAGS[@]} -ne 1 ]]; then
  die "--in-place builds the one commit this checkout is at, so it takes exactly one tag"
fi
if [[ -n "$BISQ2_DIR" ]]; then
  [[ $SKIP_BISQ2_PUBLISH -eq 0 ]] || die "--bisq2 and --skip-bisq2-publish exclude each other"
  git -C "$BISQ2_DIR" rev-parse --git-dir > /dev/null 2>&1 || die "--bisq2 $BISQ2_DIR is not a git checkout"
  BISQ2_DIR="$(cd "$BISQ2_DIR" && pwd)"
fi

if [[ -n "$REFERENCE" && ${#TAGS[@]} -ne 1 ]]; then
  die "--reference names the APKs of one release, so it takes exactly one tag"
fi
for tag in "${TAGS[@]}"; do
  # Also keeps the tag safe to use in refspecs and paths below.
  [[ "$tag" =~ ^(connect|anode)_[0-9A-Za-z._-]+$ ]] ||
    die "'$tag' is not a connect_* or anode_* tag (iOS tags have no APK to verify)"
done

REPO_URL="https://github.com/$REPO.git"

# ------------------------------------------------------------------------ prerequisites --

need() {
  command -v "$1" > /dev/null 2>&1 || die "$1 is required${2:+ ($2)}"
}

need git
need java
need unzip
need cmp
if command -v sha256sum > /dev/null 2>&1; then
  sha256() { sha256sum "$1" | cut -d ' ' -f 1; }
else
  need shasum "or sha256sum"
  sha256() { shasum -a 256 "$1" | cut -d ' ' -f 1; }
fi
if [[ -z "$REFERENCE" ]]; then
  need gh "https://cli.github.com, or pass --reference"
  gh auth status --hostname github.com > /dev/null 2>&1 || die "gh is not logged in (gh auth login)"
elif [[ "$REFERENCE" == https://* ]]; then
  need curl
fi
if [[ $DIFFOSCOPE -eq 1 ]]; then
  need diffoscope
fi

# The clone has no local.properties, so the SDK has to come from the environment. Falling back
# to this checkout's sdk.dir only reads that one key.
if [[ -z "${ANDROID_HOME:-}" ]]; then
  ANDROID_HOME="${ANDROID_SDK_ROOT:-}"
fi
if [[ -z "$ANDROID_HOME" && -f "$ROOT/local.properties" ]]; then
  ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | tail -n 1)"
fi
[[ -n "$ANDROID_HOME" && -d "$ANDROID_HOME" ]] || die "Android SDK not found; set ANDROID_HOME"
export ANDROID_HOME

java_major="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)"
if [[ "$java_major" != "21" ]]; then
  warn "java is version ${java_major:-unknown}; fdroid.yml builds with JDK 21 (Azul Zulu)"
fi
if [[ ! -d "$ANDROID_HOME/build-tools/35.0.0" ]]; then
  warn "build-tools 35.0.0 is not installed; fdroid.yml builds with it"
fi

# Signed references are compared with apksigcopier, which verifies with whatever `apksigner`
# is on PATH. Checked up front, since the first signed comparison comes after the build.
require_apksigcopier() {
  [[ -z "${APKSIGNER_READY:-}" ]] || return 0
  need apksigcopier "pip install apksigcopier; needed to compare against signed APKs"
  if ! command -v apksigner > /dev/null 2>&1; then
    local bt
    bt="$(ls "$ANDROID_HOME/build-tools" 2> /dev/null | sort -t . -k 1,1n -k 2,2n -k 3,3n | tail -n 1)"
    [[ -n "$bt" && -x "$ANDROID_HOME/build-tools/$bt/apksigner" ]] ||
      die "apksigner not found in PATH or $ANDROID_HOME/build-tools"
    PATH="$ANDROID_HOME/build-tools/$bt:$PATH"
  fi
  APKSIGNER_READY=1
}

# Release assets are always signed. A --reference is checked once fetched, still before the build.
if [[ -z "$REFERENCE" && "$SOURCE" == release ]]; then
  require_apksigcopier
fi

# Gradle gives these precedence over the project's gradle.properties, so they can change
# BuildConfig and with it the dex. Unset for the builds; the user-home file cannot be, only
# reported. Keys only, since values there are often credentials.
UNSET_GRADLE_ENV=()
for name in $(compgen -e | grep '^ORG_GRADLE_PROJECT_' || true); do
  UNSET_GRADLE_ENV+=("-u$name")
done
user_gradle_props="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"
if [[ -f "$user_gradle_props" ]]; then
  keys="$(sed -n 's/^[[:space:]]*\([^#![:space:]][^=:[:space:]]*\).*/\1/p' "$user_gradle_props" | grep -v '^org\.gradle\.' | paste -sd, - || true)"
  if [[ -n "$keys" ]]; then
    warn "$user_gradle_props overrides project properties ($keys); a mismatch may come from there"
  fi
fi

# ------------------------------------------------------------------------------ helpers --

if [[ -z "$WORK_DIR" ]]; then
  WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/bisq-repro.XXXXXX")"
fi
mkdir -p "$WORK_DIR"
WORK_DIR="$(cd "$WORK_DIR" && pwd)"
# The Maven repo path travels in JAVA_TOOL_OPTIONS, which splits on whitespace.
[[ "$WORK_DIR" != *[[:space:]]* ]] || die "the work dir path must not contain whitespace"
REPORT="$WORK_DIR/report.txt"
: > "$REPORT"
log "work dir: $WORK_DIR"

report() {
  printf '%s\n' "$*" >> "$REPORT"
}

# Runs a build step with its output in a log, printing only the tail when it fails.
run_logged() {
  local logfile="$1"
  shift
  if ! "$@" > "$logfile" 2>&1; then
    tail -n 40 "$logfile" >&2
    die "'$*' failed, full log: $logfile"
  fi
}

gradle_build() {
  local dir="$1" m2="$2" logfile="$3"
  shift 3
  # MAVEN_URL is the private mirror settings.gradle.kts reads on CI; unset so every bisq2 jar
  # comes from the source build. maven.repo.local keeps that build out of the user's ~/.m2 and
  # the user's ~/.m2 out of this build. It goes through JAVA_TOOL_OPTIONS rather than -D
  # because bisq2's publishAll runs nested gradlew invocations that would drop a -D, and the
  # daemon is off in GRADLE_OPTS too so those cannot reuse a running daemon that lacks it.
  # No build cache, so no task output is reused from builds of other sources. An empty m2
  # means the default ~/.m2, for --skip-bisq2-publish.
  local tool_options="${JAVA_TOOL_OPTIONS:-}"
  if [[ -n "$m2" ]]; then
    tool_options="${tool_options:+$tool_options }-Dmaven.repo.local=$m2"
  fi
  (
    cd "$dir"
    run_logged "$logfile" env -u MAVEN_URL ${UNSET_GRADLE_ENV[@]+"${UNSET_GRADLE_ENV[@]}"} \
      JAVA_TOOL_OPTIONS="$tool_options" \
      GRADLE_OPTS="${GRADLE_OPTS:+$GRADLE_OPTS }-Dorg.gradle.daemon=false" \
      ./gradlew --no-daemon --no-build-cache --console=plain "$@"
  )
}

# Commit a tag points at on the remote, peeling annotated tags.
remote_tag_commit() {
  local tag="$1" out
  out="$(git ls-remote "$REPO_URL" "refs/tags/$tag" "refs/tags/$tag^{}")" ||
    die "cannot reach $REPO_URL"
  [[ -n "$out" ]] || die "tag $tag does not exist in $REPO"
  # The peeled line, when present, sorts after the tag line.
  printf '%s\n' "$out" | tail -n 1 | cut -f 1
}

# Fails unless a checkout is at exactly this commit with nothing uncommitted or untracked.
# Line-ending-only differences are let through: bisq2 commits gradlew.bat with CRLF against its
# own `eol=crlf` attribute, so git reports it modified in every fresh checkout.
require_clean_at() {
  local dir="$1" commit="$2" what="$3" head
  head="$(git -C "$dir" rev-parse HEAD)"
  [[ "$head" == "$commit" ]] || die "$what is at $head, not $commit"
  git -C "$dir" diff --quiet --ignore-cr-at-eol HEAD -- &&
    [[ -z "$(git -C "$dir" ls-files --others --exclude-standard)" ]] ||
    die "$what has uncommitted or untracked changes"
}

# Builds bisq2 at the pinned commit and publishes it into a Maven repo of its own, once per
# commit and source: a local checkout embeds a longer commit hash than a shallow fetch, so their
# jars differ. The marker is written last, so an interrupted publish is redone, not trusted.
# Sets BISQ2_M2.
publish_bisq2() {
  local commit="$1" src m2
  if [[ -n "$BISQ2_DIR" ]]; then
    src="$BISQ2_DIR"
    m2="$WORK_DIR/m2-$commit-local"
  else
    src="$WORK_DIR/bisq2-$commit"
    m2="$WORK_DIR/m2-$commit"
  fi
  BISQ2_M2="$m2"
  if [[ -f "$m2/.published" ]]; then
    log "bisq2 $commit already published in $m2"
    return
  fi
  rm -rf "$m2"
  mkdir -p "$m2"
  if [[ -n "$BISQ2_DIR" ]]; then
    require_clean_at "$src" "$commit" "bisq2 checkout $src"
  else
    rm -rf "$src"
    mkdir -p "$src"
    git -C "$src" init --quiet
    git -C "$src" fetch --quiet --depth 1 https://github.com/bisq-network/bisq2.git "$commit" ||
      die "cannot fetch bisq2 commit $commit"
    git -C "$src" checkout --quiet --detach FETCH_HEAD
  fi
  log "building bisq2 @ $commit in $src"
  # Only in a fetch of our own: a mode change in a user's checkout would dirty it.
  [[ -n "$BISQ2_DIR" ]] || chmod +x "$src/gradlew"
  gradle_build "$src" "$m2" "${m2%/}.log" publishAll --stacktrace
  touch "$m2/.published"
}

# For --skip-bisq2-publish. bisq:common carries the commit it was built from as the
# COMMIT_SHORT_HASH constant of BuildVersion, which is the only way to tell apart jars of the
# same version. Other modules could still be stale, so this catches the common case only.
check_published_bisq2() {
  local commit="$1" version="$2" jar hash
  jar="$HOME/.m2/repository/bisq/common/$version/common-$version.jar"
  [[ -f "$jar" ]] || die "bisq2 $version is not in ~/.m2; drop --skip-bisq2-publish"
  hash="$(unzip -p "$jar" bisq/common/application/BuildVersion.class 2> /dev/null |
    strings -n 1 | awk 'found { print; exit } /^COMMIT_SHORT_HASH$/ { found = 1 }')"
  if [[ ! "$hash" =~ ^[0-9a-f]{7,40}$ ]]; then
    warn "cannot tell which commit $jar was built from; trusting it"
  elif [[ "$commit" != "$hash"* ]]; then
    die "$HOME/.m2 holds bisq2 $version built from $hash, not the pinned $commit; drop --skip-bisq2-publish"
  else
    log "using bisq2 $version from ~/.m2, built from $hash"
  fi
}

# Puts the reference APKs for one tag into $2, flattened.
fetch_reference() {
  local tag="$1" dest="$2" app="$3" commit="$4" raw="$2.raw"
  rm -rf "$dest" "$raw"
  mkdir -p "$dest" "$raw"

  if [[ -n "$REFERENCE" ]]; then
    local ref="$REFERENCE"
    if [[ "$ref" == https://* ]]; then
      local name="${ref##*/}"
      name="${name%%\?*}"
      [[ "$name" == *.apk ]] || name="reference.zip"
      curl --proto '=https' --tlsv1.2 -fsSL -o "$raw/$name" "$ref" || die "cannot download $ref"
      ref="$raw/$name"
    elif [[ "$ref" == *://* ]]; then
      die "--reference URLs must be https"
    fi
    if [[ -d "$ref" ]]; then
      find "$ref" -type f -name '*.apk' -exec cp {} "$dest/" \;
    elif [[ "$ref" == *.apk ]]; then
      cp "$ref" "$dest/"
    else
      unzip -q -o "$ref" -d "$raw/unzipped" || die "$ref is neither a directory, an APK nor a zip"
      find "$raw/unzipped" -type f -name '*.apk' -exec cp {} "$dest/" \;
    fi
    REFERENCE_LABEL="$REFERENCE"

  elif [[ "$SOURCE" == workflow ]]; then
    local artifact="fdroid-node-unsigned" run_id
    [[ $app == node ]] || artifact="fdroid-connect-unsigned"
    # A tag push run carries the tag as its branch. Requiring its head commit to be the tag's
    # guards against a tag that was moved after the run built it.
    run_id="$(gh run list -R "$REPO" --workflow fdroid.yml --event push --branch "$tag" \
      --status success --limit 20 --json databaseId,headSha \
      --jq "map(select(.headSha == \"$commit\")) | first | .databaseId // empty")"
    [[ -n "$run_id" ]] ||
      die "no successful fdroid.yml run for $tag at $commit; try --source release"
    gh run download "$run_id" -R "$REPO" -n "$artifact" -D "$raw" ||
      die "cannot download $artifact from run $run_id (artifacts expire after 14 days; try --source release)"
    # The run's own digests catch a download that was corrupted or tampered with in between.
    if [[ -f "$raw/SHA256SUMS.txt" ]]; then
      local sum file
      while read -r sum file; do
        [[ "$(sha256 "$raw/${file#\*}")" == "$sum" ]] ||
          die "$file does not match the SHA256SUMS.txt of run $run_id"
      done < "$raw/SHA256SUMS.txt"
    fi
    find "$raw" -type f -name '*.apk' -exec cp {} "$dest/" \;
    REFERENCE_LABEL="fdroid.yml run https://github.com/$REPO/actions/runs/$run_id"

  else
    gh release download "$tag" -R "$REPO" -p '*.apk' -D "$raw" ||
      die "cannot download the APKs of release $tag"
    find "$raw" -type f -name '*.apk' -exec cp {} "$dest/" \;
    REFERENCE_LABEL="release https://github.com/$REPO/releases/tag/$tag"
  fi

  rm -rf "$raw"
  compgen -G "$dest/*.apk" > /dev/null || die "no APK found in the reference for $tag"
}

# True when an APK file name carries one of the requested ABIs, as AppArtifactsPlugin names them:
# <Product>-<version>-<abi>-<versionCode>[-<flavor>].apk
wanted_apk() {
  local abi
  for abi in "${ABI_LIST[@]}"; do
    [[ "$1" =~ -${abi}-[0-9]+(-[a-z]+)?\.apk$ ]] && return 0
  done
  return 1
}

is_signed() {
  # v2+ signatures live in the APK Signing Block, v1 in META-INF.
  LC_ALL=C grep -q -a -F 'APK Sig Block 42' "$1" ||
    unzip -Z1 "$1" | grep -qE '^META-INF/[^/]+\.(SF|RSA|DSA|EC)$'
}

# Per-entry listing (CRC, sizes, method, name) without v1 signature files, so a diff of two
# listings names the entries that differ.
entry_listing() {
  unzip -lv "$1" |
    awk 'NR > 3 && $7 ~ /^[0-9a-f]{8}$/ {
      name = $8
      for (i = 9; i <= NF; i++) name = name " " $i
      print $7, $1, $3, $2, name
    }' |
    grep -vE ' META-INF/([^/]+\.(SF|RSA|DSA|EC)|MANIFEST\.MF)$' || true
}

# The commit AGP recorded at build time. A reference built from anything but the tagged commit
# cannot match, and this names that cause directly instead of leaving it to a dex diff.
built_from() {
  unzip -p "$1" META-INF/version-control-info.textproto 2> /dev/null |
    sed -n 's/^ *revision: *"\([0-9a-f]*\)".*/\1/p' | head -n 1
}

write_diff() {
  local rebuilt="$1" ref="$2" out="$3"
  {
    echo "--- reference $ref"
    echo "    built from commit ${REF_COMMIT:-unknown}"
    echo "+++ rebuilt   $rebuilt"
    echo "Entries as: crc32 length compressed-size method name"
    diff <(entry_listing "$ref") <(entry_listing "$rebuilt") || true
  } > "$out"
  if [[ $DIFFOSCOPE -eq 1 ]]; then
    diffoscope --text "${out%.txt}.diffoscope.txt" "$ref" "$rebuilt" > /dev/null 2>&1 || true
  fi
}

# ---------------------------------------------------------------------------- main loop --

FAILED=0

for tag in "${TAGS[@]}"; do
  case "$tag" in
    connect_*)
      app=client
      task=":apps:clientApp:assembleFdroidRelease"
      out_rel="apps/clientApp/build/outputs/apk/fdroid/release"
      ;;
    anode_*)
      app=node
      task=":apps:nodeApp:assembleRelease"
      out_rel="apps/nodeApp/build/outputs/apk/release"
      ;;
  esac

  tag_dir="$WORK_DIR/$tag"
  src="$tag_dir/src"
  mkdir -p "$tag_dir"

  commit="$(remote_tag_commit "$tag")"
  log "$tag -> $commit"

  if [[ $IN_PLACE -eq 1 ]]; then
    src="$ROOT"
    require_clean_at "$src" "$commit" "this checkout"
    # A clone gets none, but here the file is the developer's own: KEYSTORE_PATH would sign
    # the build and any other key overrides a project property that may reach BuildConfig.
    # Keys only in the message, since values are often credentials.
    if [[ -f "$src/local.properties" ]]; then
      keys="$(sed -n 's/^[[:space:]]*\([^#![:space:]][^=:[:space:]]*\).*/\1/p' "$src/local.properties" |
        grep -vx 'sdk\.dir' | paste -sd, - || true)"
      [[ -z "$keys" ]] ||
        die "--in-place: local.properties sets $keys; move it aside or run without --in-place"
    fi
  else
    # Shallow, like actions/checkout. BuildConfig.BUILD_COMMIT is read with git, so this has to
    # be a real clone rather than an exported tree.
    rm -rf "$src"
    git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$tag" "$REPO_URL" "$src"
    [[ "$(git -C "$src" rev-parse HEAD)" == "$commit" ]] ||
      die "$tag moved while cloning; run again"
  fi

  # Before the build, so a missing or expired reference fails in seconds rather than after it.
  log "fetching reference APKs for $tag"
  ref_dir="$tag_dir/reference"
  REFERENCE_LABEL=""
  fetch_reference "$tag" "$ref_dir" "$app" "$commit"
  for ref in "$ref_dir"/*.apk; do
    if is_signed "$ref"; then
      require_apksigcopier
      break
    fi
  done

  # The client resolves nothing from mavenLocal; an empty repo keeps it that way.
  m2="$WORK_DIR/m2-empty"
  if [[ $app == node ]]; then
    ndk="$(sed -nE 's/^android-ndk *= *"([^"]+)".*/\1/p' "$src/gradle/libs.versions.toml")"
    [[ -n "$ndk" ]] || die "android-ndk not found in libs.versions.toml at $tag"
    [[ -d "$ANDROID_HOME/ndk/$ndk" ]] ||
      die "NDK $ndk is not installed: sdkmanager \"ndk;$ndk\""
    bisq2_commit="$(sed -nE 's/^bisq-core-commit *= *"([0-9a-f]{40})".*/\1/p' "$src/gradle/libs.versions.toml")"
    [[ -n "$bisq2_commit" ]] || die "bisq-core-commit not found in libs.versions.toml at $tag"
    if [[ $SKIP_BISQ2_PUBLISH -eq 1 ]]; then
      bisq_core="$(sed -nE 's/^bisq-core *= *"([^"]+)".*/\1/p' "$src/gradle/libs.versions.toml")"
      [[ -n "$bisq_core" ]] || die "bisq-core not found in libs.versions.toml at $tag"
      check_published_bisq2 "$bisq2_commit" "$bisq_core"
      m2=""
    else
      publish_bisq2 "$bisq2_commit"
      m2="$BISQ2_M2"
    fi
  fi
  [[ -z "$m2" ]] || mkdir -p "$m2"

  # Tags cut before local.properties became optional fail to configure without one, and their
  # fdroid.yml wrote exactly this. No KEYSTORE_PATH, so nothing is signed; the companions are
  # read only for signing, so newer tags are unaffected.
  if [[ $IN_PLACE -eq 0 ]]; then
    printf '%s\n' KEYSTORE_PASSWORD=unused KEY_ALIAS=unused CLI_KEY_ALIAS=unused \
      KEY_PASSWORD=unused CLI_KEY_PASSWORD=unused > "$src/local.properties"
  fi

  # An earlier build of another version, or with another ABI set, leaves its APKs here.
  rebuilt_dir="$src/$out_rel"
  rm -rf "$rebuilt_dir"

  log "building $task at $tag in $src (log: $tag_dir/build.log)"
  [[ $IN_PLACE -eq 1 ]] || chmod +x "$src/gradlew"
  abi_args=()
  [[ $WANT_UNIVERSAL -eq 1 ]] || abi_args=("-Pabi=$ABIS")
  gradle_build "$src" "$m2" "$tag_dir/build.log" "$task" \
    -PallowUnsignedRelease=true ${abi_args[@]+"${abi_args[@]}"}

  compgen -G "$rebuilt_dir/*.apk" > /dev/null || die "the build produced no APK in $rebuilt_dir"

  report "$tag @ $commit"
  report "  reference: $REFERENCE_LABEL"
  # A re-used work dir still holds the previous run's diffs, which the report would cite.
  rm -rf "$tag_dir/diff"
  mkdir -p "$tag_dir/diff"

  for abi in "${ABI_LIST[@]}"; do
    if ! compgen -G "$rebuilt_dir/*-$abi-[0-9]*.apk" > /dev/null; then
      report "  MISSING    $abi  (the build produced no APK for it)"
      FAILED=1
    fi
  done

  for rebuilt in "$rebuilt_dir"/*.apk; do
    name="${rebuilt##*/}"
    wanted_apk "$name" || continue
    ref="$ref_dir/$name"
    sha="$(sha256 "$rebuilt")"
    if [[ ! -f "$ref" ]]; then
      report "  MISSING    $name  (no reference APK of that name)"
      FAILED=1
      continue
    fi
    if is_signed "$ref"; then
      if apksigcopier compare --unsigned "$ref" "$rebuilt" > "$tag_dir/diff/$name.apksigcopier.txt" 2>&1; then
        report "  MATCH      $name  (identical apart from the signature)  rebuilt sha256 $sha"
        rm -f "$tag_dir/diff/$name.apksigcopier.txt"
        continue
      fi
    elif cmp -s "$ref" "$rebuilt"; then
      report "  MATCH      $name  (byte for byte)  sha256 $sha"
      continue
    fi
    REF_COMMIT="$(built_from "$ref")"
    write_diff "$rebuilt" "$ref" "$tag_dir/diff/$name.txt"
    report "  DIFFERS    $name  rebuilt $sha  reference $(sha256 "$ref")"
    report "             entries: $tag_dir/diff/$name.txt"
    if [[ -f "$tag_dir/diff/$name.apksigcopier.txt" ]]; then
      report "             apksigcopier: $tag_dir/diff/$name.apksigcopier.txt"
    fi
    if [[ -n "$REF_COMMIT" && "$REF_COMMIT" != "$commit" ]]; then
      report "             the reference was built from $REF_COMMIT, not the tagged commit"
    fi
    FAILED=1
  done

  # The release also carries ABIs that were not asked for and, for Connect, the google flavor.
  # Leaving those out is expected and not a failure.
  for ref in "$ref_dir"/*.apk; do
    name="${ref##*/}"
    if ! wanted_apk "$name" || [[ ! -f "$rebuilt_dir/$name" ]]; then
      report "  SKIPPED    $name  (reference only, not compared)"
    fi
  done
  report ""
done

echo
cat "$REPORT"
if [[ $FAILED -eq 0 ]]; then
  echo "REPRODUCIBLE: every rebuilt APK matches its reference. Report: $REPORT"
else
  echo "NOT REPRODUCIBLE: see the MISSING / DIFFERS lines above. Report: $REPORT" >&2
fi
exit "$FAILED"
