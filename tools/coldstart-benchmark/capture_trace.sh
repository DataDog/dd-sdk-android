#!/usr/bin/env bash
# Unless explicitly stated otherwise all files in this repository are licensed
# under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
# Cold-start Perfetto capture with attestation + Datadog liveness proof.
#
# Fixes the two defects that made the original trace pair unusable:
#   1. trace.sh never installed anything, so there was no guarantee which APK
#      was actually traced. This script installs + md5-attests the APK.
#   2. Nothing verified Datadog was running. The "with-datadog" trace had zero
#      Datadog threads/slices. This script refuses to save a trace whose arm
#      expectation is violated.
#
# Also adds sched_blocked_reason (I/O-wait attribution) which the original
# config omitted, and drives the app to a fixed state to reduce content-driven
# variance (the original pair differed by ~90 ExoPlayer/MediaCodec threads
# because one run played video and the other did not).
#
# Usage: EXPECTED_APK_MD5=<benchmark arm digest> \
#          EXPECTED_PERMISSION_STATE_ID=<benchmark arm permission_a|permission_b> \
#          EXPECTED_COMPILE_STATUS=<benchmark compile_status> \
#          EXPECTED_PERF_MODE=<benchmark perf_mode> \
#          EXPECTED_WARMUP=<benchmark warmup> \
#          EXPECTED_ANIMATIONS=<benchmark animations> \
#          EXPECTED_AIRPLANE=<benchmark airplane> \
#          EXPECTED_FP=<benchmark fp> \
#          EXPECTED_ANDROID_USER=<benchmark android_user> \
#          EXPECTED_SDK_LIVENESS=<selected arm expect_a|expect_b> \
#          EXPECTED_APP_TRACE_ID=<benchmark app_trace_id; app_trace_ms only> \
#          ./capture_trace.sh <apk> <name> <expect-datadog:0|1>
set -euo pipefail

PKG="${PKG:?set PKG to your application id, e.g. PKG=com.example.app}"
COMPILE_FILTER="${COMPILE_FILTER:-speed-profile}"
# The trace exists to explain a completed A/B run, whose header records the
# achieved state separately from COMPILE_FILTER. Requiring that value prevents a
# verify/speed-profile mismatch from being attributed to the SDK in the trace pair.
EXPECTED_COMPILE_STATUS="${EXPECTED_COMPILE_STATUS:-}"
# A host-to-device digest match proves installation integrity, but not that this is
# the APK whose A/B result the trace is meant to explain. Require the selected
# benchmark arm's digest as that cross-artifact identity.
EXPECTED_APK_MD5="${EXPECTED_APK_MD5:-}"
# Permission grants can change with device role/exemption state even for the same
# APK. The benchmark records the effective state per arm; the trace must reproduce it.
EXPECTED_PERMISSION_STATE_ID="${EXPECTED_PERMISSION_STATE_ID:-}"
# Fixed-performance mode is an achieved device outcome, not merely a command both
# workflows request. A transient HAL refusal can otherwise put trace and A/B in
# different CPU scheduling scenarios.
EXPECTED_PERF_MODE="${EXPECTED_PERF_MODE:-}"
# The build fingerprint of the device the A/B ran on. Nothing else here pins device
# identity, so a trace captured on a different model -- the largest scenario
# difference there is, and the one ab_stats.py refuses to pool across -- was accepted
# as an explanation of that run.
EXPECTED_FP="${EXPECTED_FP:-}"
# Android users and work profiles can have different policy, storage and startup
# state on the same build. Bind the trace to the benchmark's selected user.
EXPECTED_ANDROID_USER="${EXPECTED_ANDROID_USER:-}"
# The positional argument drives every runtime liveness gate. This independent
# expected value binds that choice to the selected benchmark arm's expect_a/b stamp.
EXPECTED_SDK_LIVENESS="${EXPECTED_SDK_LIVENESS:-}"
# Must match the ANIMATIONS the A/B was run with. Forcing 0 unconditionally meant a
# benchmark run with ANIMATIONS=1 was traced with animations OFF -- so the trace omits
# the per-frame SDK work whose cost that benchmark included, and the two are no longer
# the same scenario, which is the one thing trace comparison requires.
#
# A DEFAULT of 0 reintroduced that same hazard by a different route: the guide
# recommends ANIMATIONS=1 as the honest per-frame measurement, so a non-default
# benchmark value is the expected case, and omitting it here silently traced the
# other scenario. Driven by the benchmark header like WARMUP: EXPECTED_ANIMATIONS is
# required, supplies ANIMATIONS when that is unset, and an explicit disagreement
# aborts before device access.
EXPECTED_ANIMATIONS="${EXPECTED_ANIMATIONS:-}"
ANIMATIONS="${ANIMATIONS:-$EXPECTED_ANIMATIONS}"
# Same argument as ANIMATIONS: trace and benchmark must use the same controlled
# Wi-Fi/mobile-radio state. Reachability is not inferred from an enabled setting;
# the operator must hold the external network condition stable across both. Driven by
# the benchmark header for the same reason as ANIMATIONS above: `airplane` is one of
# ab_stats.py's _MUST_MATCH keys, so two CSVs that disagree on it cannot be pooled --
# and a trace that disagrees with the CSV it explains is the same error, unchecked.
EXPECTED_AIRPLANE="${EXPECTED_AIRPLANE:-}"
AIRPLANE="${AIRPLANE:-$EXPECTED_AIRPLANE}"
# The benchmark header is the source of truth for trace conditioning. WARMUP remains
# accepted as an explicit operator setting, but defaults to EXPECTED_WARMUP and must
# equal it, so omitting or mistyping a non-default benchmark value cannot silently
# fall back to three. A benchmark cell runs ONE liveness-probe launch and then
# $WARMUP warm-ups before its first measured launch, so the launch it measures is the (WARMUP+2)-th
# after install. Capturing after only $WARMUP settle launches traced the
# (WARMUP+1)-th -- one launch earlier in the JIT/profile ramp, which matters most
# under the default fresh-install `speed-profile` condition, where there is no
# profile at install time and each launch adds to it.
EXPECTED_WARMUP="${EXPECTED_WARMUP:-}"
WARMUP="${WARMUP:-$EXPECTED_WARMUP}"
# The trace must reach the same endpoint as the A/B it is meant to explain.
# total_ms is the benchmark's default (am start -W TotalTime / first frame).
# TTFD and an app-owned log endpoint need an explicit log marker before the
# Perfetto process exits, otherwise a long trace can look complete even though
# the launch never reached the window under study.
TRACE_ENDPOINT="${TRACE_ENDPOINT:-total_ms}"
APP_TRACE_REGEX="${APP_TRACE_REGEX:-}"
# app_trace_ms is defined by the app's regex. Its benchmark header stores the
# regex's digest because the raw pattern cannot safely fit in whitespace metadata.
EXPECTED_APP_TRACE_ID="${EXPECTED_APP_TRACE_ID:-}"
# Escape hatch for a device whose ActivityManager does not emit the global
# `launching: <pkg>` slice the whole-window foreground check prefers. Forwarded to
# the verifier, which then runs the lifecycle-only check and reports the capture as
# PARTIALLY verified -- never as clean.
ALLOW_MISSING_LAUNCH_MARKER="${ALLOW_MISSING_LAUNCH_MARKER:-0}"
REMOTE_TRACE="/data/misc/perfetto-traces/dd-coldstart-$$.pftrace"
APK="${1:?usage: $0 <apk> <name> <expect-datadog 0|1>}"
NAME="${2:?}"
EXPECT_DD="${3:?}"

