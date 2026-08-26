#!/usr/bin/env bash
# Unless explicitly stated otherwise all files in this repository are licensed
# under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
# Cold-start A/B benchmark for measuring Datadog SDK cold-start impact.
#
# Improvements over the original ab.sh:
#   * asserts the installed APK is the one we think it is (md5 of the APK we
#     pushed vs. what the package manager reports)
#   * PROVES Datadog is live/dead in each arm before measuring (logcat probe)
#   * pins performance state: airplane mode, animations off, thermal snapshot
#   * records Displayed= (TTID) from logcat AND am start -W TotalTime
#   * captures reportFullyDrawn (TTFD) when the app emits it
#   * pre-registers outlier policy: first N runs discarded, nothing else dropped
#   * emits a tidy CSV with every raw sample + run index so nothing is hidden
#
# Usage:
#   ./coldstart_bench.sh <no-datadog.apk> <with-datadog.apk> [RUNS] [BLOCKS]
#
set -euo pipefail

PKG="${PKG:?set PKG to your application id, e.g. PKG=com.example.app}"
APK_A="${1:?usage: $0 <baseline.apk> <treatment.apk> [runs] [blocks]}"
APK_B="${2:?}"
RUNS="${3:-4}"      # per block
BLOCKS="${4:-8}"    # must be even (ABBA); >=3 needed for a paired CI
WARMUP="${WARMUP:-3}"   # pre-registered: discarded, never analyzed
# Play Store installs land on speed-profile. `speed` compiles everything AOT,
# which removes the class-load/verify cost the SDK contributes -- and overrides
# any Baseline Profile. Default to what real users get; `speed` is a documented
# lower-variance secondary.
COMPILE_FILTER="${COMPILE_FILTER:-speed-profile}"
AIRPLANE="${AIRPLANE:-0}"   # 1 = Wi-Fi/mobile radios off; 0 = at least one enabled
# Animation scales. 0 removes a large variance source, but it is NOT bias-free for
# an SDK that does per-frame work: fewer animated frames during the launch means
# fewer Choreographer callbacks for vitals / long-task tracking and fewer Session
# Replay snapshots, which understates those components. Default 0 for comparability
# with previous runs; set ANIMATIONS=1 to measure the per-frame cost honestly and
# quantify the bias.
ANIMATIONS="${ANIMATIONS:-0}"
# Optional: capture a duration the HOST APP logs itself, so its own startup metric can
# be A/B'd under this protocol instead of read by hand off a single launch. Give an ERE
# that matches the log line; the LAST number in the match is taken as milliseconds.
#   APP_TRACE_REGEX='cold_launch_new total duration: [0-9]+'
APP_TRACE_REGEX="${APP_TRACE_REGEX:-}"
# Arm expectations for the Datadog liveness gate: 0 = expect absent, 1 = expect
# active. Override for an A/A run (same APK both arms, EXPECT_B=0), which
# measures the noise floor / false-positive rate of this very protocol.
EXPECT_A="${EXPECT_A:-0}"
EXPECT_B="${EXPECT_B:-1}"
# `-`, not `:-`: an UNSET label takes the default, but an explicitly empty one
# must reach the charset check below rather than being silently replaced by it.
# With `:-`, `LABEL_A=` became `A_noDD` and the check's "" branch was dead code.
LABEL_A="${LABEL_A-A_noDD}"
LABEL_B="${LABEL_B-B_withDD}"
TS="$(date +%Y%m%d_%H%M%S)"
OUT="results_$TS.csv"
LOG="bench_$TS.log"

die() { echo "FATAL: $*" >&2; exit 1; }

# A typo here used to silently disable the SDK-liveness gate (it tests for exactly
# "0"/"1"), and an unvalidated PKG could target the wrong app for `adb uninstall`.
case "$EXPECT_A$EXPECT_B" in [01][01]) ;; *) die "EXPECT_A/EXPECT_B must each be 0 or 1 (got '$EXPECT_A'/'$EXPECT_B')" ;; esac
case "$ANIMATIONS" in 0|1) ;; *) die "ANIMATIONS must be 0 or 1 (got '$ANIMATIONS')" ;; esac
# Every branch below tests AIRPLANE for exactly "1", so AIRPLANE=true would take
# the radio-enabled branch while the CSV recorded `airplane=true` -- metadata that
# describes neither of the two supported radio scenarios.
case "$AIRPLANE" in 0|1) ;; *) die "AIRPLANE must be 0 or 1 (got '$AIRPLANE')" ;; esac
# Identical labels would make ab_stats.py compare an arm against itself: every block
# delta is 0 and the null looks convincing. An A/A run uses LABEL_A=A1 LABEL_B=A2.
[ "$LABEL_A" != "$LABEL_B" ] || die "LABEL_A and LABEL_B are both '$LABEL_A'. The arms
       must be distinguishable in the CSV -- for an A/A run use LABEL_A=A1 LABEL_B=A2."
# Labels are written into an unquoted CSV field and a whitespace-tokenized
# metadata header. Keep them to a portable identifier alphabet so punctuation
# cannot change either output format.
for _label_var in LABEL_A LABEL_B; do
  case "${!_label_var}" in
    ""|*[!a-zA-Z0-9._-]*) die "$_label_var must contain only letters, digits, '.', '_' or '-'
       (got '${!_label_var}'). Commas and whitespace corrupt the CSV or metadata header;
       other delimiters are reserved." ;;
  esac
done
case "$PKG" in *[!a-zA-Z0-9._]*|""|.*|*.) die "invalid application id: '$PKG'" ;; esac
case "$PKG" in *.*) ;; *) die "application id must be dotted, e.g. com.example.app (got '$PKG')" ;; esac
# RUNS/BLOCKS/WARMUP reach arithmetic loops unchecked otherwise, so a typo
# surfaces as a bash arithmetic error a hundred lines later instead of here.
for _v in RUNS BLOCKS WARMUP; do
  case "${!_v}" in ''|*[!0-9]*) die "$_v must be a non-negative integer (got '${!_v}')" ;; esac
done
[ $((BLOCKS % 2)) -eq 0 ] || die "BLOCKS must be even for ABBA counterbalancing (got $BLOCKS)"
[ "$BLOCKS" -ge 2 ] || die "BLOCKS must be >= 2 (got $BLOCKS)"
[ "$RUNS" -ge 1 ] || die "RUNS must be >= 1 (got $RUNS)"
[ -f "$APK_A" ] || die "baseline APK not found: $APK_A"
[ -f "$APK_B" ] || die "treatment APK not found: $APK_B"
# A malformed APP_TRACE_REGEX -- an unclosed bracket expression is the usual one --
# makes `grep -oE` exit 2 on EVERY launch, and the `|| true` on that scrape (which
# is load-bearing: the pattern legitimately does not match on most lines) turns each
# error into an empty value. The run then completes an hour later with
# app_trace_ms=NA on every single row and nothing anywhere saying why. Compile-test
# it here instead: grep exits 0 on match, 1 on no-match, >= 2 only on a bad pattern.
if [ -n "$APP_TRACE_REGEX" ]; then
  _re_rc=0
  _re_err=$(printf 'compile test\n' | grep -oE "$APP_TRACE_REGEX" 2>&1) || _re_rc=$?
  [ "$_re_rc" -le 1 ] || die "APP_TRACE_REGEX is not a valid POSIX ERE; grep rejected it
       (exit $_re_rc): ${_re_err:-no message}
       Pattern: $APP_TRACE_REGEX
       Every launch would scrape nothing and the whole run would record
       app_trace_ms=NA. Fix the pattern, or unset APP_TRACE_REGEX."
