#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#
# Shared helpers for the cold-start harness. Source this, don't execute it.
#
# Resolves the Android tools WITHOUT depending on the caller's PATH, so the
# harness works from cron, CI, an IDE terminal, or a shell whose profile has
# not been fixed. Precedence:
#   1. $ADB / $ANDROID_HOME / $ANDROID_SDK_ROOT if already set
#   2. PATH
#   3. well-known macOS/Linux SDK locations

_dd_find_sdk() {
  local c
  for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
           "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "$HOME/Android/sdk" \
           "/usr/local/share/android-sdk" "/opt/homebrew/share/android-sdk"; do
    [ -n "$c" ] && [ -x "$c/platform-tools/adb" ] && { echo "$c"; return 0; }
  done
  return 1
}

# Version-ordered sort on stdin, portable across GNU and BSD.
#
# `sort -V` is not POSIX. Apple's sort (2.3, macOS) does implement it correctly,
# but older BSD sorts answer `illegal option -- V` and emit nothing -- which here
# would leave AAPT2 unset and send the operator chasing a build-tools install that
# is already present. Probe the local sort on a case plain lexical order gets
# wrong (1.9 must precede 1.10) and fall back to a numeric field sort, which is
# exact for the `major.minor.patch` build-tools directories this reads.
_dd_version_sort() {
  if printf '1.10\n1.9\n' | sort -V 2>/dev/null | head -1 | grep -qx '1\.9'; then
    sort -V
  else
    sort -t. -k1,1n -k2,2n -k3,3n
  fi
}