die() { echo "FATAL: $*" >&2; exit 1; }
require_expected_md5() {
  local name="$1" source="$2" value="${!1}"
  [ -n "$value" ] || die "set $name to the benchmark CSV's $source value"
  [ "${#value}" -eq 32 ] \
    || die "invalid $name: expected 32 lowercase hexadecimal characters"
  case "$value" in
    *[!0-9a-f]*) die "invalid $name: expected 32 lowercase hexadecimal characters" ;;
  esac
}
case "$PKG" in *[!a-zA-Z0-9._]*|""|.*|*.) die "invalid application id: '$PKG'" ;; esac
require_expected_md5 EXPECTED_APK_MD5 "baseline_md5 or treatment_md5"
require_expected_md5 EXPECTED_PERMISSION_STATE_ID "permission_a or permission_b"
case "$EXPECTED_COMPILE_STATUS" in
  "") die "set EXPECTED_COMPILE_STATUS to the benchmark CSV header's compile_status" ;;
  *[!a-zA-Z0-9_.+-]*) die "invalid EXPECTED_COMPILE_STATUS: '$EXPECTED_COMPILE_STATUS'" ;;
esac
case "$EXPECTED_PERF_MODE" in
  fixed|dynamic) ;;
  "") die "set EXPECTED_PERF_MODE to the benchmark CSV header's perf_mode" ;;
  *) die "EXPECTED_PERF_MODE must be fixed or dynamic (got '$EXPECTED_PERF_MODE')" ;;
esac
case "$EXPECTED_WARMUP" in
  "") die "set EXPECTED_WARMUP to the benchmark CSV header's warmup value" ;;
  *[!0-9]*) die "EXPECTED_WARMUP must be a non-negative integer (got '$EXPECTED_WARMUP')" ;;
esac
case "$EXPECTED_ANIMATIONS" in
  0|1) ;;
  "") die "set EXPECTED_ANIMATIONS to the benchmark CSV header's animations value" ;;
  *) die "EXPECTED_ANIMATIONS must be 0 or 1 (got '$EXPECTED_ANIMATIONS')" ;;
esac
case "$EXPECTED_AIRPLANE" in
  0|1) ;;
  "") die "set EXPECTED_AIRPLANE to the benchmark CSV header's airplane value" ;;
  *) die "EXPECTED_AIRPLANE must be 0 or 1 (got '$EXPECTED_AIRPLANE')" ;;
esac
# Fingerprints carry '/' and ':'; only whitespace would break the comparison, and an
# empty value would make it vacuous.
case "$EXPECTED_FP" in
  "") die "set EXPECTED_FP to the benchmark CSV header's fp value" ;;
  *[!a-zA-Z0-9._:/+-]*) die "invalid EXPECTED_FP: '$EXPECTED_FP'" ;;
esac
case "$EXPECTED_ANDROID_USER" in
  "") die "set EXPECTED_ANDROID_USER to the benchmark CSV header's android_user value" ;;
  *[!0-9]*) die "EXPECTED_ANDROID_USER must be a non-negative integer (got '$EXPECTED_ANDROID_USER')" ;;
esac
case "$EXPECTED_SDK_LIVENESS" in
  0|1) ;;
  "") die "set EXPECTED_SDK_LIVENESS to the selected benchmark arm's expect_a or expect_b value" ;;
  *) die "EXPECTED_SDK_LIVENESS must be 0 or 1 (got '$EXPECTED_SDK_LIVENESS')" ;;
esac
case "$EXPECT_DD" in 0|1) ;; *) die "expect-datadog must be 0 or 1 (got '$EXPECT_DD')" ;; esac
[ "$EXPECT_DD" = "$EXPECTED_SDK_LIVENESS" ] \
  || die "expect-datadog=$EXPECT_DD, but the selected benchmark arm recorded
       expect=$EXPECTED_SDK_LIVENESS. The trace must prove the same SDK runtime state."
