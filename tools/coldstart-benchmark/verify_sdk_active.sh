#!/usr/bin/env bash
# Unless explicitly stated otherwise all files in this repository are licensed
# under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
# STEP ZERO. Run this before anything else.
#
# Answers one question empirically: does this APK actually initialize the Datadog
# SDK at runtime, or not?
#
# A build that CONTAINS the SDK is not necessarily one that INITIALIZES it: feature
# flags, remote config, experiment buckets and consent gating can all leave it
# compiled in but inert. Inferring liveness from a trace is unreliable, so this
# script installs the build itself, attests it by md5, launches it via the real
# launcher intent, and reads the live thread list.
#
# Oracle: `CoreFeature.initialize()` calls `setupExecutors()` and immediately
# submits the NTP-sync task to `persistenceExecutorService`
# (dd-sdk-android-core/.../CoreFeature.kt:265-266). That executor is built with
# `DatadogThreadFactory`, which names threads `datadog-<ctx>-thread-<n>`
# (Linux truncates to 15 chars -> `datadog-storage`). The name is assembled at
# runtime from a string template, so R8/ProGuard cannot rename it. A completed
# `Datadog.initialize()` therefore ALWAYS leaves a `datadog-*` thread.
#
# Usage: ./verify_sdk_active.sh <apk> <your.app.id>
set -euo pipefail

APK="${1:?usage: $0 <apk> <package>}"
PKG="${2:?<package> required}"
SETTLE="${SETTLE:-20}"   # seconds to wait after launch before sampling

die() { echo "FATAL: $*" >&2; exit 2; }
[ -f "$APK" ] || die "APK not found: $APK"
case "$PKG" in *[!a-zA-Z0-9._]*|""|.*|*.) die "invalid application id: '$PKG'" ;; esac
case "$PKG" in *.*) ;; *) die "application id must be dotted, e.g. com.example.app" ;; esac
log() { echo "[$(date +%H:%M:%S)] $*" >&2; }

. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
dd_resolve_tools || exit 2
dd_require_device || exit 2
DD_ANDROID_USER=""
dd_resolve_android_user || exit 2

echo "device: $("$ADB" shell getprop ro.product.model | tr -d '\r') / $("$ADB" shell getprop ro.build.fingerprint | tr -d '\r')"
echo "android: $("$ADB" shell getprop ro.build.version.release | tr -d '\r') (sdk $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r'))"
echo "android user: $DD_ANDROID_USER"
echo

# Check the APK declares <package> BEFORE uninstalling anything. The md5
# attestation below runs only after the install, so it cannot prevent a mistyped
# package from wiping an unrelated app first. This script is documented as step
# zero, so it is the most likely place for that typo to be made.
if [ "${ALLOW_UNVERIFIED_PKG:-0}" = "1" ]; then
  log "WARNING: ALLOW_UNVERIFIED_PKG=1 -- the APK/package check is DISABLED."
  log "         'adb uninstall $PKG' will run against whatever app owns that id."
else
  [ -n "${AAPT2:-}" ] || die "aapt2 not found, so the APK's package name cannot be
       verified against '$PKG' before 'adb uninstall' destroys that app's data.
       Fix: install Android SDK build-tools, or set AAPT2=/path/to/aapt2.
       Override with ALLOW_UNVERIFIED_PKG=1 once you have checked by hand."
  APK_PKG=$("$AAPT2" dump badging "$APK" 2>/dev/null \
            | awk -F"'" '/^package: name=/{print $2; exit}' || true)
  [ -n "$APK_PKG" ] || die "aapt2 could not read a package name from $APK."
  [ "$APK_PKG" = "$PKG" ] || die "you passed package '$PKG' but the APK declares
       '$APK_PKG'. Refusing to uninstall '$PKG' -- that would wipe an unrelated
       app's data. Re-run with '$APK_PKG'."
  log "APK declares $APK_PKG, matches the package argument"
fi