# Newest executable aapt2 from the first capable SDK root. A non-empty
# ANDROID_HOME is not sufficient: IDE upgrades and removed SDK installs commonly
# leave it pointing at a directory with no usable build-tools. Such a stale root
# must not mask ANDROID_SDK_ROOT or a well-known installation.
_dd_find_aapt2() {
  local c a bt
  for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
           "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "$HOME/Android/sdk" \
           "/usr/local/share/android-sdk" "/opt/homebrew/share/android-sdk"; do
    [ -n "$c" ] || continue
    bt=$(
      for a in "$c"/build-tools/*/aapt2; do
        [ -x "$a" ] && basename "$(dirname "$a")"
      done | _dd_version_sort | tail -1
    )
    if [ -n "$bt" ]; then
      printf '%s\n' "$c/build-tools/$bt/aapt2"
      return 0
    fi
  done
  return 1
}

dd_resolve_tools() {
  if [ -n "${ADB:-}" ] && [ -x "${ADB}" ]; then
    :
  elif command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
  else
    local sdk
    sdk="$(_dd_find_sdk)" || {
      echo "FATAL: cannot find adb. Set ADB=/path/to/adb or ANDROID_HOME." >&2
      return 1
    }
    ANDROID_HOME="$sdk"
    ADB="$sdk/platform-tools/adb"
  fi

  # aapt2 (optional): newest build-tools wins. Used by coldstart_bench.sh's
  # preflight to read each APK's package name and version out of the manifest.
  # Absent aapt2 downgrades that check to a warning rather than failing the run.
  if [ -z "${AAPT2:-}" ]; then
    local sdk
    AAPT2=$(_dd_find_aapt2 2>/dev/null || true)
    if [ -n "$AAPT2" ]; then
      sdk=${AAPT2%%/build-tools/*}
      ANDROID_HOME="$sdk"
    fi
  fi

  export ADB AAPT2 ANDROID_HOME
  return 0
}

# Snapshot one numeric Android user for the entire workflow. Android package
# state is user-scoped, while an unqualified `dumpsys package` prints every
# personal/work-profile user together. Every caller must resolve once and pass
# this value explicitly to package, permission and activity commands; otherwise a
# profile switch or a conflicting work-profile grant can silently change the
# scenario mid-run.
dd_resolve_android_user() {
  local user
  user=$("$ADB" shell am get-current-user 2>/dev/null | tr -d '\r') || user=""
  case "$user" in
    ''|*[!0-9]*)
      echo "FATAL: cannot resolve the current numeric Android user (got '${user:-missing}')." >&2
      return 1 ;;
  esac
  DD_ANDROID_USER="$user"
  export DD_ANDROID_USER
}

dd_require_android_user() {
  case "${DD_ANDROID_USER:-}" in
    ''|*[!0-9]*)
      echo "FATAL: DD_ANDROID_USER is unresolved; call dd_resolve_android_user first." >&2
      return 1 ;;
  esac
}

# Installed APK paths for one Android user. An empty, successful result means the
# package is absent; a command failure is not absence and must remain a hard error
# for clean-install workflows.
dd_package_path_for_user() {
  local pkg="$1" user="$2" raw status paths noise
  case "$user" in
    ''|*[!0-9]*)
      echo "FATAL: invalid Android user '$user' for package lookup." >&2
      return 1 ;;
  esac
  # `pm path` exits 1 for a package that is not installed for this user (verified
  # on Android 12), and `adb shell` forwards that 1 unchanged -- the same 1 it
  # returns when the shell never ran at all. Absence is the expected precondition
  # for a clean install; a transport failure is not, and treating them alike made
  # every install cycle abort on the normal path. Ask the device for pm's own exit
  # status so the two are distinguishable, and treat a missing marker -- the one
  # thing that cannot happen if the remote shell ran -- as the hard error.
  raw=$("$ADB" shell "pm path --user $user $pkg 2>&1; echo __dd_rc=\$?" 2>/dev/null \
        | tr -d '\r') || true
  status=$(printf '%s\n' "$raw" | sed -n 's/^__dd_rc=//p' | tail -1)
  case "$status" in
    ''|*[!0-9]*)
      echo "FATAL: cannot query package '$pkg' for Android user $user:" >&2
      echo "       the device shell reported no exit status. Output: ${raw:-none}" >&2
      return 1 ;;
  esac
  paths=$(printf '%s\n' "$raw" | grep '^package:' || true)
  noise=$(printf '%s\n' "$raw" | grep -v '^package:' | grep -v '^__dd_rc=' \
          | grep -v '^[[:space:]]*$' || true)
  if [ "$status" = 0 ]; then
    if [ -z "$paths" ]; then
      echo "FATAL: 'pm path' succeeded for '$pkg' but printed no package path." >&2
      echo "       Refusing to read that as either installed or absent." >&2
      return 1
    fi
  elif [ -n "$noise" ]; then
    # Silence is how absence is reported. Anything printed alongside the failure
    # is something this function cannot interpret, so it does not guess.
    echo "FATAL: 'pm path' failed (exit $status) for '$pkg' on user $user:" >&2
    echo "       $noise" >&2
    return 1
  fi
  [ -z "$paths" ] || printf '%s\n' "$paths"
}

dd_package_path() {
  dd_require_android_user || return 1
  dd_package_path_for_user "$1" "$DD_ANDROID_USER"
}

# Enumerate every user before invoking host-side `adb uninstall`, which has no
# user selector. A clean-install benchmark cannot preserve another profile's APK
# code while replacing it for the selected user, so the intentionally simple and
# safe contract is to refuse multi-profile ownership and ask for a test device.
dd_android_users() {
  local raw status users
  raw=$("$ADB" shell "pm list users 2>&1; echo __dd_rc=\$?" 2>/dev/null | tr -d '\r') || true
  status=$(printf '%s\n' "$raw" | sed -n 's/^__dd_rc=//p' | tail -1)
  case "$status" in
    0) ;;
    ''|*[!0-9]*)
      echo "FATAL: cannot enumerate Android users: the device shell reported no exit status." >&2
      return 1 ;;
    *)
      echo "FATAL: 'pm list users' failed with exit $status." >&2
      return 1 ;;
  esac
  users=$(printf '%s\n' "$raw" | sed -n 's/.*UserInfo{\([0-9][0-9]*\):.*/\1/p')
  if [ -z "$users" ]; then
    echo "FATAL: 'pm list users' reported no parseable Android users." >&2
    return 1
  fi
  printf '%s\n' "$users"
}

dd_refuse_other_user_installations() {
  local pkg="$1" users user paths others="" selected_seen=0
  dd_require_android_user || return 1
  users=$(dd_android_users) || return 1
  for user in $users; do
    if [ "$user" = "$DD_ANDROID_USER" ]; then
      selected_seen=1
      continue
    fi
    paths=$(dd_package_path_for_user "$pkg" "$user") || return 1
    [ -z "$paths" ] || others="${others}${others:+ }$user"
  done
  if [ "$selected_seen" != 1 ]; then
    echo "FATAL: selected Android user $DD_ANDROID_USER is absent from 'pm list users'." >&2
    return 1
  fi
  if [ -n "$others" ]; then
    echo "FATAL: package '$pkg' is also installed for Android user(s): $others." >&2
    echo "       Refusing global 'adb uninstall', which would delete their app data." >&2
    echo "       Use a dedicated test device or remove the package from those users first." >&2
    return 1
  fi
}

# Establish the clean-install precondition instead of trusting `adb uninstall`'s
# exit status. A protected/device-admin package can reject removal, after which
# `install -r` preserves its data, permissions, caches and profile state while APK
# byte attestation still passes. If the selected user has no package, there is
# nothing to remove; otherwise the package path must disappear before continuing.
dd_ensure_uninstalled() {
  local pkg="$1" before after uninstall_output="" uninstall_rc=0
  dd_refuse_other_user_installations "$pkg" || return 1
  before=$(dd_package_path "$pkg") || return 1
  [ -n "$before" ] || return 0
  uninstall_output=$("$ADB" uninstall "$pkg" 2>&1) || uninstall_rc=$?
  after=$(dd_package_path "$pkg") || return 1
  if [ -n "$after" ]; then
    echo "FATAL: package '$pkg' remains installed for Android user $DD_ANDROID_USER" >&2
    echo "       after uninstall (exit $uninstall_rc): ${uninstall_output:-no message}" >&2
    echo "       Refusing 'install -r': it would preserve app data, caches and profiles." >&2
    return 1
  fi
}

# Read one numeric Android setting before any mutation. Empty output, `null`, a
# malformed value and command failure are all different from a restorable state:
# guessing a default can leave a borrowed device changed after the run.
dd_snapshot_numeric_setting() {
  local namespace="$1" key="$2" value
  # No pipe: with one, the exit status is `tr`'s unless the caller happens to run
  # `set -o pipefail`, so a dead adb was reported as a malformed value -- the
  # message asserting more than the check could distinguish.
  if ! value=$("$ADB" shell "settings get $namespace $key" 2>/dev/null); then
    echo "FATAL: cannot snapshot Android setting $namespace/$key: the command failed." >&2
    echo "       Check the device is attached and authorized." >&2
    return 1
  fi
  value=$(printf '%s' "$value" | tr -d '\r')
  if ! awk -v value="$value" 'BEGIN {
    numeric = (value ~ /^([0-9]+([.][0-9]*)?|[.][0-9]+)$/)
    exit !numeric
  }'; then
    echo "FATAL: cannot snapshot Android setting $namespace/$key:" >&2
    echo "       expected a concrete numeric value, got '${value:-empty}'." >&2
    echo "       Refusing to mutate device state without a restorable snapshot." >&2
    echo "       A device that has never set this key reports 'null'; writing it once" >&2
    echo "       ('adb shell settings put $namespace $key <value>') makes the state" >&2
    echo "       restorable, and that value is what the run will put back." >&2
    return 1
  fi
  printf '%s\n' "$value"
}