case "$NAME" in */*|*..*|"") die "invalid trace name: '$NAME'" ;; esac
case "$ANIMATIONS" in 0|1) ;; *) die "ANIMATIONS must be 0 or 1 (got '$ANIMATIONS')" ;; esac
case "$AIRPLANE" in 0|1) ;; *) die "AIRPLANE must be 0 or 1 (got '$AIRPLANE')" ;; esac
case "$WARMUP" in ''|*[!0-9]*) die "WARMUP must be a non-negative integer (got '$WARMUP')" ;; esac
[ "$WARMUP" = "$EXPECTED_WARMUP" ] \
  || die "WARMUP=$WARMUP, but the benchmark recorded warmup=$EXPECTED_WARMUP.
       The trace must use the same post-install launch ordinal as the A/B."
[ "$ANIMATIONS" = "$EXPECTED_ANIMATIONS" ] \
  || die "ANIMATIONS=$ANIMATIONS, but the benchmark recorded
       animations=$EXPECTED_ANIMATIONS. Animation scales change how many frames the
       launch draws, so the two would not be the same scenario."
[ "$AIRPLANE" = "$EXPECTED_AIRPLANE" ] \
  || die "AIRPLANE=$AIRPLANE, but the benchmark recorded airplane=$EXPECTED_AIRPLANE.
       The controlled Wi-Fi/mobile-radio state must be the one the A/B measured."
case "$ALLOW_MISSING_LAUNCH_MARKER" in 0|1) ;;
  *) die "ALLOW_MISSING_LAUNCH_MARKER must be 0 or 1 (got '$ALLOW_MISSING_LAUNCH_MARKER')" ;; esac
case "$TRACE_ENDPOINT" in
  total_ms|ttfd) ;;
  app_trace_ms)
    [ -n "$APP_TRACE_REGEX" ] || die "TRACE_ENDPOINT=app_trace_ms requires APP_TRACE_REGEX"
    require_expected_md5 EXPECTED_APP_TRACE_ID "app_trace_id" ;;
  *) die "TRACE_ENDPOINT must be total_ms, ttfd or app_trace_ms (got '$TRACE_ENDPOINT')" ;;
esac
if [ -n "$APP_TRACE_REGEX" ]; then
  _re_rc=0
  _re_err=$(printf 'compile test\n' | grep -oE "$APP_TRACE_REGEX" 2>&1) || _re_rc=$?
  [ "$_re_rc" -le 1 ] || die "APP_TRACE_REGEX is not a valid POSIX ERE; grep rejected it
       (exit $_re_rc): ${_re_err:-no message}
       Pattern: $APP_TRACE_REGEX"
fi
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
if [ "$TRACE_ENDPOINT" = app_trace_ms ]; then
  TRACE_APP_TRACE_ID=$(dd_md5_str "$APP_TRACE_REGEX") \
    || die "cannot hash APP_TRACE_REGEX"
  [ "$TRACE_APP_TRACE_ID" = "$EXPECTED_APP_TRACE_ID" ] \
    || die "APP_TRACE_REGEX hashes to $TRACE_APP_TRACE_ID, but the benchmark recorded
         app_trace_id=$EXPECTED_APP_TRACE_ID. The trace endpoint is a different app event."
fi
# +1 = the benchmark's liveness-probe launch, which precedes its warm-ups.
# Not named SETTLE: verify_sdk_active.sh already uses that for a number of
# SECONDS, and this is a number of LAUNCHES.
SETTLE_LAUNCHES=$((WARMUP + 1))
[ -f "$APK" ] || die "APK not found: $APK"
TRACE_FILE="./$NAME.pftrace"
PKG_RE=$(printf '%s' "$PKG" | sed 's/[.]/\\./g')
log() { echo "[$(date +%H:%M:%S)] $*" >&2; }

HOST_MD5=$(dd_md5 "$APK") || die "cannot hash APK: $APK"
[ "$HOST_MD5" = "$EXPECTED_APK_MD5" ] \
  || die "trace APK md5=$HOST_MD5, but the selected benchmark arm recorded
       md5=$EXPECTED_APK_MD5. Use the exact APK measured by that arm; a rebuilt or
       substituted binary cannot explain its A/B result."
log "benchmark APK digest matched: $HOST_MD5"
dd_resolve_tools || exit 2
dd_require_device || exit 2
DD_ANDROID_USER=""
dd_resolve_android_user || exit 2
[ "$DD_ANDROID_USER" = "$EXPECTED_ANDROID_USER" ] \
  || die "this device's active Android user is $DD_ANDROID_USER, but the benchmark
       recorded android_user=$EXPECTED_ANDROID_USER. A different profile is a different scenario."
log "benchmark Android user matched: $DD_ANDROID_USER"
# Before anything is installed or changed. `fp` is one of ab_stats.py's _MUST_MATCH
# keys precisely because a different build is a different experiment; the same is
# true of the trace meant to explain it.
DEV_FP=$("$ADB" shell getprop ro.build.fingerprint 2>/dev/null | tr -d '\r') \
  || die "cannot read this device's build fingerprint"
[ -n "$DEV_FP" ] || die "this device reported an empty build fingerprint, so it
       cannot be checked against the benchmark's fp=$EXPECTED_FP."
[ "$DEV_FP" = "$EXPECTED_FP" ] \
  || die "this device is $DEV_FP, but the benchmark ran on $EXPECTED_FP.
       A trace from another build or model does not explain that run's A/B result."
log "benchmark device fingerprint matched: $DEV_FP"
log "android user: $DD_ANDROID_USER"
if [ "$TRACE_ENDPOINT" = app_trace_ms ]; then
  dd_logcat_supports_uid_filter || die "this device's logcat has no --uid filter.
       TRACE_ENDPOINT=app_trace_ms must be attributable to the installed package;
       a device-wide watcher can accept another process's matching line."
fi
# Animations are not startup time, so they come off -- but put them back on the
# way out, including on Ctrl-C. Leaving a borrowed device with animations
# permanently disabled (and a trace file in /data/misc) is not acceptable.
# All three scales are read separately. Restoring all three from the window one
# rewrites the other two on any device where they differed -- which silently broke
# the restoration guarantee this comment claims.
_ORIG_ANIM_window_animation_scale=""
_ORIG_ANIM_transition_animation_scale=""
_ORIG_ANIM_animator_duration_scale=""
for _s in window_animation_scale transition_animation_scale animator_duration_scale; do
  _v=$(dd_snapshot_numeric_setting global "$_s") || exit 2
  printf -v "_ORIG_ANIM_$_s" '%s' "$_v"
done
_ORIG_WIFI=$(dd_snapshot_radio_setting global wifi_on) || exit 2
_ORIG_DATA=$(dd_snapshot_radio_setting global mobile_data) || exit 2
# Empty is reachable only through ALLOW_UNVERIFIED_RADIOS, which the snapshot
# helper enforces; a numeric value that is neither 0 nor 1 is not a radio state
# this harness knows how to restore.
for _v in "$_ORIG_WIFI" "$_ORIG_DATA"; do
  case "$_v" in
    0|1|'') ;;
    *) die "radio snapshot must read 0 or 1 (got '$_v')." ;;
  esac
done
_ORIG_STAY=$(dd_snapshot_numeric_setting global stay_on_while_plugged_in) || exit 2
_ORIG_TIMEOUT=$(dd_snapshot_numeric_setting system screen_off_timeout) || exit 2
_WE_SET_PERF=0
_WE_SET_DEXOPT=0
_TRACE_RESERVED=0
PERFETTO_PID=""
_ENDPOINT_WATCH_PID=""
_ENDPOINT_WATCH_PGID=""
_ENDPOINT_FILE=""
# Stop the endpoint watcher, and with it the `logcat` it left on the DEVICE.
# `$!` on a background pipeline is the LAST element (grep), and killing grep does
# not stop the `adb shell logcat` feeding it, nor the logcat process on the device:
# measured on a moto g(60)s / Android 12, the device-side logcat was still running
# 10s after grep exited, and only died when the host `adb shell logcat` client was
# killed -- one leaked process per capture. The watcher is therefore started under
# job control so it owns a process group, and stopped by group.
# Deliberately no `wait`: this runs from the EXIT trap, which Ctrl-C also takes, and
# a cleanup path that can block is worse than a transient zombie the kernel reaps.
dd_stop_endpoint_watcher() {
  [ -n "${_ENDPOINT_WATCH_PGID:-}${_ENDPOINT_WATCH_PID:-}" ] || return 0
  if [ -n "${_ENDPOINT_WATCH_PGID:-}" ]; then
    kill -- -"$_ENDPOINT_WATCH_PGID" >/dev/null 2>&1 || true
  fi
  # The pipeline may have exited before Bash exposed its job-table PGID. Keep the
  # tail PID as a best-effort fallback, but never mistake it for the group leader.
  if [ -n "${_ENDPOINT_WATCH_PID:-}" ]; then
    kill "$_ENDPOINT_WATCH_PID" >/dev/null 2>&1 || true
  fi
  _ENDPOINT_WATCH_PGID=""
  _ENDPOINT_WATCH_PID=""
}
cleanup() {
  local rc=$?
  local _var _orig
  # Stop host-side readers before changing the device back. On an early failure
  # Perfetto may still be recording and the endpoint watcher may still own an adb
  # connection; leaving either alive races the remote-trace deletion below.
  if [ -n "${PERFETTO_PID:-}" ]; then
    kill "$PERFETTO_PID" >/dev/null 2>&1 || true
    wait "$PERFETTO_PID" >/dev/null 2>&1 || true
  fi
  dd_stop_endpoint_watcher
  for _s in window_animation_scale transition_animation_scale animator_duration_scale; do
    _var="_ORIG_ANIM_$_s"; _orig="${!_var}"
    "$ADB" shell settings put global "$_s" "$_orig" >/dev/null 2>&1 || true
  done
  "$ADB" shell settings put global stay_on_while_plugged_in "$_ORIG_STAY" >/dev/null 2>&1 || true
  "$ADB" shell settings put system screen_off_timeout "$_ORIG_TIMEOUT" >/dev/null 2>&1 || true
  case "$_ORIG_WIFI" in 0) "$ADB" shell svc wifi disable >/dev/null 2>&1 || true ;;
                        1) "$ADB" shell svc wifi enable  >/dev/null 2>&1 || true ;; esac
  case "$_ORIG_DATA" in 0) "$ADB" shell svc data disable >/dev/null 2>&1 || true ;;
                        1) "$ADB" shell svc data enable  >/dev/null 2>&1 || true ;; esac
  # Android exposes no getter for either control. Match coldstart_bench.sh's
  # best-effort restoration: undo only a command that this capture successfully
  # issued, rather than unconditionally flipping both settings on exit.
  [ "${_WE_SET_PERF:-0}" = 1 ] && { "$ADB" shell cmd power set-fixed-performance-mode-enabled false >/dev/null 2>&1 || true; }
  [ "${_WE_SET_DEXOPT:-0}" = 1 ] && { "$ADB" shell cmd package bg-dexopt-job --enable >/dev/null 2>&1 || true; }
  [ -z "${_ENDPOINT_FILE:-}" ] || rm -f "$_ENDPOINT_FILE"
  "$ADB" shell rm -f "$REMOTE_TRACE" >/dev/null 2>&1 || true
  # Hand back exactly the permissions we force-granted below -- not a device-wide
  # `pm reset-permissions`, which would also revoke grants for every other app on
  # a borrowed device.
  for _p in ${_GRANTED:-}; do
    "$ADB" shell pm revoke --user "$DD_ANDROID_USER" "$PKG" "$_p" >/dev/null 2>&1 || true
  done
  if [ "${_TRACE_RESERVED:-0}" = 1 ]; then
    rm -f "$TRACE_FILE"
  fi
  return $rc
}
# Restore on EXIT only. `trap cleanup INT` returns to the interrupted line, so
# Ctrl-C mid-capture used to delete the on-device trace and revoke the grants and
# then carry on to pull a file that no longer exists. Exiting routes through EXIT
# once, with the right status.
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

dd_reserve_output_files "$TRACE_FILE" || die "trace output reservation failed"
_TRACE_RESERVED=1
dd_apply_animation_scales "$ANIMATIONS" || exit 2
[ "$ANIMATIONS" = 1 ] && log "animations ENABLED -- matching a benchmark run with ANIMATIONS=1"
dd_apply_radio_state "$AIRPLANE" || exit 2
# Trace the same scheduling/compilation scenario the A/B measured. Leaving these
# controls out made trace attribution observe dynamic CPU behavior and background
# dexopt work that every benchmark launch explicitly excluded.
attest_performance_mode() {
  [ "$DD_PERF_MODE" = "$EXPECTED_PERF_MODE" ] \
    || die "trace achieved perf_mode=$DD_PERF_MODE, but the benchmark recorded
         perf_mode=$EXPECTED_PERF_MODE. These are different CPU scheduling scenarios."
  log "benchmark performance mode matched: $DD_PERF_MODE"
}
dd_enable_fixed_performance_mode || exit 2
# Only when the device accepted it: see the note in coldstart_bench.sh's pin_device.
if [ "$DD_PERF_MODE" = fixed ]; then
  _WE_SET_PERF=1
fi
attest_performance_mode
log "CPU scheduling scenario: perf_mode=$DD_PERF_MODE"
dd_disable_background_dexopt || exit 2
_WE_SET_DEXOPT=1
# Keep the screen on for the whole capture. $SETTLE_LAUNCHES settle launches plus a 20s trace
# outlast a default screen timeout, and a screen that sleeps mid-capture relocks the
# device -- which produces a trace with no rendering in it.
"$ADB" shell settings put global stay_on_while_plugged_in 3 >/dev/null 2>&1 || true
"$ADB" shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1 || true

# A locked device resumes the activity but never draws, so the trace would contain
# no rendering and `am start -W` no TotalTime. Same gate the benchmark applies.
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true
sleep 1
dd_require_unlocked || exit 2

# Verify the APK actually declares PKG BEFORE uninstalling anything. The md5
# attestation below only proves the file we pushed is the file that landed -- it
# says nothing about whether PKG names the same app, and by then the uninstall has
# already destroyed the data of whatever app did own that id. coldstart_bench.sh
# gates on this; trace capture is just as destructive and did not.
if [ "${ALLOW_UNVERIFIED_PKG:-0}" = "1" ]; then
  log "WARNING: ALLOW_UNVERIFIED_PKG=1 -- the APK/package check is DISABLED."
  log "         'adb uninstall $PKG' will run against whatever app owns that id."
else
  [ -n "${AAPT2:-}" ] || die "aapt2 not found, so the APK's package name cannot be
       verified against PKG='$PKG' before 'adb uninstall' destroys that app's data.
       Fix: install Android SDK build-tools, or set AAPT2=/path/to/aapt2.
       Override with ALLOW_UNVERIFIED_PKG=1 once you have checked by hand."
  APK_PKG=$("$AAPT2" dump badging "$APK" 2>/dev/null \
            | awk -F"'" '/^package: name=/{print $2; exit}' || true)
  [ -n "$APK_PKG" ] || die "aapt2 could not read a package name from $APK."
  [ "$APK_PKG" = "$PKG" ] || die "PKG='$PKG' but the APK declares '$APK_PKG'.
       Refusing to uninstall '$PKG' -- that would wipe an unrelated app's data."
  log "APK declares $APK_PKG, matches PKG"
fi

log "installing $(basename "$APK") md5=$HOST_MD5"
dd_ensure_uninstalled "$PKG" || die "uninstall did not establish a clean install state"
"$ADB" install --user "$DD_ANDROID_USER" -r "$APK" >/dev/null || die "install failed"
REMOTE=$(dd_package_path "$PKG" | head -1 | sed 's/package://') \
  || die "cannot read the installed APK path"
DEV_MD5=$("$ADB" shell md5sum "$REMOTE" | awk '{print $1}' | tr -d '\r')
[ "$HOST_MD5" = "$DEV_MD5" ] || die "APK attestation failed host=$HOST_MD5 dev=$DEV_MD5"
log "APK attested OK"

PKG_UID=""
if [ "$TRACE_ENDPOINT" = app_trace_ms ]; then
  PKG_UID=$(dd_unique_pkg_uid "$PKG") || die "cannot scope APP_TRACE_REGEX to $PKG"
  log "app-owned trace endpoint scoped to package UID $PKG_UID"
fi

"$ADB" shell cmd package compile -m "$COMPILE_FILTER" -f "$PKG" >/dev/null \
  || die "'cmd package compile -m $COMPILE_FILTER' failed"
TRACE_COMPILE_STATUS=$(dd_package_compile_status "$PKG") \
  || die "achieved compilation state is unreadable after a successful compile"
log "AOT compile requested -m $COMPILE_FILTER; achieved status=$TRACE_COMPILE_STATUS"
[ "$TRACE_COMPILE_STATUS" = "$EXPECTED_COMPILE_STATUS" ] \
  || die "trace APK achieved compile_status=$TRACE_COMPILE_STATUS, but the benchmark
       this trace is meant to explain recorded compile_status=$EXPECTED_COMPILE_STATUS.
       Use matching APKs/device state; do not attribute compilation-state work to the SDK."

# Pre-grant every runtime permission the app declares -- the same thing
# coldstart_bench.sh does before it measures anything.
#
# WHY THIS IS ESSENTIAL HERE TOO: an app that asks for runtime permissions on
# first launch gets GrantPermissionsActivity stacked on top of it. That is a
# second activity launch inside the window, it pauses (and can stop) the app
# under trace, and on a stopped activity the framework produces no frames -- so
# the app never reaches its fully-drawn point and the trace records a scenario
# that never happened in the benchmark. A whole trace set was thrown away to
# this: the baseline arm was stopped at +1030 ms and rendered nothing for the
# rest of the capture, while both treatment arms carried a permissioncontroller
# launch mid-window. A trace has to be the same scenario the A/B measured, or
# its deltas are not comparable to the A/B's.
#
# Derived from the package manager rather than hardcoded, so it tracks any build.
_GRANTED=""
grant_runtime_permissions() {
  if ! dd_grant_runtime_permissions "$PKG"; then
    # Preserve successful grants for cleanup even on a partial failure.
    _GRANTED="$DD_GRANTED_PERMISSIONS"
    die "runtime-permission setup was incomplete; refusing a different trace scenario"
  fi
  _GRANTED="$DD_GRANTED_PERMISSIONS"
  log "pre-granted $DD_GRANTED_PERMISSION_COUNT/$DD_RUNTIME_PERMISSION_COUNT runtime permissions"
  return 0
}
attest_permission_state() {
  [ "$DD_PERMISSION_STATE_ID" = "$EXPECTED_PERMISSION_STATE_ID" ] \
    || die "trace permission state=$DD_PERMISSION_STATE_ID, but the selected benchmark
         arm recorded $EXPECTED_PERMISSION_STATE_ID. Role, exemption or package-policy
         state changed; this trace would exercise a different startup scenario."
  log "benchmark permission state matched: $DD_PERMISSION_STATE_ID"
}
grant_runtime_permissions
attest_permission_state

# Measure the REAL user cold start: resolve the launcher activity rather than
# hardcoding a component. Apps commonly route the launcher through
# activity-aliases, so `am start -n <activity>` may not be the path a user
# actually takes.
ACT=$("$ADB" shell cmd package resolve-activity --brief --user "$DD_ANDROID_USER" \
       -c android.intent.category.LAUNCHER "$PKG" \
       | tail -1 | tr -d '\r')
# `resolve-activity --brief` prints the literal text "No activity found" (exit 0)
# when nothing matches, so a bare non-empty test passes it straight through to
# `am start -n "No activity found"`. Verified on a moto g(60)s / Android 12.
# Check the SHAPE instead: it must be <pkg>/<component> for the app under test.
case "$ACT" in
  "$PKG"/*) ;;
  *) die "could not resolve a launcher activity for $PKG (got '${ACT:-nothing}')." ;;
esac
START_ARGS=(--user "$DD_ANDROID_USER" -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER -n "$ACT")
log "launcher activity: $ACT"

# Select the host-observed endpoint before the final conditioning wait. Perfetto
# starts during that already-registered wait, so nothing should be inserted between
# the end of the wait and the measured launch's force-stop.
_ENDPOINT_REGEX=""
_ENDPOINT_UID=""
case "$TRACE_ENDPOINT" in
  ttfd) _ENDPOINT_REGEX="Fully drawn $PKG_RE/" ;;
  app_trace_ms) _ENDPOINT_REGEX="$APP_TRACE_REGEX"; _ENDPOINT_UID="$PKG_UID" ;;
esac

_PERFETTO_READY_WAIT=4
start_perfetto() {
  [ -z "${PERFETTO_PID:-}" ] || die "Perfetto was started more than once"
  log "starting perfetto during the final conditioning wait"
  cat <<EOF | "$ADB" shell perfetto -c - --txt -o "$REMOTE_TRACE" &
buffers: { size_kb: 65536 fill_policy: RING_BUFFER }
data_sources: {
  config {
    name: "linux.ftrace"
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_waking"
      ftrace_events: "sched/sched_blocked_reason"
      ftrace_events: "sched/sched_process_exit"
      ftrace_events: "sched/sched_process_free"
      ftrace_events: "task/task_newtask"
      ftrace_events: "task/task_rename"
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      atrace_categories: "am"
      atrace_categories: "wm"
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_categories: "dalvik"
      atrace_categories: "binder_driver"
      atrace_categories: "pm"
      atrace_categories: "ss"
      atrace_categories: "res"
      atrace_categories: "database"
      atrace_categories: "disk"
      atrace_categories: "sched"
      atrace_apps: "$PKG"
    }
  }
}
data_sources: { config { name: "linux.process_stats"
  process_stats_config { scan_all_processes_on_start: true proc_stats_poll_ms: 1000
                         record_thread_names: true } } }
data_sources: { config { name: "linux.sys_stats"
  sys_stats_config { stat_period_ms: 1000 stat_counters: STAT_CPU_TIMES } } }
duration_ms: 25000
EOF
  PERFETTO_PID=$!
}

# settle: $SETTLE_LAUNCHES discarded launches so dex/oat caches and any first-run migrations
# are done, verifying Datadog liveness after each launch. The count reproduces
# the benchmark's probe + warm-up launches exactly (see WARMUP above), so the traced
# launch sits at the same point in the post-install ramp as a measured one.
log "settling: $SETTLE_LAUNCHES discarded launches (WARMUP=$WARMUP + 1 liveness probe),"
log "          so the traced launch is the $((SETTLE_LAUNCHES + 1))th after install -- the same"
log "          position as the benchmark's first measured launch"
for ((_i=1; _i<=SETTLE_LAUNCHES; _i++)); do
  # Discard 1 is coldstart_bench.sh's liveness probe. The rest are its warm-ups.
  # Reproduce both cadences, not just the ordinal launch: async migrations,
  # profile persistence, deferred SDK work and cache cooling all continue between
  # launches. The warm-up check happens after 6s and its final 4s wait happens only
  # after the evidence below is sampled, exactly as measure(..., phase=warmup) does.
  if [ "$_i" -eq 1 ]; then
    _SETTLE_KIND="liveness probe"
    _SETTLE_PRE_SLEEP=3
    _SETTLE_CHECK_SLEEP=8
    _SETTLE_FINAL_SLEEP=0
  else
    _SETTLE_KIND="warm-up"
    _SETTLE_PRE_SLEEP=5
    _SETTLE_CHECK_SLEEP=6
    _SETTLE_FINAL_SLEEP=4
  fi
  "$ADB" shell am force-stop --user "$DD_ANDROID_USER" "$PKG"
  sleep "$_SETTLE_PRE_SLEEP"
  "$ADB" shell logcat -c >/dev/null 2>&1 || true
  _SETTLE_POST_CLEAR=$("$ADB" shell logcat -d 2>/dev/null | tr -d '\r') || true
  _SETTLE_STALE=$(printf '%s\n' "$_SETTLE_POST_CLEAR" \
    | grep -cE "ActivityTaskManager: Displayed [a-zA-Z0-9_.]+/" || true)
  # Deliberately every package, not just $PKG: proving the buffer holds no Displayed
  # line at all is what makes a foreign one seen later provably inside this window.
  # The cause is not knowable from here -- a denied `logcat -c` and an unrelated
  # activity drawing in the same instant look identical -- so the message says what
  # was observed and lists the causes rather than asserting one.
  [ "${_SETTLE_STALE:-0}" -eq 0 ] || die "settle launch $_i/$SETTLE_LAUNCHES:
       $_SETTLE_STALE ActivityTaskManager Displayed marker(s) were in the buffer
       immediately after 'logcat -c'. Either clearing logcat is denied on this device,
       or something drew in that instant; without an empty buffer a later foreign
       draw cannot be attributed to this launch. Check 'adb shell logcat -c' first."
  _SETTLE_OUT=$("$ADB" shell am start -W "${START_ARGS[@]}" | tr -d '\r') \
    || die "settle launch $_i/$SETTLE_LAUNCHES: am start -W failed"
  dd_validate_cold_launch_output "$_SETTLE_OUT" \
    || die "settle launch $_i/$SETTLE_LAUNCHES: $DD_LAUNCH_ERROR"
  log "settle launch $_i/$SETTLE_LAUNCHES ($_SETTLE_KIND cadence): Status=$DD_LAUNCH_STATUS LaunchState=$DD_LAUNCH_STATE TotalTime=${DD_LAUNCH_TOTAL}ms"
  if [ "$_i" -eq "$SETTLE_LAUNCHES" ] && [ "$_SETTLE_FINAL_SLEEP" -eq 0 ]; then
    # WARMUP=0: the probe has no post-validation wait. Start Perfetto halfway
    # through its existing 8s validation delay and use the last 4s for readiness,
    # so validation still happens at +8s and force-stop follows immediately.
    _SETTLE_BEFORE_PERFETTO=$((_SETTLE_CHECK_SLEEP - _PERFETTO_READY_WAIT))
    [ "$_SETTLE_BEFORE_PERFETTO" -ge 0 ] \
      || die "the final conditioning wait is shorter than Perfetto readiness"
    [ "$_SETTLE_BEFORE_PERFETTO" -eq 0 ] || sleep "$_SETTLE_BEFORE_PERFETTO"
    start_perfetto
    sleep "$_PERFETTO_READY_WAIT"
  else
    sleep "$_SETTLE_CHECK_SLEEP"
  fi
  _SETTLE_LOG=$("$ADB" shell logcat -d 2>/dev/null | tr -d '\r') || true
  _SETTLE_TARGET_DISPLAYED=$(printf '%s\n' "$_SETTLE_LOG" \
    | grep -cE "ActivityTaskManager: Displayed $PKG_RE/" || true)
  [ "${_SETTLE_TARGET_DISPLAYED:-0}" -gt 0 ] \
    || die "settle launch $_i/$SETTLE_LAUNCHES: no ActivityTaskManager Displayed marker for $PKG"
  _SETTLE_FOREIGN=$(printf '%s\n' "$_SETTLE_LOG" \
    | dd_first_foreign_displayed_activity "$PKG") || true
  [ -z "$_SETTLE_FOREIGN" ] \
    || die "settle launch $_i/$SETTLE_LAUNCHES: foreign activity reached first draw: $_SETTLE_FOREIGN"
  _SETTLE_TOP=$(dd_top_activity)
  case "$_SETTLE_TOP" in
    "$PKG"/*) ;;
    *) die "settle launch $_i/$SETTLE_LAUNCHES: app is not the foreground activity (found '${_SETTLE_TOP:-nothing}')" ;;
  esac
  if ! _SETTLE_PIDS=$(dd_pkg_pids "$PKG"); then
    die "settle launch $_i/$SETTLE_LAUNCHES: complete package-process enumeration
         is unavailable, so SDK liveness is unverified"
  fi
  [ -n "$_SETTLE_PIDS" ] \
    || die "settle launch $_i/$SETTLE_LAUNCHES: app owns no running process"
  if ! _SETTLE_DD=$(dd_datadog_threads "$_SETTLE_PIDS"); then
    die "settle launch $_i/$SETTLE_LAUNCHES: could not read every package process's
         thread list, so SDK liveness is unverified"
  fi
  log "settle launch $_i/$SETTLE_LAUNCHES: processes=$(printf '%s' "$_SETTLE_PIDS" | tr '\n' ' ') datadog-*=$_SETTLE_DD"
  if [ "$EXPECT_DD" = 1 ] && [ "$_SETTLE_DD" -eq 0 ]; then
    die "settle launch $_i/$SETTLE_LAUNCHES: expected Datadog ACTIVE, found none"
  fi
  if [ "$EXPECT_DD" = 0 ] && [ "$_SETTLE_DD" -ne 0 ]; then
    die "settle launch $_i/$SETTLE_LAUNCHES: expected Datadog ABSENT, found $_SETTLE_DD datadog-* threads"
  fi
  if [ "$_SETTLE_FINAL_SLEEP" -gt 0 ]; then
    # WARMUP>0: this is the benchmark warm-up's own 4s post-validation
    # wait. Starting Perfetto here reuses it instead of adding another 4s.
    if [ "$_i" -eq "$SETTLE_LAUNCHES" ]; then
      [ "$_SETTLE_FINAL_SLEEP" -ge "$_PERFETTO_READY_WAIT" ] \
        || die "the final warm-up wait is shorter than Perfetto readiness"
      start_perfetto
    fi
    sleep "$_SETTLE_FINAL_SLEEP"
  fi
done

# Perfetto readiness consumed the conditioning wait above. Reproduce the benchmark's
# measured launch immediately from its own force-stop boundary; there is no second
# app-running wait here.
#
# BUDGET. From Perfetto start to `am start -W`, 4s of the conditioning wait plus
# the benchmark's own 5s pre-launch wait run inside the capture. Those 9s come
# out of `duration_ms` above, which is why it is 25000 and not 20000 -- the endpoint
# wait further down is bounded by Perfetto's lifetime, so shrinking the post-launch
# window is what makes a late `Fully drawn` marker fail. Any change to the cadence
# here has to be paid for there.
#
# Those 9s are the SLEEPS only. Under WARMUP=0 there is no post-validation wait to
# reuse, so Perfetto starts mid-wait and the final settle launch's validation --
# `logcat -d` over a full buffer, the Displayed/foreign scan, `dumpsys` for the top
# activity, `ps -A`, and a `/proc` read per process -- runs inside the capture too.
# Those adb round-trips are real seconds that this arithmetic does not model, so
# treat the post-launch window as a ceiling rather than a measurement. Under
# WARMUP>0 Perfetto starts after that validation and the 9s is exact.
[ -n "${PERFETTO_PID:-}" ] || die "Perfetto was not started during conditioning"
kill -0 "$PERFETTO_PID" >/dev/null 2>&1 \
  || die "Perfetto stopped before the traced launch could be prepared"
"$ADB" shell am force-stop --user "$DD_ANDROID_USER" "$PKG"
sleep 5
"$ADB" shell logcat -c >/dev/null 2>&1 || true

# For log-backed endpoints, prove that the post-wait boundary contains no stale
# marker before starting the watcher. A denied/no-op clear must not let a previous
# launch satisfy this capture's endpoint gate.
if [ -n "$_ENDPOINT_REGEX" ]; then
  if [ -n "$_ENDPOINT_UID" ]; then
    _post_clear=$("$ADB" shell logcat -d --uid="$_ENDPOINT_UID" 2>/dev/null | tr -d '\r') || true
  else
    _post_clear=$("$ADB" shell logcat -d 2>/dev/null | tr -d '\r') || true
  fi
  _stale_endpoint=$(printf '%s\n' "$_post_clear" | grep -cE "$_ENDPOINT_REGEX" || true)
  [ "${_stale_endpoint:-0}" -eq 0 ] || die "'logcat -c' left a previous
       TRACE_ENDPOINT=$TRACE_ENDPOINT marker in the buffer. Clearing logcat is
       likely denied on this device; fix that before tracing."
fi

if [ -n "$_ENDPOINT_REGEX" ]; then
  _ENDPOINT_FILE=$(mktemp "${TMPDIR:-/tmp}/dd-coldstart-endpoint.XXXXXX")
  # Stream logcat instead of polling dumpsys/logcat during the trace. The watcher
  # records the first endpoint marker and exits; the file is the cross-process
  # signal that the marker happened while Perfetto was still alive.
  # `set -m` puts the pipeline in its own process group so it can be killed as one.
  # The regex stays on the HOST on purpose: `logcat -e` would match the device side's
  # message field only, while coldstart_bench.sh scrapes APP_TRACE_REGEX against the
  # whole formatted line. Moving it would silently accept a different set of patterns
  # here than the benchmark accepts -- verified on-device that a tag-qualified pattern
  # matches host-side and not with `logcat -e`.
  set -m
  if [ -n "$_ENDPOINT_UID" ]; then
    "$ADB" shell logcat --uid="$_ENDPOINT_UID" 2>/dev/null \
      | grep -m1 -E "$_ENDPOINT_REGEX" >"$_ENDPOINT_FILE" &
  else
    "$ADB" shell logcat 2>/dev/null \
      | grep -m1 -E "$_ENDPOINT_REGEX" >"$_ENDPOINT_FILE" &
  fi
  _ENDPOINT_WATCH_PID=$!
  # With job control, every process in the pipeline joins a new process group led
  # by its FIRST command (`adb`). `$!` is the LAST command (`grep`), so it is not a
  # valid group id. Read the actual leader from Bash's job table while monitor mode
  # is still enabled; cleanup can then terminate host adb and device logcat as well
  # as grep.
  _ENDPOINT_WATCH_PGID=$(jobs -p %+)
  set +m
  # An empty job table is only fatal if the watcher is still RUNNING: then it
  # cannot be reaped by group and would leave a logcat on the device. If the
  # pipeline has already exited -- `adb` failing immediately on a disconnected
  # device is the realistic case -- there is nothing to reap, and aborting here
  # would replace that device's real error with a confusing one about job control.
  case "$_ENDPOINT_WATCH_PGID" in
    ''|*[!0-9]*)
      _ENDPOINT_WATCH_PGID=""
      if kill -0 "$_ENDPOINT_WATCH_PID" 2>/dev/null; then
        die "the endpoint watcher is running but its process-group leader could not
       be read from the job table, so cleanup could not stop the logcat it started
       on the device. Refusing to leave that behind."
      fi
      log "endpoint watcher exited before it could be grouped; nothing to reap" ;;
  esac
fi

LAUNCH_OUT=$("$ADB" shell am start -W "${START_ARGS[@]}" | tr -d '\r') \
  || die "traced am start -W failed"
dd_validate_cold_launch_output "$LAUNCH_OUT" || die "traced launch: $DD_LAUNCH_ERROR"
LAUNCH_STATUS="$DD_LAUNCH_STATUS"
LAUNCH_STATE="$DD_LAUNCH_STATE"
LAUNCH_TOTAL="$DD_LAUNCH_TOTAL"
# Deliberately conservative, and deliberately not phrased as a measurement: all
# this proves is that Perfetto was still alive when `am start -W` returned. It
# cannot separate "first frame just before Perfetto exited" from "just after".
kill -0 "$PERFETTO_PID" >/dev/null 2>&1 \
  || die "the first-frame endpoint could not be confirmed to fall inside the trace:
       Perfetto had already stopped by the time the traced launch returned."
log "traced launch reached first frame: Status=$LAUNCH_STATUS LaunchState=$LAUNCH_STATE TotalTime=${LAUNCH_TOTAL}ms"

if [ -n "$_ENDPOINT_REGEX" ]; then
  while [ ! -s "$_ENDPOINT_FILE" ] && kill -0 "$PERFETTO_PID" >/dev/null 2>&1; do
    sleep 0.1
  done
  [ -s "$_ENDPOINT_FILE" ] || die "TRACE_ENDPOINT=$TRACE_ENDPOINT was not reached
       before Perfetto stopped. The capture does not cover the measurement window."
  # Same conservatism as the first-frame check above: a marker landing in the last
  # moments of the window is indistinguishable from one landing just after it, so
  # this reports what it can prove rather than asserting the ordering.
  kill -0 "$PERFETTO_PID" >/dev/null 2>&1 \
    || die "TRACE_ENDPOINT=$TRACE_ENDPOINT could not be confirmed to have occurred
       before Perfetto stopped. The capture does not prove that it covers the
       measurement window."
  log "TRACE_ENDPOINT=$TRACE_ENDPOINT reached inside the trace"
  dd_stop_endpoint_watcher
fi
_perfetto_rc=0
wait "$PERFETTO_PID" || _perfetto_rc=$?
PERFETTO_PID=""
[ "$_perfetto_rc" -eq 0 ] || die "Perfetto failed (exit $_perfetto_rc)"

# Read the foreground state before the trace is pulled, but judge it after, so a
# rejected capture is still on disk to be looked at.
TOP=$(dd_top_activity)

"$ADB" pull "$REMOTE_TRACE" "$TRACE_FILE" >/dev/null || die "failed to pull trace"
[ -s "$TRACE_FILE" ] || die "pulled trace is empty -- perfetto produced no output"
_TRACE_RESERVED=0

# The app has to still be the resumed activity when the capture ends. If a
# permission dialog, a system prompt or the launcher took over, the trace
# records an app that was paused or stopped for part of the window, produced no
# frames while it was, and never reached its fully-drawn point -- none of which
# is the launch the benchmark measures. Liveness verification does not catch
# this: a stopped app still has all of its `datadog-*` threads.
case "$TOP" in
  "$PKG"/*) log "app still foreground at end of capture ($TOP)" ;;
  *) die "app was not foreground when the capture ended (top=${TOP:-unknown}).
  Something took the foreground -- most often a runtime permission dialog.
  This is a SNAPSHOT with no transition timestamp: it cannot say WHEN, and the
  traced launch above already proved Status=ok, LaunchState=COLD and a first
  frame, so on its own it does not establish that the measured interval was
  affected. Treated as fatal anyway, conservatively. The trace is kept at
  $TRACE_FILE; run the verifier on it by hand for timestamped ownership detail
  before deciding to re-capture:
    <python-with-perfetto> verify_trace.py $TRACE_FILE --package $PKG --require-foreground" ;;
esac
log "saved $TRACE_FILE (md5 $(dd_md5 "$TRACE_FILE"))"

VERIFIER="$(dirname "$0")/verify_trace.py"
[ -f "$VERIFIER" ] || die "verifier missing: $VERIFIER"
log "post-hoc trace verification:"
# --require-foreground makes the verifier check ownership across the WHOLE window
# from the trace's own lifecycle slices. The end-of-capture dumpsys check below is
# kept, but it only sees the final state: an activity that took over and handed back
# mid-window is invisible to it and visible here.
VERIFY_ARGS=(--package "$PKG" --require-foreground)
if [ "$ALLOW_MISSING_LAUNCH_MARKER" = 1 ]; then
  log "WARNING: ALLOW_MISSING_LAUNCH_MARKER=1 -- if ActivityManager's global"
  log "  'launching: $PKG' slice is missing, the whole-window check runs on this"
  log "  app's lifecycle slices alone. A foreign activity taking over BETWEEN two"
  log "  of this app's activities would then be invisible. The verifier will say so."
  VERIFY_ARGS+=(--allow-missing-launch-marker)
fi
if [ "$EXPECT_DD" = "1" ]; then VERIFY_ARGS+=(--expect-ndk)
else VERIFY_ARGS+=(--expect-absent); fi
# Resolve an interpreter that can import perfetto BEFORE judging the trace, so a
# missing dependency is never reported as a liveness failure. The trace is already
# on disk at this point and is worth keeping either way.
if ! dd_resolve_python; then
  log "trace saved to $TRACE_FILE but NOT verified -- see the error above."
  log "verify it once perfetto is installed:"
  log "  <python-with-perfetto> $VERIFIER $TRACE_FILE ${VERIFY_ARGS[*]}"
  exit 2
fi
# verify_trace.py distinguishes "SDK absent" (1) from "no cold start in this trace
# at all" (3). Collapsing both into one message sends you looking for a gated SDK
# when the real problem is that the launch happened outside the trace window.
set +e
"$PY" "$VERIFIER" "$TRACE_FILE" "${VERIFY_ARGS[@]}"
_vrc=$?
set -e
case $_vrc in
  0) ;;
  3) die "the capture contains no cold start (no bindApplication slice), so it cannot
       answer the question either way. The app was already running when tracing
       began. Kept at $TRACE_FILE. Re-capture." ;;
  4) die "the app did not own the foreground for the whole capture (see above).
       Part of the window was paused or stopped, so this is not the scenario the
       benchmark measures. Kept at $TRACE_FILE for inspection. Re-capture." ;;
  *) die "trace failed SDK liveness verification (arm expect=$EXPECT_DD, verifier exit $_vrc)" ;;
esac