# Say it at the point of destruction, not only in the README. This is the script an
# operator is most likely to run casually -- it is documented as "step zero" -- and it
# is just as destructive as the benchmark.
INSTALLED_PATHS=$(dd_package_path "$PKG") || die "cannot query the current install state"
if [ -n "$INSTALLED_PATHS" ]; then
  log "WARNING: $PKG is already installed. Uninstalling it now to guarantee a known"
  log "         install state -- THIS DELETES ITS APP DATA (accounts, caches, databases)."
fi
HOST_MD5=$(dd_md5 "$APK")
log "installing $(basename "$APK")  md5=$HOST_MD5"
dd_ensure_uninstalled "$PKG" || die "uninstall did not establish a clean install state"
"$ADB" install --user "$DD_ANDROID_USER" -r "$APK" >/dev/null \
  || die "install failed (v2/v3-signed APK requires Android 7+)"

REMOTE=$(dd_package_path "$PKG" | head -1 | sed 's/package://') \
  || die "cannot read the installed APK path"
DEV_MD5=$("$ADB" shell md5sum "$REMOTE" | awk '{print $1}' | tr -d '\r')
[ "$HOST_MD5" = "$DEV_MD5" ] \
  || die "APK attestation FAILED host=$HOST_MD5 device=$DEV_MD5 — the device is not running the APK you think it is"
log "APK attested OK  ($REMOTE)"
# The grepped line already reads "versionName=..."; prefixing it again printed
# "versionName=versionName=3.13.0-SNAPSHOT".
log "$("$ADB" shell dumpsys package "$PKG" | grep -m1 versionName | tr -d '\r' | xargs)"

# Match the permission state used by both measurement paths. Without this, a host
# app that gates Datadog.initialize() on a runtime permission fails this preflight
# even though the benchmark and trace pre-grant that permission before launching.
_GRANTED=""
# shellcheck disable=SC2329  # invoked indirectly by the EXIT trap below
restore_permissions() {
  local rc=$? p
  for p in ${_GRANTED:-}; do
    "$ADB" shell pm revoke --user "$DD_ANDROID_USER" "$PKG" "$p" >/dev/null 2>&1 || true
  done
  return "$rc"
}
trap restore_permissions EXIT
if ! dd_grant_runtime_permissions "$PKG"; then
  # Preserve successful grants for the EXIT trap even on a partial failure.
  _GRANTED="$DD_GRANTED_PERMISSIONS"
  die "runtime-permission setup was incomplete; refusing a different liveness scenario"
fi
_GRANTED="$DD_GRANTED_PERMISSIONS"
log "pre-granted $DD_GRANTED_PERMISSION_COUNT/$DD_RUNTIME_PERMISSION_COUNT runtime permissions"

# Real launcher intent, exactly as tapping the icon does.
LAUNCH=$("$ADB" shell cmd package resolve-activity --brief --user "$DD_ANDROID_USER" \
          -c android.intent.category.LAUNCHER "$PKG" \
          | tail -1 | tr -d '\r')