# Radio snapshot. Same gate, with the one exception the animation and screen
# settings do not have: a device can simply not have the setting -- a Wi-Fi-only
# tablet has no `mobile_data` -- and `dd_apply_radio_state` already decides that
# case against ALLOW_UNVERIFIED_RADIOS. Failing closed here first would make the
# recovery its own abort message prints ("re-run with ALLOW_UNVERIFIED_RADIOS=1")
# impossible to follow. Both restore paths act only on a literal 0 or 1, so an
# unreadable snapshot restores nothing, which is right for a setting that is
# not there to restore.
dd_snapshot_radio_setting() {
  local namespace="$1" key="$2" value
  if value=$(dd_snapshot_numeric_setting "$namespace" "$key" 2>/dev/null); then
    printf '%s\n' "$value"
    return 0
  fi
  if [ "${ALLOW_UNVERIFIED_RADIOS:-0}" = 1 ]; then
    echo "WARNING: ALLOW_UNVERIFIED_RADIOS=1 -- $namespace/$key is unreadable." >&2
    echo "         Nothing will be restored for it: no prior value was observed." >&2
    return 0
  fi
  echo "FATAL: cannot snapshot Android setting $namespace/$key." >&2
  echo "       A device that has no such setting (a Wi-Fi-only tablet has no" >&2
  echo "       mobile_data) is accepted with ALLOW_UNVERIFIED_RADIOS=1, which also" >&2
  echo "       relaxes the post-'svc' read-back. Check the radio state by hand first." >&2
  return 1
}

# Apply and verify all animation scales that define the rendering scenario.
# Android stores them as numeric strings (`0`, `0.0`, `1.0` depending on build),
# so compare numeric equivalence while rejecting null, empty and malformed state.
dd_apply_animation_scales() {
  local expected="$1" scale actual
  case "$expected" in
    0|1) ;;
    *) echo "FATAL: animation scale must be 0 or 1 (got '$expected')." >&2; return 1 ;;
  esac
  for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
    if ! "$ADB" shell settings put global "$scale" "$expected" >/dev/null 2>&1; then
      echo "FATAL: device rejected animation setting '$scale=$expected'." >&2
      return 1
    fi
  done
  for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
    actual=$("$ADB" shell settings get global "$scale" 2>/dev/null | tr -d '\r') || actual=""
    if ! awk -v actual="$actual" -v expected="$expected" 'BEGIN {
      numeric = (actual ~ /^([0-9]+([.][0-9]*)?|[.][0-9]+)$/)
      exit !(numeric && actual + 0 == expected + 0)
    }'; then
      echo "FATAL: animation setting '$scale' read back as '${actual:-missing}'," >&2
      echo "       expected numeric value $expected. Refusing a mislabeled scenario." >&2
      return 1
    fi
  done
}

# Atomically reserve every evidence path as one set. Bash noclobber turns the
# redirection into an exclusive create; checking `[ ! -e ]` first would leave a
# race where two runs pass the check and then append/truncate the same files.
dd_reserve_output_files() {
  local path failed=""
  local -a reserved=()
  [ "$#" -gt 0 ] || { echo "FATAL: no output paths were provided for reservation." >&2; return 1; }
  for path in "$@"; do
    if (set -o noclobber; : > "$path") 2>/dev/null; then
      reserved+=("$path")
    else
      failed="$path"
      break
    fi
  done
  if [ -n "$failed" ]; then
    # `${reserved[@]+...}`, not `"${reserved[@]}"`: the callers run `set -u`, and
    # in bash 3.2 -- still /bin/bash on every macOS host -- expanding an empty
    # array under `set -u` is an unbound-variable error. That is exactly the case
    # where the FIRST path collided, so the abort message explaining which file
    # already exists was replaced by `reserved[@]: unbound variable`.
    for path in ${reserved[@]+"${reserved[@]}"}; do
      rm -f -- "$path"
    done
    echo "FATAL: refusing to overwrite or share output path '$failed'." >&2
    echo "       Another run may have started in the same second; retry with new names." >&2
    return 1
  fi
}