fi

# PKG with its dots escaped, for use inside the logcat EREs below. Unescaped,
# `com.example.app` is a pattern whose dots match any character, so a different
# package could have its Displayed/Fully-drawn line scraped as ours.
PKG_RE=$(printf '%s' "$PKG" | sed 's/[.]/\\./g')
# The host app's own Datadog log markers, scraped per launch further down. Defined
# once, in one place, because two consumers must never disagree about them: the
# pre-launch stale-buffer guard and the scrapes themselves. If a scrape gains a
# marker the guard does not know about, a denied `logcat -c` repopulates that field
# from an earlier launch while the guard still reports the buffer clean. They are
# plain fixed strings, so they are safe both as `grep` patterns and as branches of
# the guard's ERE alternation.
DD_MARKER_NATIVE_INIT="Datadog native initialized"
DD_MARKER_RN_INIT="Datadog RN initialized"
DD_MARKER_ENABLED="Datadog native enabled"
DD_MARKERS_RE="$DD_MARKER_NATIVE_INIT|$DD_MARKER_RN_INIT|$DD_MARKER_ENABLED"
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG" >&2; }

. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
# Reserve both evidence files before the first device command. Second-resolution
# timestamps are not unique under parallel multi-device runs, and a check followed
# by a normal redirection would still race; the helper uses atomic noclobber creates.
dd_reserve_output_files "$OUT" "$LOG" || die "benchmark output reservation failed"
# dd_md5_str is defined by lib.sh. Computing this before sourcing the helpers made
# the command-not-found failure fall through `|| echo none`, so every non-empty
# APP_TRACE_REGEX was stamped with the same identity and incompatible host metrics
# could be pooled.
APP_TRACE_ID=$([ -n "$APP_TRACE_REGEX" ] && dd_md5_str "$APP_TRACE_REGEX" || echo none)
# Resolved per install (a reinstall gets a new UID), but declared here so the
# `set -u` guard in measure() cannot depend on install_and_attest having run.
PKG_UID=""
dd_resolve_tools || exit 2
dd_require_device || exit 2
DD_ANDROID_USER=""
dd_resolve_android_user || exit 2
if [ -n "$APP_TRACE_REGEX" ]; then
  dd_logcat_supports_uid_filter || die "this device's logcat has no --uid filter.
       APP_TRACE_REGEX must be attributable to the installed package; a device-wide
       scrape can silently take another process's matching value. Use a device whose
       'logcat --help' exposes --uid, or unset APP_TRACE_REGEX."
fi

# Read package name + version out of each APK's manifest and refuse to run on a
# pair that is not what the protocol assumes.
#
# Two failures this catches, both destructive or invalidating:
#   * PKG naming a DIFFERENT app than the APKs. Every block runs `adb uninstall
#     $PKG`, so a wrong PKG wipes an unrelated app's data 16 times over.
#   * arms built from different app versions. The guide makes matching
#     versionCode/versionName a hard requirement -- it is what makes "the builds
#     differ only by the SDK" checkable rather than asserted -- and until now
#     nothing enforced it.
# aapt2 is REQUIRED for this: a missing one is a hard failure, overridable only
# with an explicit ALLOW_UNVERIFIED_PKG=1.
_apk_badging() {
  # `|| true` is load-bearing: under `set -o pipefail` an aapt2 that cannot parse
  # the file makes the pipeline non-zero, and `set -e` would then kill the run
  # silently at the assignment below -- turning an optional preflight into a
  # fatal one with no message at all. Empty output is the failure signal instead.
  "$AAPT2" dump badging "$1" 2>/dev/null | awk -F"'" '/^package: name=/{print $2"\t"$4"\t"$6; exit}' || true
}
check_apk_pair() {
  # MANDATORY, not advisory. This is the only thing standing between a typo in
  # PKG and `adb uninstall` irreversibly wiping an unrelated app's data 16 times
  # over. Skipping it because a tool is missing trades a fixable setup problem
  # for an unrecoverable one, so a missing/unusable aapt2 is a hard failure.
  local a b a_pkg b_pkg
  if [ -z "${AAPT2:-}" ]; then
    die "aapt2 not found, so the APK's package name cannot be verified against
       PKG='$PKG' -- and every block runs 'adb uninstall \$PKG'. Refusing to run.
       Fix: install Android SDK build-tools, or set AAPT2=/path/to/aapt2.
       Override only if you have checked by hand that the APKs declare '$PKG':
         aapt2 dump badging <apk> | grep '^package:'
       then re-run with ALLOW_UNVERIFIED_PKG=1."
  fi
  a=$(_apk_badging "$APK_A"); b=$(_apk_badging "$APK_B")
  if [ -z "$a" ] || [ -z "$b" ]; then
    die "aapt2 could not read the package name out of one of the APKs, so it
       cannot be checked against PKG='$PKG' before 'adb uninstall'. Refusing to
       run. Check both files are real APKs; override with ALLOW_UNVERIFIED_PKG=1."
  fi
  a_pkg=${a%%$'\t'*}; b_pkg=${b%%$'\t'*}
  log "baseline  APK: $(printf '%s' "$a" | tr '\t' ' ')"
  log "treatment APK: $(printf '%s' "$b" | tr '\t' ' ')"
  [ "$a_pkg" = "$PKG" ] && [ "$b_pkg" = "$PKG" ] || die \
    "PKG='$PKG' does not match the APKs (baseline='$a_pkg' treatment='$b_pkg').
       Every block runs 'adb uninstall \$PKG'. Refusing to run rather than wipe
       the wrong app's data. Set PKG to the application id the APKs declare."
  if [ "$a" != "$b" ] && [ "${ALLOW_VERSION_MISMATCH:-0}" != "1" ]; then
    die "the two APKs declare different versionCode/versionName.
       Then the SDK is not the only variable between the arms and the delta is
       not attributable to it. Rebuild both from the same commit, or set
       ALLOW_VERSION_MISMATCH=1 if you know why they differ."
  fi
}
if [ "${ALLOW_UNVERIFIED_PKG:-0}" = "1" ]; then
  log "WARNING: ALLOW_UNVERIFIED_PKG=1 -- the APK/package preflight is DISABLED."
  log "         'adb uninstall $PKG' will run against whatever app owns that id."
else
  check_apk_pair
fi

# Build identity of each arm, stamped into the CSV header below.
#
# Without it, two runs from SUCCESSIVE APK pairs on the same device with the same
# protocol and launcher agree on every field ab_stats.py compares, so it pools
# their block deltas into one interval without so much as a warning -- even though
# the baseline or treatment binary changed underneath. versionCode/versionName do
# not help: check_apk_pair requires the two ARMS to match on those, so a rebuild of
# both arms keeps them equal while the bits change completely.
APK_A_MD5=$(dd_md5 "$APK_A") || exit 2
APK_B_MD5=$(dd_md5 "$APK_B") || exit 2
log "baseline  md5: $APK_A_MD5"
log "treatment md5: $APK_B_MD5"