# `resolve-activity --brief` prints the literal text "No activity found" (exit 0)
# when nothing matches, so a bare non-empty test passes it straight through to
# `am start -n "No activity found"`. Verified on a moto g(60)s / Android 12.
# Check the SHAPE instead: it must be <pkg>/<component> for the app under test.
case "$LAUNCH" in
  "$PKG"/*) ;;
  *) die "could not resolve a launcher activity for $PKG (got '${LAUNCH:-nothing}')." ;;
esac
log "launcher activity: $LAUNCH"

"$ADB" shell am force-stop --user "$DD_ANDROID_USER" "$PKG"; sleep 2
"$ADB" shell logcat -c >/dev/null 2>&1 || true
"$ADB" shell am start -W --user "$DD_ANDROID_USER" -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER -n "$LAUNCH" \
  | tr -d '\r' | sed 's/^/    /'
log "settling ${SETTLE}s so any deferred/async init completes"
sleep "$SETTLE"

# All of the package's processes. An app can initialize Datadog in a private
# process (`<pkg>:startup`), which an exact-name `pidof` never sees -- this script
# would then report "Datadog is NOT initializing in this build" about a build whose
# SDK is live, and send the operator hunting a consent flag that is already set.
if ! PIDS=$(dd_pkg_pids "$PKG"); then
  die "SDK liveness is unknown: the full process listing failed or was not shaped as
       expected (the error above says which). There is deliberately no exact-name
       pidof fallback, because it omits private processes and therefore cannot prove
       absence."
fi
[ -n "$PIDS" ] || die "app is not running after launch"

# `die` exits 2, the setup-failure code -- deliberately NOT the exit 1 that means
# "Datadog is not initializing". Reading /proc directly here, an adb hiccup or a
# denied read left every thread list empty and the script fell out at the `grep .`
# below under `set -e`, exiting 1: this tool's own contract for "not live", printed
# without a single line of explanation. That is the confident false negative the
# skill tells agents to come here to avoid.
ALL=$(dd_thread_names "$PIDS") \
  || die "SDK liveness could not be verified on every process of $PKG (see above).
       An unreadable thread list is not evidence that Datadog is absent."
ALL=$(printf '%s' "$ALL" | grep . | sort)
DD=$(echo "$ALL" | grep '^datadog-' || true)
N_ALL=$(echo "$ALL" | grep -c . || true)
N_DD=$(echo "$DD" | grep -c . || true)

echo
echo "=============================================================="
echo " threads live in $(printf '%s\n' "$PIDS" | grep -c .) process(es): $N_ALL"
echo " datadog-* threads        : $N_DD"
# shellcheck disable=SC2001  # indenting every line of a list; parameter expansion cannot
[ -n "$DD" ] && echo "$DD" | sed 's/^/     /'
echo "--------------------------------------------------------------"
# Secondary evidence: is the NDK crash-reporting lib mapped in? Both this and the
# logcat count below are informational -- the verdict is taken from N_DD alone -- but
# an informational line still may not state more than its own check can distinguish,
# so an unreadable source prints "unknown" instead of a 0 that reads as "absent".
if NDKMAP=$(dd_mapped_lib_count libdatadog-ndk "$PIDS"); then
  echo " libdatadog-ndk.so mapped : $NDKMAP"
else
  echo " libdatadog-ndk.so mapped : unknown (see the error above)"
fi
# Tertiary: SDK's own logcat output. No pipeline around adb, so the status is adb's
# and not `grep`'s: `grep -ci` exits 1 on a count of zero, so piping straight into it
# reported 0 both for "the SDK logged nothing" and for "logcat could not be read".
if ! _DD_LOGCAT=$("$ADB" shell logcat -d 2>/dev/null); then
  echo " Datadog logcat lines     : unknown ('logcat -d' failed)"
elif [ -z "$_DD_LOGCAT" ]; then
  # A real device's buffer is never empty; empty means the read did not happen.
  echo " Datadog logcat lines     : unknown ('logcat -d' returned nothing)"
else
  echo " Datadog logcat lines     : $(printf '%s\n' "$_DD_LOGCAT" | tr -d '\r' \
                                       | grep -ci datadog || true)"
fi
echo "=============================================================="
echo

if [ "$N_DD" -gt 0 ]; then
  echo "RESULT: Datadog IS initializing in this build."
  echo "        => proceed to coldstart_bench.sh."
  exit 0
else
  echo "RESULT: Datadog is NOT initializing in this build, on this device,"
  echo "        with an attested install and a ${SETTLE}s settle window."
  echo "        => next: check how init is gated in the host app (remote flag / experiment /"
  echo "           consent / build variant), and check logcat for SDK errors:"
  echo "             \$ADB shell logcat -d | grep -iE 'datadog|DD_SDK'"
  echo
  echo "  Datadog-related logcat lines captured this run:"
  # Unfiltered, then narrowed by grep: `logcat --pid` takes a single PID, and the
  # package can own several processes -- filtering on the default one would hide the
  # very lines that explain why a private-process init failed.
  "$ADB" shell logcat -d 2>/dev/null | grep -iE 'datadog|DD_SDK' \
    | head -20 | sed 's/^/     /' || echo "     (none)"
  exit 1
fi