# Parse and validate the stable fields emitted by `am start -W` for a cold launch.
# Results are returned in globals so callers can include the parsed evidence in
# their own logs without each workflow maintaining a subtly different parser:
#   DD_LAUNCH_STATUS, DD_LAUNCH_STATE, DD_LAUNCH_TOTAL, DD_LAUNCH_ERROR
dd_validate_cold_launch_output() {
  local out
  out=$(printf '%s' "$1" | tr -d '\r')
  DD_LAUNCH_STATUS=$(printf '%s\n' "$out" | awk -F': *' '/^Status/{print $2; exit}')
  DD_LAUNCH_STATE=$(printf '%s\n' "$out" | awk -F': *' '/^LaunchState/{print $2; exit}')
  DD_LAUNCH_TOTAL=$(printf '%s\n' "$out" | awk -F': *' '/^TotalTime/{print $2; exit}')
  DD_LAUNCH_ERROR=""
  if [ "$DD_LAUNCH_STATUS" != ok ]; then
    DD_LAUNCH_ERROR="Status='${DD_LAUNCH_STATUS:-missing}', not ok"
  elif [ "$DD_LAUNCH_STATE" != COLD ]; then
    DD_LAUNCH_ERROR="LaunchState='${DD_LAUNCH_STATE:-missing}', not COLD"
  else
    case "$DD_LAUNCH_TOTAL" in
      ''|*[!0-9]*) DD_LAUNCH_ERROR="invalid TotalTime '${DD_LAUNCH_TOTAL:-missing}'" ;;
    esac
  fi
  [ -z "$DD_LAUNCH_ERROR" ]
}

# Name of the currently resumed activity, as "pkg/component".
#
# The obvious `grep mResumedActivity` does NOT work everywhere: Android 12 on
# some devices (observed on a Motorola moto g60s, SDK 31) prints the record as
# `ResumedActivity:` inside the Task dump with no `m` prefix, so the anchored
# pattern matches zero lines and every foreground assertion reports OTHER.
# Match both spellings, and fall back to the window manager's focused app.
# Every PID the package owns: the default process AND private processes
# (`<pkg>:name`). `pidof <pkg>` matches the exact process name only, so on an app
# whose launcher or Datadog initialization lives in `<pkg>:startup` it either finds
# nothing -- and the caller concludes the app never started -- or finds the default
# process and inspects the wrong one. Every liveness gate in this harness needs the
# whole set, for the same reason verify_trace.py searches colon-suffixed processes.
#
# `ps -A -o PID -o NAME` is the reliable enumeration (toybox ps, API 26+). The
# `pidof` fallback covers older devices, where it at least finds the default process.
dd_pkg_pids() {
  local pkg="$1" out pids
  out=$("$ADB" shell "ps -A -o PID -o NAME" 2>/dev/null | tr -d '\r') || true
  pids=$(printf '%s\n' "$out" \
         | awk -v p="$pkg" '$2 == p || index($2, p ":") == 1 {print $1}') || true
  if [ -z "$pids" ]; then
    pids=$("$ADB" shell pidof "$pkg" 2>/dev/null | tr -d '\r' | tr ' ' '\n') || true
  fi
  printf '%s\n' "$pids" | grep -E '^[0-9]+$' || true
}

# Count `datadog-*` threads across every PID given. The SDK always creates one
# during Datadog.initialize() (CoreFeature.setupExecutors submits an NTP task
# immediately), so this is the harness's liveness oracle. Reading
# /proc/<pid>/task/*/comm is the cheapest reliable probe.
dd_datadog_threads() {
  local pid names all=""
  for pid in $1; do
    names=$("$ADB" shell "cat /proc/$pid/task/*/comm 2>/dev/null" | tr -d '\r') || true
    all="$all$names
"
  done
  printf '%s' "$all" | grep -c '^datadog-' || true
}