DEV_FP=$("$ADB" shell getprop ro.build.fingerprint | tr -d '\r')
DEV_MODEL=$("$ADB" shell getprop ro.product.model | tr -d '\r')
DEV_SDK=$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')
DEV_ABI=$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')
# Emulator detection. A custom AVD or a third-party emulator can have a fingerprint
# and model with none of the usual keywords, so the qemu properties are the fallback
# that matters -- and only `ro.boot.qemu` was read, despite the comment naming
# `ro.kernel.qemu`. Both exist depending on the image, so read both, plus ro.hardware
# (goldfish/ranchu). Getting this wrong stamps emulator=0 on emulator data and
# bypasses the analyzer's warning banner entirely.
IS_EMU=0
case "$DEV_FP$DEV_MODEL" in *generic*|*emulator*|*sdk_gphone*|*goldfish*|*ranchu*) IS_EMU=1 ;; esac
for _prop in ro.kernel.qemu ro.boot.qemu; do
  [ "$("$ADB" shell getprop "$_prop" 2>/dev/null | tr -d '\r')" = "1" ] && IS_EMU=1
done
case "$("$ADB" shell getprop ro.hardware 2>/dev/null | tr -d '\r')" in
  *goldfish*|*ranchu*) IS_EMU=1 ;;
esac
if [ "$IS_EMU" = 1 ]; then
  log "*** EMULATOR DETECTED — results are for harness validation and structural"
  log "*** attribution ONLY. Do not report these as your app's startup cost."
fi