# Grant every runtime permission declared by an installed package.
#
# All device workflows must launch the app from the same permission state. Keeping
# this in one helper prevents the standalone liveness verifier from silently testing
# a fresh-install permission-dialog path while the benchmark and trace measure the
# returning-user, permissions-already-decided path.
#
# Results are returned in globals so callers can both report the grant count and
# revoke exactly the permissions this invocation granted from their EXIT traps:
#   DD_RUNTIME_PERMISSION_COUNT  number declared
#   DD_GRANTED_PERMISSION_COUNT  number initially or newly granted
#   DD_GRANTED_PERMISSIONS       space-separated false-to-true grants owned by this run
#   DD_INITIAL_GRANTED_PERMISSIONS space-separated permissions already granted
#   DD_UNGRANTED_PERMISSIONS     space-separated rejected grants
#   DD_EFFECTIVE_GRANTED_PERMISSIONS canonical space-separated effective grants
#   DD_PERMISSION_STATE_ID       md5 of the effective granted/denied sets
dd_grant_runtime_permissions() {
  local pkg="$1" p package_dump permission_rows unscoped_rows perms pending next progress
  dd_require_android_user || return 1
  # shellcheck disable=SC2034  # public result globals consumed by sourcing callers
  DD_RUNTIME_PERMISSION_COUNT=0
  DD_GRANTED_PERMISSION_COUNT=0
  DD_GRANTED_PERMISSIONS=""
  DD_INITIAL_GRANTED_PERMISSIONS=""
  DD_UNGRANTED_PERMISSIONS=""
  DD_EFFECTIVE_GRANTED_PERMISSIONS=""
  DD_PERMISSION_STATE_ID=""
  package_dump=$("$ADB" shell dumpsys package "$pkg" 2>/dev/null | tr -d '\r') || package_dump=""
  if ! printf '%s\n' "$package_dump" | awk -v target="$DD_ANDROID_USER" '
      /^[[:space:]]*User [0-9]+:/ {
        user=$0; sub(/^[[:space:]]*User /, "", user); sub(/:.*/, "", user)
        if (user == target) found=1
      }
      END { exit !found }
    '; then
    echo "FATAL: package '$pkg' has no state for Android user $DD_ANDROID_USER." >&2
    return 1
  fi
  # Parse the entry structure, not a naming convention, and only inside the
  # selected user's section. A work profile can hold the same permission with
  # the opposite state; mixing both makes a no-op grant look owned by this run.
  permission_rows=$(printf '%s\n' "$package_dump" \
          | awk -v target="$DD_ANDROID_USER" '
              /^[[:space:]]*User [0-9]+:/ {
                user=$0; sub(/^[[:space:]]*User /, "", user); sub(/:.*/, "", user)
                selected=(user == target); runtime=0; next
              }
              selected && /^[[:space:]]*runtime permissions:/ { runtime=1; next }
              selected && /^[^[:space:]]/ { exit }
              selected && runtime {
                count=split($0, fields, /:[[:space:]]*granted=/)
                if (count >= 2 && fields[2] ~ /^(true|false)(,|[[:space:]]*$)/) {
                  name=fields[1]; sub(/^[[:space:]]+/, "", name)
                  state=fields[2]; sub(/,.*/, "", state); sub(/[[:space:]]+$/, "", state)
                  print name "\t" state
                }
              }' \
          | sort -u) || true
  # A layout this parser does not understand must never read as "the app declares
  # no runtime permissions": that outcome grants nothing, aborts nothing, and
  # reports 0/0, putting the permission-dialog contamination back with no signal.
  # Count the runtime-permission rows in the dump with no user scoping at all --
  # `install permissions:` rows are deliberately excluded, since only runtime ones
  # are grantable -- and refuse when the scoped parse found none while they exist.
  # Observed on a shared-UID system package whose per-user block is separated from
  # its `runtime permissions:` list by a top-level section.
  unscoped_rows=$(printf '%s\n' "$package_dump" | awk '
      /^[[:space:]]*runtime permissions:/ { inblock=1; indent=match($0, /[^ ]/); next }
      inblock {
        here=match($0, /[^ ]/)
        if (here <= indent) { inblock=0 }
        else if ($0 ~ /:[[:space:]]*granted=(true|false)/) { n++ }
      }
      END { print n+0 }')
  if [ -z "$permission_rows" ] && [ "${unscoped_rows:-0}" -gt 0 ]; then
    echo "FATAL: found $unscoped_rows runtime-permission rows for '$pkg' but could not" >&2
    echo "       attribute any of them to Android user $DD_ANDROID_USER." >&2
    echo "       Continuing would silently grant nothing and report 0/0." >&2
    return 1
  fi
  perms=$(printf '%s\n' "$permission_rows" | awk -F '\t' '$2 == "false" {print $1}')
  DD_INITIAL_GRANTED_PERMISSIONS=$(
    printf '%s\n' "$permission_rows" | awk -F '\t' '$2 == "true" {print $1}' | tr '\n' ' '
  )
  DD_INITIAL_GRANTED_PERMISSIONS=${DD_INITIAL_GRANTED_PERMISSIONS% }
  # shellcheck disable=SC2034  # public result global consumed by sourcing callers
  DD_RUNTIME_PERMISSION_COUNT=$(printf '%s\n' "$permission_rows" | grep -c . || true)
  DD_GRANTED_PERMISSION_COUNT=$(
    printf '%s\n' "$permission_rows" | awk -F '\t' '$2 == "true" {n++} END {print n+0}'
  )
  # Some grants depend on another runtime permission. The sorted dumpsys output
  # can put a dependent permission first (for example BACKGROUND_LOCATION before
  # foreground location), so retry only failures after each pass that made
  # progress. A full pass with no success proves the remaining set is ungrantable.
  pending="$perms"
  while [ -n "$pending" ]; do
    next=""
    progress=0
    for p in $pending; do
      if "$ADB" shell pm grant --user "$DD_ANDROID_USER" "$pkg" "$p" >/dev/null 2>&1; then
        DD_GRANTED_PERMISSION_COUNT=$((DD_GRANTED_PERMISSION_COUNT + 1))
        DD_GRANTED_PERMISSIONS="${DD_GRANTED_PERMISSIONS}${DD_GRANTED_PERMISSIONS:+ }$p"
        progress=1
      else
        next="${next}${next:+ }$p"
      fi
    done
    [ -n "$next" ] || break
    if [ "$progress" -eq 0 ]; then
      DD_UNGRANTED_PERMISSIONS="$next"
      break
    fi
    pending="$next"
  done
  # Application behavior sees the effective result, not whether a permission was
  # granted at install or by this helper. Canonicalize both sets before hashing so
  # grant order and dependency retries cannot change the run identity.
  DD_EFFECTIVE_GRANTED_PERMISSIONS=$(
    printf '%s\n' "$DD_INITIAL_GRANTED_PERMISSIONS $DD_GRANTED_PERMISSIONS" \
      | tr ' ' '\n' | grep . | sort -u | tr '\n' ' '
  ) || true
  DD_EFFECTIVE_GRANTED_PERMISSIONS=${DD_EFFECTIVE_GRANTED_PERMISSIONS% }
  DD_UNGRANTED_PERMISSIONS=$(
    printf '%s\n' "$DD_UNGRANTED_PERMISSIONS" \
      | tr ' ' '\n' | grep . | sort -u | tr '\n' ' '
  ) || true
  DD_UNGRANTED_PERMISSIONS=${DD_UNGRANTED_PERMISSIONS% }
  # shellcheck disable=SC2034  # public result global consumed by sourcing callers
  DD_PERMISSION_STATE_ID=$(dd_md5_str \
    "granted=$DD_EFFECTIVE_GRANTED_PERMISSIONS;denied=$DD_UNGRANTED_PERMISSIONS")
  if [ -n "$DD_UNGRANTED_PERMISSIONS" ]; then
    # Not every declared runtime permission is grantable: a hard-restricted one
    # (SMS, call-log, some location) is refused for any app that is not the
    # exempt role holder, and `pm grant` fails no matter how many passes it gets.
    # Failing closed is right -- a partially granted app is not the
    # permissions-already-decided scenario -- but refusing with no way through
    # would make the harness simply unusable on such an app, and every other
    # fail-closed gate here (ALLOW_UNVERIFIED_PKG, ALLOW_NO_DISPLAYED_MARKER,
    # ALLOW_MISSING_LAUNCH_MARKER, ALLOW_UNVERIFIED_RADIOS) ships a named override.
    case "${ALLOW_PARTIAL_PERMISSIONS:-0}" in
      0) ;;
      1)
        echo "WARNING: ALLOW_PARTIAL_PERMISSIONS=1 -- continuing with only" >&2
        echo "         $DD_GRANTED_PERMISSION_COUNT/$DD_RUNTIME_PERMISSION_COUNT runtime permissions granted." >&2
        echo "         ungrantable: $DD_UNGRANTED_PERMISSIONS" >&2
        echo "         A dialog for these can still appear mid-run. Nothing here prevents that;" >&2
        echo "         the per-launch foreground gate is what would catch it, and it aborts." >&2
        return 0 ;;
      *)
        echo "FATAL: ALLOW_PARTIAL_PERMISSIONS must be 0 or 1 (got '$ALLOW_PARTIAL_PERMISSIONS')." >&2
        return 1 ;;
    esac
    echo "FATAL: failed to grant all runtime permissions declared by '$pkg'." >&2
    echo "       rejected: $DD_UNGRANTED_PERMISSIONS" >&2
    echo "       A partially granted app is not the permissions-already-decided scenario." >&2
    echo "       If these are hard-restricted permissions this app can never hold, set" >&2
    echo "       ALLOW_PARTIAL_PERMISSIONS=1 to accept the weaker guarantee." >&2
    return 1
  fi
}