# Measure the REAL user cold start: the launcher intent, exactly as an icon tap
# produces it. Launching a component directly with `am start -n <activity>`
# bypasses launcher routing / activity-alias selection, so it is not the path
# your field startup metric observes.
#
# Resolved AFTER each install, never once up front. `resolve-activity` asks the
# package manager about the INSTALLED build, so hoisting it above the first
# install made the harness unusable on a clean device (nothing to resolve, so it
# aborted before installing anything) and, worse, silently reused a component
# read off a leftover build when one happened to be installed.
ACT=""
resolve_launcher() {
  local arm="$1" act
  act=$("$ADB" shell cmd package resolve-activity --brief --user "$DD_ANDROID_USER" \
         -c android.intent.category.LAUNCHER "$PKG" \
         | tail -1 | tr -d '\r')
  # `resolve-activity --brief` prints the literal text "No activity found" (exit 0)
  # when nothing matches, so a bare non-empty test passes it straight through to
  # `am start -n "No activity found"`. Verified on a moto g(60)s / Android 12.
  # Check the SHAPE instead: it must be <pkg>/<component> for the app under test.
  case "$act" in
    "$PKG"/*) ;;
    *) die "[$arm] could not resolve a launcher activity for $PKG (got '${act:-nothing}').
       The app must declare an activity with category android.intent.category.LAUNCHER." ;;
  esac
  # Both arms must enter through the same component, or the two arms are not
  # running the same scenario and the delta is not attributable to the SDK.
  if [ -n "$ACT" ] && [ "$act" != "$ACT" ]; then
    die "[$arm] resolves launcher '$act' but an earlier arm resolved '$ACT'.
       The builds route their launcher differently (activity-alias?), so the arms
       do not measure the same entry path. Refusing to compare them."
  fi
  ACT="$act"
  START_ARGS=(-W --user "$DD_ANDROID_USER" -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER -n "$ACT")
  log ">>> [$arm] launcher activity: $ACT"
}

log "device: $DEV_MODEL (sdk $DEV_SDK) $DEV_FP"
log "android user: $DD_ANDROID_USER"
log "radio mode: $([ "$AIRPLANE" = 1 ] && echo WIFI/MOBILE OFF || echo ENABLED), reachability unverified"
log "output: $OUT"

# ---------------------------------------------------------------- device state
pin_device() {
  log "pinning device state"
  # AIRPLANE=1 removes Wi-Fi/mobile traffic as a variance sanity check; the default
  # enables Wi-Fi. Neither state proves end-to-end reachability, which the operator
  # must hold stable for the experiment.
  # Either way this changes the radios even if the operator had set them deliberately,
  # so they are snapshotted and put back like every other setting we touch.
  dd_apply_radio_state "$AIRPLANE" || exit 2
  dd_apply_animation_scales "$ANIMATIONS" || exit 2
  if [ "$ANIMATIONS" = 1 ]; then
    log "animations ENABLED (higher variance; includes per-frame SDK cost)"
  fi
  "$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  # Keep the screen awake for the duration. NOTE: this suppresses the lock screen,
  # which is why restore_device() puts it back on exit.
  "$ADB" shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1 || true
  "$ADB" shell settings put global stay_on_while_plugged_in 3 >/dev/null 2>&1 || true
  # Reduce competing work. Android exposes no getter for either of these, so they
  # cannot be snapshotted the way the `settings` values are. Record whether OUR
  # call succeeded instead, and undo only that -- otherwise a device that arrived
  # with fixed-performance mode already on, or bg-dexopt already disabled, would
  # be left in the opposite state by a run that never chose it.
  if "$ADB" shell cmd power set-fixed-performance-mode-enabled true >/dev/null 2>&1; then
    _WE_SET_PERF=1
  fi
  if "$ADB" shell cmd package bg-dexopt-job --disable >/dev/null 2>&1; then
    _WE_SET_DEXOPT=1
  fi
}


# Capture the settings we are about to change so they can be put back. Without
# this the harness permanently leaves the device with no lock screen, animations
# disabled and every dangerous permission granted.
#
# All three animation scales are read, not just window_animation_scale: they are
# independent settings and restoring all three from the window one silently
# rewrites the other two on any device where they differed.
_ORIG_STAY=""; _ORIG_TIMEOUT=""; _ORIG_WIFI=""; _ORIG_DATA=""
_WE_SET_PERF=0; _WE_SET_DEXOPT=0
_ORIG_ANIM_window_animation_scale=""
_ORIG_ANIM_transition_animation_scale=""
_ORIG_ANIM_animator_duration_scale=""
snapshot_device() {
  local s v
  _ORIG_STAY=$(dd_snapshot_numeric_setting global stay_on_while_plugged_in) || return 1
  _ORIG_TIMEOUT=$(dd_snapshot_numeric_setting system screen_off_timeout) || return 1
  _ORIG_WIFI=$(dd_snapshot_radio_setting global wifi_on) || return 1
  _ORIG_DATA=$(dd_snapshot_radio_setting global mobile_data) || return 1
  # Empty is reachable only through ALLOW_UNVERIFIED_RADIOS, which the snapshot
  # helper enforces; a value that is numeric but neither 0 nor 1 is not a radio
  # state this harness knows how to restore.
  for v in "$_ORIG_WIFI" "$_ORIG_DATA"; do
    case "$v" in
      0|1|'') ;;
      *) echo "FATAL: radio snapshot must read 0 or 1 (got '$v')." >&2; return 1 ;;
    esac
  done
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do
    v=$(dd_snapshot_numeric_setting global "$s") || return 1
    printf -v "_ORIG_ANIM_$s" '%s' "$v"
  done
  log "device state snapshotted (stay_on=$_ORIG_STAY timeout=$_ORIG_TIMEOUT" \
      "anim=$_ORIG_ANIM_window_animation_scale wifi=$_ORIG_WIFI data=$_ORIG_DATA)"
}

restore_device() {
  local rc=$? s var orig
  echo "[$(date +%H:%M:%S)] restoring device state" >&2
  "$ADB" shell settings put global stay_on_while_plugged_in "$_ORIG_STAY" >/dev/null 2>&1 || true
  "$ADB" shell settings put system screen_off_timeout "$_ORIG_TIMEOUT" >/dev/null 2>&1 || true
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do
    var="_ORIG_ANIM_$s"; orig="${!var}"
    "$ADB" shell settings put global "$s" "$orig" >/dev/null 2>&1 || true
  done
  # Undo only what this run actually turned on (see pin_device). Still imperfect:
  # with no getter we cannot tell "we enabled it" from "it was already enabled and
  # our call was a no-op", so this is documented as best-effort, not a guarantee.
  [ "${_WE_SET_PERF:-0}" = 1 ] && { "$ADB" shell cmd power set-fixed-performance-mode-enabled false >/dev/null 2>&1 || true; }
  [ "${_WE_SET_DEXOPT:-0}" = 1 ] && { "$ADB" shell cmd package bg-dexopt-job --enable >/dev/null 2>&1 || true; }
  # Radios: pin_device changes these in BOTH modes (it enables Wi-Fi in the
  # default radio-enabled mode), so both are restored from the validated snapshot
  # rather than unconditionally switched back on.
  case "$_ORIG_WIFI" in 0) "$ADB" shell svc wifi disable >/dev/null 2>&1 || true ;;
                        1) "$ADB" shell svc wifi enable  >/dev/null 2>&1 || true ;; esac
  case "$_ORIG_DATA" in 0) "$ADB" shell svc data disable >/dev/null 2>&1 || true ;;
                        1) "$ADB" shell svc data enable  >/dev/null 2>&1 || true ;; esac
  # Hand back exactly the permissions we force-granted. NOT a device-wide
  # `pm reset-permissions`: that also revokes every other app's grants, which is
  # not ours to do on a borrowed or personal device.
  for s in ${_GRANTED:-}; do
    "$ADB" shell pm revoke --user "$DD_ANDROID_USER" "$PKG" "$s" >/dev/null 2>&1 || true
  done
  # Stamp an aborted run so the CSV cannot be mistaken for a complete one. It is a
  # `#` line, so ab_stats.py surfaces it with the rest of the metadata.
  if [ "$rc" -ne 0 ] && [ -s "$OUT" ]; then
    echo "# RUN ABORTED (exit $rc) -- this CSV is a partial run, not a completed one" >> "$OUT"
  fi
  echo "[$(date +%H:%M:%S)] device restored. The app remains installed; 'adb uninstall $PKG' to remove." >&2
  return $rc
}
thermal_snapshot() {
  { "$ADB" shell dumpsys thermalservice 2>/dev/null \
      | grep -iE "Temperature\{|mStatus" | head -6 | tr -d '\r' | tr '\n' ' '; } || true
}

# ------------------------------------------------------- install + attestation
install_and_attest() {
  local apk="$1" arm="$2"
  local host_md5 expect_md5=""
  host_md5=$(dd_md5 "$apk")
  # The header stamps the digests read at preflight. A file rebuilt in place while
  # the benchmark is running would leave every later block labeled with the wrong
  # binary, which is exactly the confusion the header exists to prevent.
  case "$apk" in
    "$APK_A") expect_md5="$APK_A_MD5" ;;
    "$APK_B") expect_md5="$APK_B_MD5" ;;
  esac
  if [ -n "$expect_md5" ] && [ "$host_md5" != "$expect_md5" ]; then
    die "[$arm] $apk changed on disk mid-run (preflight md5 $expect_md5, now
       $host_md5). The CSV header records the preflight digest, so the remaining
       blocks would be stamped with a build identity that is not theirs."
  fi
  log ">>> [$arm] installing $(basename "$apk") (md5 $host_md5)"
  dd_ensure_uninstalled "$PKG" || die "[$arm] uninstall did not establish a clean install state"
  "$ADB" install --user "$DD_ANDROID_USER" -r "$apk" >/dev/null || die "install failed for $apk"

  # attest: pull the installed APK back and compare digests
  local remote_path dev_md5
  remote_path=$(dd_package_path "$PKG" | head -1 | sed 's/package://') \
    || die "[$arm] cannot read the installed APK path"
  dev_md5=$("$ADB" shell md5sum "$remote_path" | awk '{print $1}' | tr -d '\r')
  if [ "$host_md5" != "$dev_md5" ]; then
    die "[$arm] APK attestation FAILED: host=$host_md5 device=$dev_md5"
  fi
  log ">>> [$arm] APK attested OK ($remote_path)"

  if [ -n "$APP_TRACE_REGEX" ]; then
    PKG_UID=$(dd_unique_pkg_uid "$PKG") || die "[$arm] cannot scope APP_TRACE_REGEX to $PKG"
    log ">>> [$arm] app-owned log metrics scoped to package UID $PKG_UID"
  fi

  log ">>> [$arm] AOT compile (-m $COMPILE_FILTER)"
  "$ADB" shell cmd package compile -m "$COMPILE_FILTER" -f "$PKG" >/dev/null \
    || die "[$arm] 'cmd package compile -m $COMPILE_FILTER' failed"
  # Report what the compile ACTUALLY achieved. `speed-profile` compiles only what is in
  # the app's profile, and a freshly installed app has none -- so on a fresh install it
  # lands at `status=verify`, i.e. NO AOT code at all, and every launch JITs the startup
  # path. Silently assuming otherwise is how a run gets described as "AOT compiled" when
  # it is not. Logged per arm into bench_<ts>.log; the CSV header is written before the
  # first install, so it carries the requested COMPILE_FILTER, not the achieved status.
  # index(), not a regex match: "[com.example.app]" as an ERE is a bracket expression and
  # matches single characters, so `$0 ~ pkg` silently never fires.
  DEXOPT_STATUS=$("$ADB" shell dumpsys package dexopt 2>/dev/null \
    | awk -v pkg="[$PKG]" 'index($0,pkg){found=1; next} found && /status=/{print; exit}' \
    | grep -oE 'status=[a-z-]+' | head -1 | tr -d '\r') || true
  log ">>> [$arm] dexopt after compile: ${DEXOPT_STATUS:-unknown}"
  # `run-from-apk` means the same thing as `verify` for our purposes -- no AOT code --
  # and it is what an emulator reports where a device reports `verify`. Warning on only
  # one of them let the other pass as though the app had been compiled.
  case "${DEXOPT_STATUS:-}" in
    status=verify|status=run-from-apk)
      log ">>> [$arm] NOTE: no AOT code (no profile to compile against on a fresh install)."
      log ">>> [$arm]       The startup path is JIT-compiled on every launch. This is a"
      log ">>> [$arm]       no-profile condition -- pessimistic vs a Play install, which"
      log ">>> [$arm]       ships a cloud profile. COMPILE_FILTER=speed forces full AOT." ;;
  esac
  grant_runtime_permissions "$arm"
  resolve_launcher "$arm"
}

# Pre-grant every runtime permission the app declares.
#
# WHY THIS IS ESSENTIAL: apps that request runtime permissions on first launch
# show the GrantPermissionsActivity dialog on top of the app. Unattended, nothing
# dismisses it, so it reappears every launch and instances accumulate (we have
# observed 23 stacked, with the dialog — not the app — as the resumed activity).
# That adds a system activity launch to every sample and drifts monotonically
# across a run. Pre-granting removes the prompt and measures the "permissions
# already decided" path, which is what returning users, and therefore your field
# startup metrics, actually reflect.
#
# Derived from the package manager rather than hardcoded, so it tracks any build.
#
# The grants are recorded in $_GRANTED so restore_device can revoke exactly
# them. Reset per call, not accumulated: each call follows a fresh install that
# already dropped the previous arm's grants, so only the last arm's survive to
# the end of the run.
_GRANTED=""
_PERMISSION_STATE_ID=""
_PERMISSION_EFFECTIVE=""
_PERMISSION_DENIED=""
grant_runtime_permissions() {
  local arm="$1"
  _GRANTED=""
  if ! dd_grant_runtime_permissions "$PKG"; then
    # Preserve successful grants for restore_device even on a partial failure.
    _GRANTED="$DD_GRANTED_PERMISSIONS"
    die "[$arm] runtime-permission setup was incomplete; refusing a different benchmark scenario"
  fi
  _GRANTED="$DD_GRANTED_PERMISSIONS"
  _PERMISSION_STATE_ID="$DD_PERMISSION_STATE_ID"
  _PERMISSION_EFFECTIVE="$DD_EFFECTIVE_GRANTED_PERMISSIONS"
  _PERMISSION_DENIED="$DD_UNGRANTED_PERMISSIONS"
  log ">>> [$arm] pre-granted $DD_GRANTED_PERMISSION_COUNT/$DD_RUNTIME_PERMISSION_COUNT runtime permissions"
  log ">>> [$arm] effective permissions: granted=[${_PERMISSION_EFFECTIVE:-none}] denied=[${_PERMISSION_DENIED:-none}] id=$_PERMISSION_STATE_ID"
  return 0
}

# Record the effective result separately for each binary. The two arms may
# legitimately declare different permissions, but a given arm must keep the same
# state across fresh installs and across every CSV that enters one pooled result.
_PERMISSION_A_ID=""
_PERMISSION_B_ID=""
record_permission_state() {
  local arm_key="$1" arm="$2" previous=""
  [ -n "$_PERMISSION_STATE_ID" ] \
    || die "[$arm] runtime-permission setup produced no state identity"
  case "$arm_key" in
    A)
      previous="$_PERMISSION_A_ID"
      if [ -z "$previous" ]; then
        _PERMISSION_A_ID="$_PERMISSION_STATE_ID"
        echo "# permission_a=$_PERMISSION_STATE_ID" >> "$OUT"
      fi ;;
    B)
      previous="$_PERMISSION_B_ID"
      if [ -z "$previous" ]; then
        _PERMISSION_B_ID="$_PERMISSION_STATE_ID"
        echo "# permission_b=$_PERMISSION_STATE_ID" >> "$OUT"
      fi ;;
    *) die "internal error: unknown arm key '$arm_key'" ;;
  esac
  [ -z "$previous" ] || [ "$previous" = "$_PERMISSION_STATE_ID" ] \
    || die "[$arm] effective runtime-permission state changed within this run
       ($previous -> $_PERMISSION_STATE_ID). The app can take a different startup
       path under a different grant set; do not mix those cells in one estimate."
}

# --------------------------------------------- prove Datadog live/dead per arm
# The Datadog Android SDK always creates a `datadog-*` thread during
# Datadog.initialize() (CoreFeature.setupExecutors + immediate NTP task submit).
# Reading /proc/<pid>/task/*/comm is the cheapest reliable probe.
probe_datadog() {
  local arm="$1" blk="${2:-0}" pos="${3:-0}"
  "$ADB" shell am force-stop --user "$DD_ANDROID_USER" "$PKG"; sleep 3
  # This IS a real application launch and it precedes every warm-up and measured
  # launch in the cell. It used to happen without leaving a CSV row, which made
  # "every launch is in the CSV" untrue and made WARMUP=0 not a first launch.
  # It is now recorded as phase=probe. ab_stats.py analyzes phase=measure only.
  local pout launch_rc=0 row_phase=probe probe_fg=NA
  local probe_post_clear probe_stale probe_log probe_ours probe_intruder probe_top
  "$ADB" shell logcat -c >/dev/null 2>&1 || true
  probe_post_clear=$("$ADB" shell logcat -d 2>/dev/null | tr -d '\r') || true
  probe_stale=$(printf '%s\n' "$probe_post_clear" \
    | grep -cE "ActivityTaskManager: Displayed [a-zA-Z0-9_.]+/" || true)
  # Every package, not just $PKG: an empty buffer is what makes a foreign draw seen
  # later provably part of this launch. The cause is not knowable from here -- a
  # denied clear and an unrelated activity drawing in the same instant look the
  # same -- so report the observation and name both, as capture_trace.sh does.
  [ "${probe_stale:-0}" -eq 0 ] || die "[$arm] liveness probe: $probe_stale
       ActivityTaskManager Displayed marker(s) were in the buffer immediately after
       'logcat -c'. Either clearing logcat is denied on this device, or something
       drew in that instant; without an empty buffer a later foreign draw cannot be
       attributed to this launch. Check 'adb shell logcat -c' first."
  pout=$("$ADB" shell am start -W "${START_ARGS[@]}" 2>/dev/null | tr -d '\r') \
    || launch_rc=$?
  dd_validate_cold_launch_output "$pout" || true
  if [ "$launch_rc" -ne 0 ]; then
    DD_LAUNCH_ERROR="am start -W failed (exit $launch_rc)"
  fi
  if [ -n "$DD_LAUNCH_ERROR" ]; then
    row_phase=probe_rejected
    echo "$arm,$blk,$pos,$row_phase,1,${DD_LAUNCH_TOTAL:-NA},${DD_LAUNCH_STATE:-NA},${DD_LAUNCH_STATUS:-NA},NA,NA,NA,NA,NA,NA,NA,NA" >> "$OUT"
    die "[$arm] liveness probe launch: $DD_LAUNCH_ERROR"
  fi
  sleep 8
  probe_log=$("$ADB" shell logcat -d 2>/dev/null | tr -d '\r') || true
  probe_ours=$(printf '%s\n' "$probe_log" \
    | grep -cE "ActivityTaskManager: Displayed $PKG_RE/" || true)
  probe_intruder=$(printf '%s\n' "$probe_log" \
    | dd_first_foreign_displayed_activity "$PKG") || true
  probe_top=$(dd_top_activity)
  case "$probe_top" in "$PKG"/*) probe_fg=ok ;; *) probe_fg=OTHER ;; esac
  [ -z "$probe_intruder" ] || probe_fg=OTHER_MID
  if [ "$probe_ours" -eq 0 ] && [ "$probe_fg" = ok ]; then
    if [ "${ALLOW_NO_DISPLAYED_MARKER:-0}" = 1 ]; then
      log ">>> [$arm] liveness probe: no 'Displayed $PKG/...' line; foreground=NA"
      probe_fg=NA
    else
      probe_fg=NO_MARKER
    fi
  fi
  case "$probe_fg" in
    ok|NA) ;;
    OTHER_MID)
      DD_LAUNCH_ERROR="foreign activity reached first draw: $probe_intruder" ;;
    NO_MARKER)
      DD_LAUNCH_ERROR="no ActivityTaskManager Displayed marker for $PKG" ;;
    *)
      DD_LAUNCH_ERROR="app is not the foreground activity (found '${probe_top:-nothing}')" ;;
  esac
  [ -z "$DD_LAUNCH_ERROR" ] || row_phase=probe_rejected
  echo "$arm,$blk,$pos,$row_phase,1,${DD_LAUNCH_TOTAL:-NA},${DD_LAUNCH_STATE:-NA},${DD_LAUNCH_STATUS:-NA},$probe_fg,NA,NA,NA,NA,NA,NA,NA" >> "$OUT"
  [ -z "$DD_LAUNCH_ERROR" ] || die "[$arm] liveness probe launch: $DD_LAUNCH_ERROR"
  # Every process the package owns. An exact-name `pidof "$PKG"` misses a private
  # process, so on an app that initializes Datadog in `<pkg>:startup` this gate
  # either declared the app dead or inspected the default process and rejected a
  # treatment build whose SDK was live.
  local pids names dd
  pids=$(dd_pkg_pids "$PKG")
  [ -n "$pids" ] || die "[$arm] app did not start"
  names=""
  for _p in $pids; do
    names="$names$("$ADB" shell "cat /proc/$_p/task/*/comm 2>/dev/null" | tr -d '\r')
"
  done
  names=$(printf '%s' "$names" | grep . | sort -u) || true
  dd=$(printf '%s\n' "$names" | grep -c '^datadog-' || true)
  log ">>> [$arm] processes=$(printf '%s' "$pids" | tr '\n' ' ') threads=$(printf '%s\n' "$names" | grep -c . || true)  datadog-*=$dd"
  echo "$names" | grep '^datadog-' | sed 's/^/        /' | tee -a "$LOG" >&2 || true
  echo "$dd"
}

# ------------------------------------------------------------------- measuring
measure() {
  local arm="$1" blk="$2" phase="$3" n="$4" pos="${5:-0}"
  local _post_clear _post_clear_app="" _stale _stale_app
  for ((i=1; i<=n; i++)); do
    "$ADB" shell am force-stop --user "$DD_ANDROID_USER" "$PKG" >/dev/null 2>&1 || true
    # Let the launcher/force-stop transition finish before establishing the
    # guarded logcat window. A launcher draw during this settle is pre-launch;
    # after the clear below, every foreign Displayed line is conservatively
    # contamination, including one before the app reaches its own first frame.
    sleep 5
    "$ADB" shell logcat -c >/dev/null 2>&1 || true
    # PROVE the buffer is clear of this package's previous launch markers. `logcat -c`
    # can be denied while `logcat -d` stays readable; the scrapes below take `head -1`,
    # so a surviving line from an earlier launch would be recorded against THIS row and
    # ab_stats.py would happily use it as the endpoint. Checking for the specific
    # markers, rather than an empty buffer, tolerates ordinary system chatter.
    # Include every fixed pattern scraped later, not just the Android endpoints:
    # stale Datadog host markers would otherwise populate dd_enabled/init durations
    # from a previous launch even though logcat clearing failed.
    _post_clear=$("$ADB" shell logcat -d 2>/dev/null | tr -d '\r') || true
    _stale=$(printf '%s\n' "$_post_clear" \
             | grep -cE "Displayed $PKG_RE/|Fully drawn $PKG_RE/|$DD_MARKERS_RE" || true)
    [ "${_stale:-0}" -eq 0 ] || die "[$arm] launch $i: 'logcat -c' left $_stale previous
       scraped marker(s) in the buffer. The timing and Datadog host-marker scrapes take
       the first match, so this row would be attributed data from an earlier launch.
       Clearing logcat is likely denied on this device; fix that before measuring."
    _stale_app=0
    if [ -n "$APP_TRACE_REGEX" ]; then
      _post_clear_app=$("$ADB" shell logcat -d --uid="$PKG_UID" 2>/dev/null | tr -d '\r') || true
      # Do not use grep -q here. With pipefail and a large buffer, grep exits on an
      # early match, printf gets SIGPIPE, and the pipeline returns 141 -- turning a
      # real stale match into the false "clean" branch. grep -c consumes the input.
      _stale_app=$(printf '%s\n' "$_post_clear_app" | grep -cE "$APP_TRACE_REGEX" || true)
    fi
    [ "$_stale_app" -eq 0 ] || die "[$arm] launch $i: 'logcat -c' left a previous
       APP_TRACE_REGEX match in the buffer. The app_trace_ms scrape takes the first
       match, so this row would be attributed a stale host-app duration. Clearing logcat
       is likely denied on this device; fix that before measuring."
    local out total displayed lg app_lg="" dd_nat dd_rn dd_on state status ttfd fg
    local launch_rc=0 launch_error=""
    out=$("$ADB" shell am start "${START_ARGS[@]}" 2>/dev/null | tr -d '\r') \
      || launch_rc=$?
    dd_validate_cold_launch_output "$out" || true
    total="$DD_LAUNCH_TOTAL"
    state="$DD_LAUNCH_STATE"
    status="$DD_LAUNCH_STATUS"
    launch_error="$DD_LAUNCH_ERROR"
    [ "$launch_rc" -eq 0 ] \
      || launch_error="am start -W failed (exit $launch_rc)"
    sleep 6
    if [ -n "$APP_TRACE_REGEX" ]; then
      # Read the package-scoped view first, at the collection cutoff. Device-side
      # UID filtering preserves logcat's default `threadtime` format, exactly what
      # the trace watcher passes to the same regex. Adding a UID format column here
      # would make an anchored pattern match one path but not the other.
      app_lg=$("$ADB" shell logcat -d --uid="$PKG_UID" 2>/dev/null | tr -d '\r') || true
    fi
    lg=$("$ADB" shell logcat -d 2>/dev/null | tr -d '\r') || true
    # The preflight proves `--uid` EXISTS; it cannot prove this shell may read
    # another UID's logs. Android's own help says the filter "is only useful for
    # the 'root', 'log', and 'system' users" -- where it is not permitted the
    # filtered read comes back EMPTY, every row records app_trace_ms=NA, and an
    # hour of measuring is wasted on a metric that was never readable. An empty
    # package view while the device-wide buffer has content is that failure, not
    # an app that did not log its marker.
    if [ -n "$APP_TRACE_REGEX" ] && [ -z "$app_lg" ] && [ -n "$lg" ]; then
      die "[$arm] launch $i: 'logcat -d --uid=$PKG_UID' returned nothing while the
       device-wide buffer has content. This shell cannot read the package's logs by
       UID, so APP_TRACE_REGEX is not attributable and app_trace_ms would be NA on
       every row. Use a device that permits it, or unset APP_TRACE_REGEX."
    fi
    # NOTE: every extraction below MUST tolerate "no match". These strings are
    # absent in the baseline build, and under `set -euo pipefail` an unguarded
    # failing grep inside a command substitution aborts the whole run.
    # Anchored on the AOSP format on purpose: some vendors (e.g. Motorola) log their
    # own "MotoDisplayed <pkg>..." line FIRST, and an unanchored grep -m1 picks that
    # one and then finds no "+NNNms", silently yielding NA on every row.
    displayed=$(printf '%s\n' "$lg" \
                | grep -oE "ActivityTaskManager: Displayed $PKG_RE/[^:]*: \+[0-9smh]+" \
                | head -1 | grep -oE '\+[0-9smh]+$') || true
    # TTFD, if the app calls reportFullyDrawn(). Absent for most apps.
    ttfd=$(printf '%s\n' "$lg" \
                | grep -oE "Fully drawn $PKG_RE/[^:]*: \+[0-9smh]+" \
                | head -1 | grep -oE '\+[0-9smh]+$') || true
    # The permission/ANR dialog contamination this guards against ACCUMULATES, so
    # sampling it once per arm cannot see it. Record per launch instead.
    #
    # dd_top_activity is a single snapshot taken at the END of the 6 s collection
    # window, so an activity that took over and handed back INSIDE the window -- the
    # permission-dialog case -- leaves it reading `ok` while the app was paused for
    # part of the measurement. The logcat buffer already pulled above records every
    # activity that reached first draw, in order, so the intrusion is recoverable
    # from data in hand: no polling loop, no extra adb round-trip, and therefore no
    # added perturbation of the thing being measured.
    #
    # The post-settle logcat clear above is the boundary. Do not anchor the scan on
    # OUR first Displayed line: a permission/system activity can take over after
    # launch but before our first frame, then hand back before dd_top_activity runs.
    # Ignoring that prefix would accept the contaminated launch.
    local intruder="" ours=0
    intruder=$(printf '%s\n' "$lg" | dd_first_foreign_displayed_activity "$PKG") || true
    # grep -q under pipefail can false-negative on a large log buffer: it exits at
    # the first match, printf receives SIGPIPE, and the pipeline status becomes 141.
    # Consume the buffer so a present anchor cannot be reported as absent.
    ours=$(printf '%s\n' "$lg" \
           | grep -cE "ActivityTaskManager: Displayed $PKG_RE/" || true)
    case "$(dd_top_activity)" in "$PKG"/*) fg=ok ;; *) fg=OTHER ;; esac
    [ -z "$intruder" ] || fg=OTHER_MID
    # No anchor line means the mid-window check DID NOT RUN -- it is not evidence of
    # a clean window. The foreign-activity scan still runs without our marker, but
    # absence means it cannot prove that the expected app itself reached first draw.
    if [ "$ours" -eq 0 ] && [ "$fg" = ok ]; then
      if [ "${ALLOW_NO_DISPLAYED_MARKER:-0}" = 1 ]; then
        log ">>> [$arm] launch $i: no 'Displayed $PKG/...' line; mid-window foreground"
        log "    check could not run. Recorded foreground=NA (ALLOW_NO_DISPLAYED_MARKER=1)."
        fg=NA
      else
        fg=NO_MARKER
      fi
    fi
    # Optional: if the host app logs its own init timing, capture it. Harmless when absent.
    dd_nat=$(printf '%s\n' "$lg" | grep -m1 "$DD_MARKER_NATIVE_INIT" \
                | grep -oE 'duration: [0-9]+' | grep -oE '[0-9]+$') || true
    dd_rn=$(printf '%s\n' "$lg" | grep -m1 "$DD_MARKER_RN_INIT" \
                | grep -oE 'duration: [0-9]+' | grep -oE '[0-9]+$') || true
    dd_on=$(printf '%s\n' "$lg" | grep -m1 "$DD_MARKER_ENABLED" \
                | grep -oE '(true|false)' | head -1) || true
    # Liveness on THE PROCESS JUST MEASURED, not on the probe launch. `dd_on` is the
    # HOST APP's marker: most apps never emit it, so that gate passes vacuously and
    # the treatment arm can measure launches where the SDK never initialized. The
    # thread oracle needs no cooperation from the app. Sampled here, after the whole
    # collection window has already elapsed, so it cannot perturb the timing.
    local dd_thr=NA _pids
    _pids=$(dd_pkg_pids "$PKG")
    [ -z "$_pids" ] || dd_thr=$(dd_datadog_threads "$_pids")
    # Host-app metric, if one was requested. Absent is not fatal: it lands as NA and
    # ab_stats.py counts it in the missing-value warning rather than silently dropping it.
    local app_tr=""
    if [ -n "$APP_TRACE_REGEX" ]; then
      app_tr=$(printf '%s\n' "$app_lg" | grep -m1 -oE "$APP_TRACE_REGEX" \
                 | grep -oE '[0-9]+' | tail -1) || true
    fi
    # Decide validity BEFORE the row is written. Both warm-ups and measurements
    # define the cell's post-install JIT/profile ramp, so a failed discarded launch
    # is not interchangeable with a successful one: measuring after it would stamp
    # WARMUP=N while only N-1 valid launches prepared the app. A bad launch is still
    # recorded (nothing is hidden), but under a rejected phase the analyzer excludes
    # by construction before the run aborts.
    local row_phase="$phase" reject=""
    if [ "$phase" = warmup ] || [ "$phase" = measure ]; then
      if [ "$launch_rc" -ne 0 ]; then
        reject="$launch_error"
      elif [ "${status:-}" != "ok" ]; then
        reject="Status='${status:-none}', not ok"
      elif [ "${state:-}" != COLD ]; then
        # Strict: an empty LaunchState used to be accepted here, which let a launch
        # that reported nothing at all pass as cold.
        reject="LaunchState='${state:-none}', not COLD.
       If TotalTime was also empty, the device is almost certainly locked or the
       notification shade is on top: the activity resumes but never draws, so
       'am start -W' reports nothing. Unlock the phone and re-run.
       Otherwise the process was not fully reaped; increase the force-stop settle time."
      elif [ -n "$launch_error" ]; then
        # At this point Status and LaunchState passed, so the shared parser's only
        # remaining failure is a missing or non-numeric TotalTime.
        reject="$launch_error"
      elif [ "$fg" = OTHER_MID ]; then
        reject="another activity took the foreground DURING the collection window:
       '$intruder' reached first draw after the post-settle logcat boundary. It may
       have appeared before or after $PKG's first frame, and had handed the foreground
       back before the end-of-window check -- so that check saw nothing.
       Most often a runtime permission dialog; pre-granting is supposed to prevent
       it (see grant_runtime_permissions)."
      elif [ "$fg" = NO_MARKER ]; then
        reject="no 'ActivityTaskManager: Displayed $PKG/...' line in the buffer, so the
       mid-window foreground check could not run and this row carries no evidence that
       the app owned the screen for the whole measured window. The same line is the
       source of the displayed/ttfd metrics, so they are NA here too -- this device's
       log format is not the one the scrapes assume. Fix that, or accept the weaker
       guarantee explicitly with ALLOW_NO_DISPLAYED_MARKER=1 (rows then carry
       foreground=NA and ab_stats.py warns about them)."
      elif [ "$fg" != ok ] && [ "$fg" != NA ]; then
        reject="ended with '$fg' in the foreground, not $PKG (dialog/crash/ANR?)"
      elif [ "${dd_thr:-NA}" = NA ]; then
        reject="the package owned no process at the end of the collection window, so SDK
       liveness could not be rechecked on the MEASURED process (crash during startup?)."
      elif [ "${expect_dd:-}" = 1 ] && [ "${dd_thr:-0}" -eq 0 ]; then
        reject="no datadog-* thread in the measured process. probe_datadog proved the SDK
       live once, before this cell's warm-ups; this launch did not initialize it. Init
       that is first-launch-only, consent-gated or remote-config-gated behaves exactly
       like this, and every such row measures an SDK-ABSENT launch as treatment."
      elif [ "${expect_dd:-}" = 0 ] && [ "${dd_thr:-0}" -ne 0 ]; then
        reject="$dd_thr datadog-* thread(s) in the measured process of the BASELINE arm.
       The baseline must not link a live SDK -- this row would cancel the effect it is
       supposed to isolate."
      else
        # The app's own liveness marker, compared against the arm's expectation.
        # Absent marker (most apps) is not a failure; a CONTRADICTING marker is.
        case "${expect_dd:-}${dd_on:-}" in
          0true)  reject="app reports Datadog ENABLED in the baseline arm" ;;
          1false) reject="app reports Datadog DISABLED in the treatment arm" ;;
        esac
      fi
      [ -z "$reject" ] || row_phase="${phase}_rejected"
    fi
    echo "$arm,$blk,$pos,$row_phase,$i,${total:-NA},${state:-NA},${status:-NA},${fg:-NA},${displayed:-NA},${ttfd:-NA},${app_tr:-NA},${dd_on:-NA},${dd_thr:-NA},${dd_nat:-NA},${dd_rn:-NA}" | tee -a "$OUT"
    [ -z "$reject" ] || die "[$arm] $phase launch $i $reject"
    sleep 4
  done
}

# ------------------------------------------------------------------------ main
snapshot_device
# Restore on EXIT only, and make INT/TERM *exit* rather than run the handler
# inline. `trap restore_device INT` returns to the interrupted loop, so Ctrl-C
# used to restore the device and then carry on benchmarking against an unpinned
# device -- every launch after the interrupt silently measured a different
# machine, and the handler ran twice. Exiting routes through the EXIT trap once.
trap restore_device EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
pin_device
# pin_device wakes the screen and tries to dismiss a swipe-only keyguard; this
# catches the secure-lock case, which adb cannot dismiss.
dd_require_unlocked || exit 2
log "thermal before: $(thermal_snapshot)"
# The header names the launcher component, which is only known after the first
# install resolves it -- so it is written once, immediately before the first row.
_HEADER_WRITTEN=0
write_header_once() {
  if [ "$_HEADER_WRITTEN" = 0 ]; then
    # label_a/label_b make the label -> arm mapping part of the run's identity.
    # baseline_md5/treatment_md5 alone do not: swapping LABEL_A and LABEL_B between
    # two runs of the SAME apk pair leaves both digests equal while every row's label
    # now names the other binary, so ab_stats.py would pool deltas of opposite sign.
    # app_trace_id is the md5 of APP_TRACE_REGEX ("none" when unset) -- the regex
    # itself cannot go in a whitespace-split header, and two files whose regexes
    # differ hold app_trace_ms values from different app events entirely.
    echo "# device=$DEV_MODEL sdk=$DEV_SDK abi=$DEV_ABI emulator=$IS_EMU android_user=$DD_ANDROID_USER compile_filter=$COMPILE_FILTER blocks=$BLOCKS runs=$RUNS warmup=$WARMUP animations=$ANIMATIONS fp=$DEV_FP launcher=$ACT airplane=$AIRPLANE baseline_md5=$APK_A_MD5 treatment_md5=$APK_B_MD5 label_a=$LABEL_A label_b=$LABEL_B expect_a=$EXPECT_A expect_b=$EXPECT_B app_trace_id=$APP_TRACE_ID" >> "$OUT"
    echo "label,block,pos_in_block,phase,run,total_ms,launch_state,status,foreground,displayed,ttfd,app_trace_ms,dd_enabled,dd_threads,dd_native_init_ms,dd_rn_init_ms" >> "$OUT"
    _HEADER_WRITTEN=1
  fi
}

for ((b=1; b<=BLOCKS; b++)); do
  # COUNTERBALANCE the arm order (ABBA). Running A-then-B in every block turns
  # any monotonic drift across the session into a systematic bias favoring A.
  # An A/A validation run with fixed A-then-B order produced a spurious
  # "significant" +12.7 ms (CI [+3.0,+22.5], p=0.011) on identical APKs, entirely
  # from block-1 ordering. Alternating the order cancels linear drift.
  if [ $((b % 2)) -eq 1 ]; then order=(A B); else order=(B A); fi
  pos=0
  for arm_key in "${order[@]}"; do
    pos=$((pos+1))
    # Keep the fields separate. Packing label:path:expect into one string made a
    # valid APK path containing ':' select the wrong file, and made ':' in a label
    # change which expectation was applied to the arm.
    case "$arm_key" in
      A) arm="$LABEL_A"; apk="$APK_A"; expect_dd="$EXPECT_A" ;;
      B) arm="$LABEL_B"; apk="$APK_B"; expect_dd="$EXPECT_B" ;;
    esac
    install_and_attest "$apk" "$arm"
    write_header_once
    record_permission_state "$arm_key" "$arm"
    dd_count=$(probe_datadog "$arm" "$b" "$pos" | tail -1)
    if [ "$expect_dd" = "1" ] && [ "$dd_count" -eq 0 ]; then
      die "[$arm] expected Datadog ACTIVE but found 0 datadog-* threads. \
Datadog did not initialize — fix before measuring (wrong APK? init gated by flag/consent?)."
    fi
    if [ "$expect_dd" = "0" ] && [ "$dd_count" -ne 0 ]; then
      die "[$arm] expected Datadog ABSENT but found $dd_count datadog-* threads."
    fi
    log ">>> [$arm] warm-up x$WARMUP (pre-registered discard)"
    measure "$arm" "$b" "warmup" "$WARMUP" "$pos"
    log ">>> [$arm] measuring x$RUNS"
    measure "$arm" "$b" "measure" "$RUNS" "$pos"
    log "thermal after $arm block $b: $(thermal_snapshot)"
  done
done

log "done -> $OUT"
log "analyze with: ab_stats.py $OUT   (filter phase==measure)"