# Numeric UID of an installed package, refusing legacy shared-UID installs.
# `logcat --uid` is the only filter available before the app process exists, which
# is when the trace endpoint watcher must start. A shared UID would admit logs from
# the other package too, so it cannot support an attributable app-owned metric.
dd_unique_pkg_uid() {
  local pkg="$1" out uid owners count
  dd_require_android_user || return 1
  out=$("$ADB" shell pm list packages -U --user "$DD_ANDROID_USER" 2>/dev/null | tr -d '\r') || true
  uid=$(printf '%s\n' "$out" | awk -v p="package:$pkg" '
    $1 == p {
      for (i = 2; i <= NF; i++) if ($i ~ /^uid:[0-9]+$/) {
        sub(/^uid:/, "", $i); print $i; exit
      }
    }')
  case "$uid" in
    ''|*[!0-9]*)
      echo "FATAL: cannot resolve a numeric UID for installed package '$pkg'." >&2
      return 1 ;;
  esac
  owners=$(printf '%s\n' "$out" | awk -v u="uid:$uid" '
    {
      for (i = 2; i <= NF; i++) if ($i == u) {
        sub(/^package:/, "", $1); print $1
      }
    }')
  count=$(printf '%s\n' "$owners" | grep -c . || true)
  if [ "$count" -ne 1 ]; then
    echo "FATAL: package '$pkg' shares UID $uid with: $(printf '%s' "$owners" | tr '\n' ' ')" >&2
    echo "       APP_TRACE_REGEX cannot be attributed to one package on a shared UID." >&2
    return 1
  fi
  printf '%s\n' "$uid"
}

# Verify the device logcat can filter from process birth by numeric package UID.
dd_logcat_supports_uid_filter() {
  local help
  help=$("$ADB" shell logcat --help 2>&1) || true
  [ "$(printf '%s\n' "$help" | grep -c -- '--uid' || true)" -gt 0 ]
}

dd_top_activity() {
  local top
  top=$("$ADB" shell dumpsys activity activities 2>/dev/null \
        | grep -m1 -E '(^|[^a-zA-Z])m?ResumedActivity[:=]' \
        | grep -oE '[a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+' | head -1 | tr -d '\r') || true
  if [ -z "$top" ]; then
    top=$("$ADB" shell dumpsys window 2>/dev/null \
          | grep -m1 'mFocusedApp=' \
          | grep -oE '[a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+' | head -1 | tr -d '\r') || true
  fi
  printf '%s' "$top"
}

# Read a logcat buffer from stdin and print the first foreign activity that
# reached first draw. Callers establish the pre-launch boundary by clearing and
# verifying logcat after the force-stop settle; every Displayed line visible here
# is therefore conservatively inside the guarded window, even when it precedes
# the target package's own first frame.
dd_first_foreign_displayed_activity() {
  local pkg="$1"
  grep -oE "ActivityTaskManager: Displayed [a-zA-Z0-9_.]+/[^ :]+" \
    | awk -v p="$pkg/" 'index($3,p)!=1 && !found {print $3; found=1}'
}

# Refuse to measure behind a lockscreen. A locked device still RESUMES the
# activity -- `ResumedActivity` names the app correctly -- but no frame is ever
# drawn, so `am start -W` reports no TotalTime and LaunchState=UNKNOWN. Without
# this check the run dies later with a misleading "not fully reaped" message.
dd_require_unlocked() {
  local locked focus attempt
  # The notification shade and the always-on display are not locks -- they are
  # just windows sitting on top, and either can be left behind by an earlier
  # `input` command or by a notification arriving between runs. Clear them and
  # look again before failing, otherwise an unattended sequence of captures
  # loses every run after the first stray swipe.
  for attempt in 1 2; do
    locked=$("$ADB" shell dumpsys trust 2>/dev/null | grep -oE 'deviceLocked=[01]' | head -1 | cut -d= -f2 | tr -d '\r') || true
    focus=$("$ADB" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | tr -d '\r') || true
    case "$focus" in
      *Keyguard*|*NotificationShade*|*AOD*|*DreamActivity*) locked=1 ;;
    esac
    { [ "${locked:-0}" = "1" ] && [ "$attempt" = 1 ]; } || break
    "$ADB" shell cmd statusbar collapse >/dev/null 2>&1 || true
    "$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    "$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true
    sleep 2
  done
  if [ "${locked:-0}" = "1" ]; then
    echo "FATAL: the device is locked, or the lockscreen/shade is on top." >&2
    echo "  A locked device still resumes the activity, but never draws a frame, so" >&2
    echo "  'am start -W' returns no TotalTime and LaunchState=UNKNOWN. Every launch" >&2
    echo "  would be unmeasurable." >&2
    echo "  Fix: unlock the phone by hand and leave it on the home screen. If it has a" >&2
    echo "  PIN/pattern/password, adb cannot dismiss it ('wm dismiss-keyguard' only" >&2
    echo "  works for a swipe-only lock)." >&2
    echo "  Current focus: ${focus:-unknown}" >&2
    return 1
  fi
  return 0
}

# A Python that can actually import `perfetto`. The trace scripts need it, and the
# usual failure is invisible: `verify_trace.py`'s shebang is `#!/usr/bin/env python3`,
# i.e. the SYSTEM interpreter, while the documented install puts `perfetto` in a
# local venv. Prefer the venv next to the scripts, then $PY, then anything on PATH
# that can import it.
dd_resolve_python() {
  local here c
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  for c in "${PY:-}" "$here/.venv/bin/python" "$here/.venv/bin/python3" \
           "$(command -v python3 2>/dev/null)" "$(command -v python 2>/dev/null)"; do
    [ -n "$c" ] && [ -x "$c" ] || continue
    if "$c" -c 'import perfetto' >/dev/null 2>&1; then PY="$c"; export PY; return 0; fi
  done
  echo "FATAL: no Python with the 'perfetto' package. Install it, then re-run:" >&2
  echo "    python3 -m venv $here/.venv && $here/.venv/bin/pip install perfetto" >&2
  echo "  (verify_trace.py's shebang is the system python3, which will not see a venv," >&2
  echo "   so pass the venv interpreter explicitly when running it by hand.)" >&2
  return 1
}

# md5 of a local file, portable between macOS and Linux.
dd_md5() {
  [ -f "$1" ] || { echo "FATAL: file not found: $1" >&2; return 1; }
  if command -v md5 >/dev/null 2>&1; then md5 -q "$1"
  else md5sum "$1" | awk '{print $1}'; fi
}

# md5 of a STRING, for stamping the identity of a setting into a CSV header where
# the raw value cannot go: parse_meta() in ab_stats.py splits header tokens on
# whitespace, and APP_TRACE_REGEX contains spaces.
dd_md5_str() {
  if command -v md5 >/dev/null 2>&1; then printf '%s' "$1" | md5 -q
  else printf '%s' "$1" | md5sum | awk '{print $1}'; fi
}

# Fail unless exactly one device is attached AND authorized. Distinguishes the
# common failure modes instead of reporting a generic "no device".
dd_require_device() {
  local out n_auth n_unauth n_off
  out="$("$ADB" devices | tail -n +2 | grep -v '^$' || true)"
  # Honour ANDROID_SERIAL, which adb itself respects: a Linux CI box or a
  # workstation with an emulator running alongside a phone is a normal setup.
  if [ -n "${ANDROID_SERIAL:-}" ]; then
    if printf '%s\n' "$out" | grep -qE "^${ANDROID_SERIAL}[[:space:]]+device$"; then
      return 0
    fi
    echo "FATAL: ANDROID_SERIAL='$ANDROID_SERIAL' is not attached and authorized." >&2
    printf '%s\n' "$out" >&2
    return 1
  fi
  n_auth=$(echo "$out"   | grep -cE '[[:space:]]device$'       || true)
  n_unauth=$(echo "$out" | grep -cE '[[:space:]]unauthorized$' || true)
  n_off=$(echo "$out"    | grep -cE '[[:space:]]offline$'      || true)

  if [ "$n_unauth" -gt 0 ]; then
    echo "FATAL: device attached but UNAUTHORIZED." >&2
    echo "  Unlock the phone; accept the 'Allow USB debugging?' prompt and tick" >&2
    echo "  'Always allow from this computer'. If no prompt appears:" >&2
    echo "    $ADB kill-server && $ADB start-server && $ADB devices" >&2
    echo "  (or revoke old keys: Developer options -> Revoke USB debugging authorisations)" >&2
    return 1
  fi
  [ "$n_off" -eq 0 ] || { echo "FATAL: device is offline; replug the cable." >&2; return 1; }
  if [ "$n_auth" -ne 1 ]; then
    echo "FATAL: need exactly one authorized device, found $n_auth." >&2
    echo "  Set ANDROID_SERIAL=<serial> to pick one." >&2
    "$ADB" devices -l >&2
    return 1
  fi
  return 0
}

# Put the radios into the requested state and PROVE they got there.
#
# `svc` is best-effort and silently no-ops without root -- `svc data disable` needs
# root on most retail devices -- so without a read-back a run can be stamped
# airplane=1 while mobile data is still up. That is not merely noisy, it is
# mislabeled against the requested Wi-Fi/mobile-radio-off scenario.
#
# Shared because BOTH the benchmark and trace need the same controlled radio state.
# $1 = 1 for Wi-Fi/mobile off, 0 for at least one enabled. BOTH directions are
# enforced. This deliberately does not claim association, internet validation, DNS
# or endpoint reachability; those are operator-controlled properties a generic probe
# cannot establish for every app and SDK destination or for the whole run.
dd_apply_radio_state() {
  local want="$1"
  if [ "$want" = 1 ]; then
    "$ADB" shell svc wifi disable >/dev/null 2>&1 || true
    "$ADB" shell svc data disable >/dev/null 2>&1 || true
  else
    "$ADB" shell svc wifi enable >/dev/null 2>&1 || true
  fi
  sleep 2
  local w d
  w=$("$ADB" shell settings get global wifi_on 2>/dev/null | tr -d '\r') || true
  d=$("$ADB" shell settings get global mobile_data 2>/dev/null | tr -d '\r') || true
  if [ "$want" = 1 ]; then
    # Require an explicit 0. Rejecting only the literal 1 accepted `null`, an empty
    # readback and any unsupported value as "verified off", so a device whose
    # `settings get` is unreadable -- exactly the device whose `svc` call is most
    # likely to have silently no-op'd -- produced a CSV stamped airplane=1 with a
    # radio still up. "Not provably off" is not "off".
    local bad=""
    case "$w" in 0) ;; 1) bad="Wi-Fi is still ON (wifi_on=1)" ;;
                    *) bad="Wi-Fi state is INDETERMINATE (wifi_on='${w:-<empty>}')" ;; esac
    case "$d" in 0) ;; 1) bad="${bad:+$bad; }mobile data is still ON (mobile_data=1)" ;;
                    *) bad="${bad:+$bad; }mobile data state is INDETERMINATE (mobile_data='${d:-<empty>}')" ;; esac
    if [ -n "$bad" ]; then
      # A literal 1 is never overridable: the radio is demonstrably up.
      if [ "$w" != 1 ] && [ "$d" != 1 ] && [ "${ALLOW_UNVERIFIED_RADIOS:-0}" = "1" ]; then
        echo "[WARNING: ALLOW_UNVERIFIED_RADIOS=1 -- $bad]" >&2
        echo "[         This run will be stamped airplane=1 WITHOUT proof both radios were off.]" >&2
        return 0
      fi
      echo "FATAL: AIRPLANE=1 but the radios are not provably off: $bad" >&2
      echo "       'svc wifi/data disable' is best-effort and needs root on most retail" >&2
      echo "       devices, so without a clean read-back this run would be stamped" >&2
      echo "       airplane=1 while a radio was still up -- a mislabeled radio scenario." >&2
      echo "       Turn Wi-Fi and mobile data off by hand." >&2
      echo "       A device that reports 'null' may simply not have the setting (a" >&2
      echo "       Wi-Fi-only tablet has no mobile_data): check by hand, then re-run with" >&2
      echo "       ALLOW_UNVERIFIED_RADIOS=1 to accept an indeterminate read-back." >&2
      return 1
    fi
    echo "[radios verified OFF (wifi_on=$w mobile_data=$d)]" >&2
  else
    # Symmetric to the AIRPLANE=1 branch: `airplane` is in ab_stats.py's
    # _MUST_MATCH, so the requested enabled-radio state must be proved too. `svc
    # wifi enable` can silently no-op. Require at least one setting to read back a
    # literal 1, without treating that as proof of association or reachability.
    if [ "$w" != 1 ] && [ "$d" != 1 ]; then
      local why
      if [ "$w" = 0 ] && [ "$d" = 0 ]; then
        why="both radios read back OFF (wifi_on=0 mobile_data=0)"
      else
        why="no radio is provably on (wifi_on='${w:-<empty>}' mobile_data='${d:-<empty>}')"
      fi
      if [ "${ALLOW_UNVERIFIED_RADIOS:-0}" = "1" ]; then
        echo "[WARNING: ALLOW_UNVERIFIED_RADIOS=1 -- $why]" >&2
        echo "[         This run will be stamped airplane=0 WITHOUT proof a radio was enabled.]" >&2
        return 0
      fi
      echo "FATAL: AIRPLANE=0 (radio-enabled scenario) but $why." >&2
      echo "       The CSV would claim an enabled Wi-Fi/mobile-radio scenario without" >&2
      echo "       proving either setting. Enable one by hand and re-run." >&2
      echo "       For an unrepresented transport (Ethernet, USB tethering, or an emulator" >&2
      echo "       whose settings are unreadable), inspect it by hand and re-run with" >&2
      echo "       ALLOW_UNVERIFIED_RADIOS=1. Reachability is never inferred here." >&2
      return 1
    fi
    echo "[radio setting verified ON (wifi_on=${w:-<empty>} mobile_data=${d:-<empty>}); reachability unverified]" >&2
  fi
  return 0
}
