#!/usr/bin/env python3
"""Regression tests for cross-script cold-start harness contracts."""

import importlib.util
import os
import re
import shutil
from pathlib import Path
import stat
import subprocess
import tempfile
import textwrap
import unittest


HARNESS = Path(__file__).resolve().parents[1]
LIB = HARNESS / "lib.sh"
AB_STATS = HARNESS / "ab_stats.py"


def load_ab_stats_module():
    spec = importlib.util.spec_from_file_location("coldstart_ab_stats", AB_STATS)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot import {AB_STATS}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class HarnessRegressionTests(unittest.TestCase):
    def run_with_fake_adb(self, bash: str, adb: str) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as tmp:
            fake_adb = Path(tmp) / "adb"
            fake_adb.write_text(textwrap.dedent(adb), encoding="utf-8")
            fake_adb.chmod(fake_adb.stat().st_mode | stat.S_IXUSR)
            env = os.environ.copy()
            env.update(
                ADB=str(fake_adb),
                DD_ANDROID_USER="0",
                LIB=str(LIB),
                FAKE_ADB_STATE=str(Path(tmp) / "adb-state"),
            )
            return subprocess.run(
                ["bash", "-c", bash],
                check=False,
                capture_output=True,
                text=True,
                env=env,
            )

    def available_bashes(self) -> list[str]:
        """Every distinct bash on this host, so the oldest one is also exercised.

        macOS ships bash 3.2 at /bin/bash while developers usually run a 5.x from
        Homebrew first on PATH. Testing only `bash` would leave the version the
        harness actually runs under on a stock Mac untested.
        """
        shells = []
        for candidate in ("bash", "/bin/bash"):
            path = shutil.which(candidate)
            if path and os.path.realpath(path) not in shells:
                shells.append(os.path.realpath(path))
        self.assertTrue(shells, "no bash found on this host")
        return shells

    def test_aapt2_discovery_skips_a_stale_android_home(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            stale = root / "stale-sdk"
            valid = root / "valid-sdk"
            (stale / "build-tools" / "34.0.0").mkdir(parents=True)
            aapt2 = valid / "build-tools" / "35.0.1" / "aapt2"
            aapt2.parent.mkdir(parents=True)
            (valid / "build-tools" / "36.0.0").mkdir()
            aapt2.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            aapt2.chmod(aapt2.stat().st_mode | stat.S_IXUSR)
            env = os.environ.copy()
            env.update(
                ADB="/usr/bin/true",
                ANDROID_HOME=str(stale),
                ANDROID_SDK_ROOT=str(valid),
                LIB=str(LIB),
            )
            env.pop("AAPT2", None)
            result = subprocess.run(
                [
                    "bash",
                    "-c",
                    '. "$LIB"; dd_resolve_tools; printf "%s|%s\\n" "$AAPT2" "$ANDROID_HOME"',
                ],
                check=False,
                capture_output=True,
                text=True,
                env=env,
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), f"{aapt2}|{valid}")

    def test_animation_scale_helper_accepts_numeric_equivalent_readback(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_apply_animation_scales 0
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell settings put global "*) exit 0 ;;
              "shell settings get global window_animation_scale") printf '0\n' ;;
              "shell settings get global transition_animation_scale") printf '0.0\n' ;;
              "shell settings get global animator_duration_scale") printf '00.000\n' ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_animation_scale_helper_rejects_an_ignored_write(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_apply_animation_scales 0
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell settings put global "*) exit 0 ;;
              "shell settings get global window_animation_scale") printf '0.0\n' ;;
              "shell settings get global transition_animation_scale") printf '1.0\n' ;;
              "shell settings get global animator_duration_scale") printf '0.0\n' ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("transition_animation_scale", result.stderr)
        self.assertIn("read back as '1.0'", result.stderr)

    def test_background_dexopt_is_a_shared_fail_closed_precollection_gate(self) -> None:
        success = self.run_with_fake_adb(
            '. "$LIB"; dd_disable_background_dexopt; echo disabled',
            """
            #!/usr/bin/env bash
            [ "$*" = "shell cmd package bg-dexopt-job --disable" ]
            """,
        )
        self.assertEqual(success.returncode, 0, success.stderr)
        self.assertEqual(success.stdout.strip(), "disabled")

        failure = self.run_with_fake_adb(
            '. "$LIB"; dd_disable_background_dexopt',
            """
            #!/usr/bin/env bash
            exit 17
            """,
        )
        self.assertNotEqual(failure.returncode, 0)
        self.assertIn("cannot disable Android's background dexopt job", failure.stderr)

        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        gate = "dd_disable_background_dexopt || exit 2"
        self.assertEqual(benchmark.count(gate), 1)
        self.assertEqual(capture.count(gate), 1)
        main_pin = benchmark.rindex("\npin_device\n")
        first_block = benchmark.index("for ((b=1; b<=BLOCKS; b++))", main_pin)
        self.assertLess(main_pin, first_block)
        self.assertLess(capture.index(gate), capture.index('log "starting perfetto"'))

    def test_enabled_radio_readback_does_not_claim_reachability(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            sleep() { :; }
            . "$LIB"
            dd_apply_radio_state 0
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell svc wifi enable") exit 0 ;;
              "shell settings get global wifi_on") printf '1\n' ;;
              "shell settings get global mobile_data") printf '0\n' ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("reachability unverified", result.stderr)
        self.assertNotIn("verified online", result.stderr.lower())

        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        self.assertNotIn("echo ONLINE", benchmark)

    def test_benchmark_and_trace_share_animation_readback_gate(self) -> None:
        for name in ("coldstart_bench.sh", "capture_trace.sh"):
            source = (HARNESS / name).read_text(encoding="utf-8")
            self.assertIn('dd_apply_animation_scales "$ANIMATIONS" || exit 2', source, name)

    def test_numeric_setting_snapshot_accepts_a_restorable_value(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_snapshot_numeric_setting global window_animation_scale
            """,
            """
            #!/usr/bin/env bash
            printf '1.0\r\n'
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "1.0\n")

    def test_numeric_setting_snapshot_rejects_unrestorable_results(self) -> None:
        cases = {
            "command failure": "exit 1",
            "empty": "exit 0",
            "null": "printf 'null\\n'",
            "malformed": "printf 'enabled\\n'",
        }
        for label, behavior in cases.items():
            with self.subTest(label=label):
                result = self.run_with_fake_adb(
                    """
                    set -euo pipefail
                    . "$LIB"
                    dd_snapshot_numeric_setting global window_animation_scale
                    """,
                    f"""
                    #!/usr/bin/env bash
                    {behavior}
                    """,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("cannot snapshot Android setting", result.stderr)

    def test_device_mutators_snapshot_every_setting_through_the_shared_gate(self) -> None:
        for name in ("coldstart_bench.sh", "capture_trace.sh"):
            source = (HARNESS / name).read_text(encoding="utf-8")
            self.assertEqual(source.count("dd_snapshot_numeric_setting"), 3, name)
            self.assertEqual(source.count("dd_snapshot_radio_setting"), 2, name)
            self.assertIn(
                "window_animation_scale transition_animation_scale "
                "animator_duration_scale",
                source,
                name,
            )
            for setting in ("stay_on_while_plugged_in", "screen_off_timeout"):
                self.assertRegex(
                    source,
                    rf"dd_snapshot_numeric_setting [^\n]*{setting}",
                    name,
                )
            for setting in ("wifi_on", "mobile_data"):
                self.assertRegex(
                    source,
                    rf"dd_snapshot_radio_setting [^\n]*{setting}",
                    name,
                )

    def test_radio_snapshot_keeps_the_documented_override_reachable(self) -> None:
        """A device with no `mobile_data` must still be runnable.

        `dd_apply_radio_state` decides that case against ALLOW_UNVERIFIED_RADIOS
        and its abort message tells the operator to re-run with it. A snapshot
        that failed closed on the same value first would make that instruction
        impossible to follow, and the setting a Wi-Fi-only tablet does not have
        is the exact case the override was written for.
        """
        adb = """
            #!/usr/bin/env bash
            case "$*" in
              *"settings get global mobile_data"*) printf 'null\n' ;;
              *"settings get global wifi_on"*) printf '1\n' ;;
              *) exit 1 ;;
            esac
        """
        refused = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_snapshot_radio_setting global mobile_data
            """,
            adb,
        )
        self.assertNotEqual(refused.returncode, 0)
        self.assertIn("ALLOW_UNVERIFIED_RADIOS=1", refused.stderr)

        allowed = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            ALLOW_UNVERIFIED_RADIOS=1
            printf 'wifi=[%s] data=[%s]\n' \
              "$(dd_snapshot_radio_setting global wifi_on)" \
              "$(dd_snapshot_radio_setting global mobile_data)"
            """,
            adb,
        )
        self.assertEqual(allowed.returncode, 0, allowed.stderr)
        self.assertIn("wifi=[1] data=[]", allowed.stdout)
        self.assertIn("Nothing will be restored for it", allowed.stderr)

    def test_numeric_snapshot_names_a_dead_shell_as_such(self) -> None:
        """The two failures must not be reported as each other.

        The classification cannot depend on the caller running `set -o pipefail`:
        with a pipe in the command substitution the status is `tr`'s, so a device
        that was not there at all was reported as having returned a malformed
        value.
        """
        result = self.run_with_fake_adb(
            """
            . "$LIB"
            dd_snapshot_numeric_setting global window_animation_scale
            """,
            """
            #!/usr/bin/env bash
            printf 'adb: device offline\n' >&2
            exit 1
            """,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("the command failed", result.stderr)
        self.assertNotIn("expected a concrete numeric value", result.stderr)

    def test_unset_setting_abort_names_the_way_out(self) -> None:
        result = self.run_with_fake_adb(
            """
            . "$LIB"
            dd_snapshot_numeric_setting global window_animation_scale
            """,
            """
            #!/usr/bin/env bash
            printf 'null\n'
            """,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("settings put global window_animation_scale", result.stderr)

    def test_trace_output_is_reserved_before_the_first_device_mutation(self) -> None:
        source = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        reserve = source.index('dd_reserve_output_files "$TRACE_FILE"')
        mutate = source.index('dd_apply_animation_scales "$ANIMATIONS"')
        pull = source.index('pull "$REMOTE_TRACE" "$TRACE_FILE"')
        release = source.index("_TRACE_RESERVED=0", pull)
        self.assertLess(reserve, mutate)
        self.assertLess(pull, release)
        self.assertIn('if [ "${_TRACE_RESERVED:-0}" = 1 ]; then', source)
        self.assertNotIn('[ ! -e "$TRACE_FILE" ]', source)

    def test_foreign_displayed_scan_covers_both_sides_of_the_app_first_frame(self) -> None:
        own = "ActivityTaskManager: Displayed com.example/.MainActivity: +100ms"
        own_next = "ActivityTaskManager: Displayed com.example/.HomeActivity: +120ms"
        foreign = (
            "ActivityTaskManager: Displayed "
            "com.android.permissioncontroller/.GrantPermissionsActivity: +80ms"
        )
        cases = (
            (f"{foreign}\n{own}\n", "com.android.permissioncontroller/"),
            (f"{own}\n{foreign}\n", "com.android.permissioncontroller/"),
            (f"{own}\n{own_next}\n", ""),
        )
        for logcat, expected in cases:
            with self.subTest(logcat=logcat):
                result = subprocess.run(
                    [
                        "bash",
                        "-c",
                        '. "$LIB"; dd_first_foreign_displayed_activity com.example',
                    ],
                    input=logcat,
                    check=False,
                    capture_output=True,
                    text=True,
                    env={**os.environ, "LIB": str(LIB)},
                )
                self.assertIn(expected, result.stdout)
                if not expected:
                    self.assertEqual(result.stdout, "")

    def test_foreground_log_boundary_is_after_force_stop_settle(self) -> None:
        source = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        measure = source.index("measure() {")
        force_stop = source.index("shell am force-stop", measure)
        settle = source.index("sleep 5", force_stop)
        clear = source.index("shell logcat -c", settle)
        launch = source.index('shell am start "${START_ARGS[@]}"', clear)
        self.assertLess(force_stop, settle)
        self.assertLess(settle, clear)
        self.assertLess(clear, launch)

    def test_absent_package_is_not_read_as_a_failed_query(self) -> None:
        """`pm path` exits 1 for a package that is not installed for this user.

        Verified on Android 12: absence prints nothing and exits 1, and `adb
        shell` forwards that 1 -- the same status a dead shell returns. Reading
        the two alike makes `dd_ensure_uninstalled` refuse the clean state it was
        asked to establish, which is the normal state before the first install
        and after every successful uninstall.
        """
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            DD_ANDROID_USER=0
            paths=$(dd_package_path com.example.app)
            printf 'paths=[%s]\n' "$paths"
            dd_ensure_uninstalled com.example.app
            printf 'ensure_rc=%s\n' "$?"
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              *"pm list users"*)
                printf 'Users:\n  UserInfo{0:Owner:c13}\n__dd_rc=0\n' ;;
              *"pm path"*) printf '__dd_rc=1\n'; exit 1 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("paths=[]", result.stdout)
        self.assertIn("ensure_rc=0", result.stdout)

    def test_package_path_refuses_a_shell_that_never_ran(self) -> None:
        result = self.run_with_fake_adb(
            """
            . "$LIB"
            DD_ANDROID_USER=0
            dd_package_path com.example.app
            printf 'rc=%s\n' "$?"
            """,
            """
            #!/usr/bin/env bash
            printf 'adb: device offline\n' >&2
            exit 1
            """,
        )
        self.assertIn("rc=1", result.stdout)
        self.assertIn("reported no exit status", result.stderr)

    def test_permission_scan_refuses_rows_it_cannot_attribute(self) -> None:
        """A layout the user-scoping cannot read must not report "declares none".

        Modeled on a real `dumpsys package` layout (a shared-UID system package
        on Android 12) where a top-level section sits between the package's
        `User 0:` line and the `runtime permissions:` list. The scoped parse
        returns nothing there; without this refusal the run grants nothing,
        aborts nothing, and logs 0/0.
        """
        result = self.run_with_fake_adb(
            """
            . "$LIB"
            DD_ANDROID_USER=0
            dd_grant_runtime_permissions com.example.app || printf 'rc=%s\n' "$?"
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'Packages:' \
                  '  Package [com.example.app] (abc):' \
                  '    User 0: ceDataInode=1 installed=true' \
                  '      overlay paths:' \
                  'Queries:' \
                  '  nothing' \
                  'Shared users:' \
                  '    User 0:' \
                  '      runtime permissions:' \
                  '        android.permission.CAMERA: granted=false, flags=[]' ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertIn("rc=1", result.stdout)
        self.assertIn("could not", result.stderr)
        self.assertIn("attribute", result.stderr)

    def test_output_reservation_creates_the_complete_pair(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "results.csv"
            log = Path(tmp) / "bench.log"
            result = subprocess.run(
                ["bash", "-c", '. "$LIB"; dd_reserve_output_files "$OUT" "$LOG"'],
                check=False,
                capture_output=True,
                text=True,
                env={**os.environ, "LIB": str(LIB), "OUT": str(out), "LOG": str(log)},
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(out.is_file())
            self.assertTrue(log.is_file())

    def test_output_reservation_rolls_back_its_files_on_collision(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "results.csv"
            log = Path(tmp) / "bench.log"
            log.write_text("existing evidence\n", encoding="utf-8")
            result = subprocess.run(
                ["bash", "-c", '. "$LIB"; dd_reserve_output_files "$OUT" "$LOG"'],
                check=False,
                capture_output=True,
                text=True,
                env={**os.environ, "LIB": str(LIB), "OUT": str(out), "LOG": str(log)},
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(out.exists())
            self.assertEqual(log.read_text(encoding="utf-8"), "existing evidence\n")
            self.assertIn("refusing to overwrite or share", result.stderr)

    def test_output_reservation_aborts_readably_when_the_first_path_collides(self) -> None:
        """The rollback loop runs with nothing reserved, under every available bash.

        This is not a duplicate of the collision test above: there the first path
        is created and only the second collides, so the rollback array is
        non-empty. When the FIRST path collides the array is empty, and under the
        `set -u` the callers use, bash 3.2 -- still /bin/bash on every macOS host
        -- treats expanding an empty array as an unbound-variable error. That
        replaced the message naming the file that already exists with
        `reserved[@]: unbound variable`.
        """
        for shell in self.available_bashes():
            with self.subTest(shell=shell):
                with tempfile.TemporaryDirectory() as tmp:
                    out = Path(tmp) / "results.csv"
                    log = Path(tmp) / "bench.log"
                    out.write_text("existing evidence\n", encoding="utf-8")
                    result = subprocess.run(
                        [
                            shell,
                            "-c",
                            'set -euo pipefail; . "$LIB"; '
                            'dd_reserve_output_files "$OUT" "$LOG"',
                        ],
                        check=False,
                        capture_output=True,
                        text=True,
                        env={
                            **os.environ,
                            "LIB": str(LIB),
                            "OUT": str(out),
                            "LOG": str(log),
                        },
                    )
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("refusing to overwrite or share", result.stderr)
                    self.assertNotIn("unbound variable", result.stderr)
                    self.assertFalse(log.exists())
                    self.assertEqual(
                        out.read_text(encoding="utf-8"), "existing evidence\n"
                    )

    def test_benchmark_reserves_outputs_before_device_access(self) -> None:
        source = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        reserve = source.index('dd_reserve_output_files "$OUT" "$LOG"')
        device = source.index("dd_require_device", reserve)
        self.assertLess(reserve, device)
        self.assertNotIn('app_trace_id=$APP_TRACE_ID" > "$OUT"', source)
        self.assertIn('app_trace_id=$APP_TRACE_ID" >> "$OUT"', source)

    def test_runtime_permission_helper_reports_and_records_successful_grants(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_grant_runtime_permissions com.example.app
            printf '%s|%s|%s\n' "$DD_RUNTIME_PERMISSION_COUNT" \
              "$DD_GRANTED_PERMISSION_COUNT" "$DD_GRANTED_PERMISSIONS"
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  android.permission.CAMERA: granted=false' \
                  '  android.permission.POST_NOTIFICATIONS: granted=false' \
                  'Components:' ;;
              "shell pm grant --user 0 com.example.app android.permission.CAMERA"|\
              "shell pm grant --user 0 com.example.app android.permission.POST_NOTIFICATIONS") exit 0 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout.strip(),
            "2|2|android.permission.CAMERA android.permission.POST_NOTIFICATIONS",
        )

    def test_runtime_permission_helper_captures_custom_permission_names(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_grant_runtime_permissions com.example.app
            printf '%s|%s|%s\n' "$DD_RUNTIME_PERMISSION_COUNT" \
              "$DD_GRANTED_PERMISSION_COUNT" "$DD_GRANTED_PERMISSIONS"
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  com.vendor.ACCESS_feature: granted=false' \
                  '  com.vendor.camera_access: granted=false' \
                  'Components:' ;;
              "shell pm grant --user 0 com.example.app com.vendor.ACCESS_feature"|\
              "shell pm grant --user 0 com.example.app com.vendor.camera_access") exit 0 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout.strip(),
            "2|2|com.vendor.ACCESS_feature com.vendor.camera_access",
        )

    def test_runtime_permission_helper_does_not_own_an_existing_grant(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_grant_runtime_permissions com.example.app
            printf '%s|%s|%s|%s\n' "$DD_RUNTIME_PERMISSION_COUNT" \
              "$DD_GRANTED_PERMISSION_COUNT" "$DD_INITIAL_GRANTED_PERMISSIONS" \
              "$DD_GRANTED_PERMISSIONS"
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  android.permission.CAMERA: granted=true' \
                  '  android.permission.POST_NOTIFICATIONS: granted=false' \
                  'Components:' ;;
              "shell pm grant --user 0 com.example.app android.permission.CAMERA") exit 9 ;;
              "shell pm grant --user 0 com.example.app android.permission.POST_NOTIFICATIONS") exit 0 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout.strip(),
            "2|2|android.permission.CAMERA|android.permission.POST_NOTIFICATIONS",
        )

    def test_permission_identity_describes_effective_not_newly_granted_state(self) -> None:
        script = """
            set -euo pipefail
            . "$LIB"
            dd_grant_runtime_permissions com.example.app
            printf '%s\n' "$DD_PERMISSION_STATE_ID"
        """
        initially_granted = self.run_with_fake_adb(
            script,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  android.permission.CAMERA: granted=true' \
                  'Components:' ;;
              *) exit 1 ;;
            esac
            """,
        )
        newly_granted = self.run_with_fake_adb(
            script,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  android.permission.CAMERA: granted=false' \
                  'Components:' ;;
              "shell pm grant --user 0 com.example.app android.permission.CAMERA") exit 0 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(initially_granted.returncode, 0, initially_granted.stderr)
        self.assertEqual(newly_granted.returncode, 0, newly_granted.stderr)
        self.assertRegex(initially_granted.stdout.strip(), r"^[0-9a-f]{32}$")
        self.assertEqual(initially_granted.stdout, newly_granted.stdout)

    def test_runtime_permission_helper_ignores_other_android_users(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_grant_runtime_permissions com.example.app
            printf '%s|%s|%s|%s\n' "$DD_RUNTIME_PERMISSION_COUNT" \
              "$DD_GRANTED_PERMISSION_COUNT" "$DD_INITIAL_GRANTED_PERMISSIONS" \
              "$DD_GRANTED_PERMISSIONS"
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  '  runtime permissions:' \
                  '    android.permission.CAMERA: granted=true' \
                  'User 10: installed=true' \
                  '  runtime permissions:' \
                  '    android.permission.CAMERA: granted=false' \
                  '    android.permission.POST_NOTIFICATIONS: granted=false' \
                  'Queries:' ;;
              "shell pm grant "*) exit 9 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "1|1|android.permission.CAMERA|")

    def test_uninstall_helper_skips_a_package_absent_for_the_selected_user(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            DD_ANDROID_USER=0
            dd_ensure_uninstalled com.example.app
            """,
            """
            #!/usr/bin/env bash
            # Absence as the device reports it: no output, exit 1.
            case "$*" in
              *"pm list users"*)
                printf 'Users:\n  UserInfo{0:Owner:c13}\n__dd_rc=0\n' ;;
              *"pm path"*) printf '__dd_rc=1\n'; exit 1 ;;
              "uninstall com.example.app") exit 9 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_uninstall_helper_accepts_only_an_absent_postcondition(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            DD_ANDROID_USER=0
            dd_ensure_uninstalled com.example.app
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              *"pm list users"*)
                printf 'Users:\n  UserInfo{0:Owner:c13}\n__dd_rc=0\n' ;;
              *"pm path"*)
                if [ -e "$FAKE_ADB_STATE" ]; then printf '__dd_rc=1\n'; exit 1; fi
                printf 'package:/data/app/base.apk\n__dd_rc=0\n' ;;
              "uninstall com.example.app") : > "$FAKE_ADB_STATE" ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_uninstall_helper_rejects_a_protected_package(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            DD_ANDROID_USER=0
            dd_ensure_uninstalled com.example.app
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              *"pm list users"*)
                printf 'Users:\n  UserInfo{0:Owner:c13}\n__dd_rc=0\n' ;;
              *"pm path"*)
                printf 'package:/data/app/base.apk\n__dd_rc=0\n' ;;
              "uninstall com.example.app")
                printf 'Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]\n' >&2
                exit 1 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("remains installed for Android user 0", result.stderr)
        self.assertIn("Refusing 'install -r'", result.stderr)

    def test_uninstall_helper_refuses_a_package_owned_by_another_user(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            DD_ANDROID_USER=0
            dd_ensure_uninstalled com.example.app
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              *"pm list users"*)
                printf '%s\n' \
                  'Users:' \
                  '  UserInfo{0:Owner:c13} running' \
                  '  UserInfo{10:Work:30}' \
                  '__dd_rc=0' ;;
              *"pm path --user 10 com.example.app"*)
                printf 'package:/data/app/work/base.apk\n__dd_rc=0\n' ;;
              *"pm path --user 0 com.example.app"*) printf '__dd_rc=1\n'; exit 1 ;;
              "uninstall com.example.app") exit 9 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("also installed for Android user(s): 10", result.stderr)
        self.assertIn("Refusing global 'adb uninstall'", result.stderr)

    def test_runtime_permission_helper_refuses_a_partial_grant(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            if dd_grant_runtime_permissions com.example.app; then exit 9; fi
            printf '%s|%s|%s|%s\n' "$DD_RUNTIME_PERMISSION_COUNT" \
              "$DD_GRANTED_PERMISSION_COUNT" "$DD_GRANTED_PERMISSIONS" \
              "$DD_UNGRANTED_PERMISSIONS"
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  android.permission.CAMERA: granted=false' \
                  '  android.permission.POST_NOTIFICATIONS: granted=false' \
                  'Components:' ;;
              "shell pm grant --user 0 com.example.app android.permission.CAMERA") exit 0 ;;
              "shell pm grant --user 0 com.example.app android.permission.POST_NOTIFICATIONS") exit 1 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout.strip(),
            "2|1|android.permission.CAMERA|android.permission.POST_NOTIFICATIONS",
        )
        self.assertIn("failed to grant all runtime permissions", result.stderr)

    def test_runtime_permission_helper_retries_after_granting_a_prerequisite(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_grant_runtime_permissions com.example.app
            printf '%s|%s|%s\n' "$DD_RUNTIME_PERMISSION_COUNT" \
              "$DD_GRANTED_PERMISSION_COUNT" "$DD_GRANTED_PERMISSIONS"
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  android.permission.ACCESS_BACKGROUND_LOCATION: granted=false' \
                  '  android.permission.ACCESS_FINE_LOCATION: granted=false' \
                  'Components:' ;;
              "shell pm grant --user 0 com.example.app android.permission.ACCESS_BACKGROUND_LOCATION")
                if [ -e "$FAKE_ADB_STATE" ]; then exit 0; fi
                exit 1 ;;
              "shell pm grant --user 0 com.example.app android.permission.ACCESS_FINE_LOCATION")
                : > "$FAKE_ADB_STATE"
                exit 0 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout.strip(),
            "2|2|android.permission.ACCESS_FINE_LOCATION "
            "android.permission.ACCESS_BACKGROUND_LOCATION",
        )

    def test_ungrantable_permission_aborts_by_default(self) -> None:
        """A hard-restricted permission cannot be granted by any number of passes."""
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_grant_runtime_permissions com.example.app && echo UNEXPECTED_SUCCESS
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  android.permission.CAMERA: granted=false' \
                  '  android.permission.READ_SMS: granted=false' \
                  'Components:' ;;
              "shell pm grant --user 0 com.example.app android.permission.CAMERA") exit 0 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertNotIn("UNEXPECTED_SUCCESS", result.stdout)
        self.assertIn("failed to grant all runtime permissions", result.stderr)
        self.assertIn("android.permission.READ_SMS", result.stderr)
        self.assertIn("ALLOW_PARTIAL_PERMISSIONS=1", result.stderr)

    def test_ungrantable_permission_override_continues_and_names_them(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            ALLOW_PARTIAL_PERMISSIONS=1 dd_grant_runtime_permissions com.example.app
            printf '%s|%s|%s|%s|%s\n' "$DD_GRANTED_PERMISSION_COUNT" \
              "$DD_RUNTIME_PERMISSION_COUNT" "$DD_EFFECTIVE_GRANTED_PERMISSIONS" \
              "$DD_UNGRANTED_PERMISSIONS" "$DD_PERMISSION_STATE_ID"
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  android.permission.CAMERA: granted=false' \
                  '  android.permission.READ_SMS: granted=false' \
                  'Components:' ;;
              "shell pm grant --user 0 com.example.app android.permission.CAMERA") exit 0 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        fields = result.stdout.strip().split("|")
        self.assertEqual(
            fields[:4],
            ["1", "2", "android.permission.CAMERA", "android.permission.READ_SMS"],
        )
        self.assertRegex(fields[4], r"^[0-9a-f]{32}$")
        self.assertIn("ALLOW_PARTIAL_PERMISSIONS=1", result.stderr)

        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        self.assertIn('record_permission_state "$arm_key" "$arm"', benchmark)
        self.assertIn('echo "# permission_a=$_PERMISSION_STATE_ID"', benchmark)
        self.assertIn('echo "# permission_b=$_PERMISSION_STATE_ID"', benchmark)
        self.assertIn("effective runtime-permission state changed within this run", benchmark)

    def test_partial_permission_override_rejects_non_boolean(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            ALLOW_PARTIAL_PERMISSIONS=yes dd_grant_runtime_permissions com.example.app \
              && echo UNEXPECTED_SUCCESS
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell dumpsys package com.example.app")
                printf '%s\n' \
                  'User 0: installed=true' \
                  'runtime permissions:' \
                  '  android.permission.CAMERA: granted=false' \
                  '  android.permission.READ_SMS: granted=false' \
                  'Components:' ;;
              "shell pm grant --user 0 com.example.app android.permission.CAMERA") exit 0 ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertNotIn("UNEXPECTED_SUCCESS", result.stdout)
        self.assertIn("must be 0 or 1", result.stderr)

    def test_every_device_workflow_uses_shared_permission_helper(self) -> None:
        for name in ("verify_sdk_active.sh", "coldstart_bench.sh", "capture_trace.sh"):
            source = (HARNESS / name).read_text(encoding="utf-8")
            self.assertIn("dd_resolve_android_user || exit 2", source, name)
            self.assertIn('dd_ensure_uninstalled "$PKG"', source, name)
            self.assertNotIn('"$ADB" uninstall "$PKG"', source, name)
            self.assertIn('if ! dd_grant_runtime_permissions "$PKG"; then', source, name)
            self.assertIn('_GRANTED="$DD_GRANTED_PERMISSIONS"', source, name)
        verifier = (HARNESS / "verify_sdk_active.sh").read_text(encoding="utf-8")
        self.assertLess(
            verifier.index('dd_grant_runtime_permissions "$PKG"'),
            verifier.index('shell am start -W'),
        )

    def test_unique_package_uid_rejects_shared_uid(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_unique_pkg_uid com.example.app
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell pm list packages -U --user 0")
                printf '%s\n' \
                  'package:com.example.app uid:10123' \
                  'package:com.example.shared uid:10123' ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("shares UID 10123", result.stderr)

    def test_app_trace_scrapes_and_watcher_are_package_scoped(self) -> None:
        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        self.assertIn('logcat -d --uid="$PKG_UID"', benchmark)
        self.assertIn('printf \'%s\\n\' "$app_lg" | grep -m1', benchmark)
        self.assertIn('logcat --uid="$_ENDPOINT_UID"', capture)

    def test_endpoint_watcher_cleanup_targets_pipeline_group_leader(self) -> None:
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        capture_pgid = capture.index('_ENDPOINT_WATCH_PGID=$(jobs -p %+)')
        disable_job_control = capture.index("set +m", capture_pgid)
        self.assertLess(capture_pgid, disable_job_control)
        self.assertIn('kill -- -"$_ENDPOINT_WATCH_PGID"', capture)
        self.assertNotIn('kill -- -"$_ENDPOINT_WATCH_PID"', capture)

    def test_supported_host_scripts_do_not_require_seq(self) -> None:
        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        # Assert the invariant -- "no host `seq` is invoked" -- rather than one
        # spelling of the replacement loops, which any reformatting would break
        # while proving nothing about the dependency.
        for name, body in (("coldstart_bench.sh", benchmark), ("capture_trace.sh", capture)):
            for line in body.splitlines():
                code = line.split("#", 1)[0]
                self.assertIsNone(
                    re.search(r"(^|[|(`;&]|\$\()\s*seq\s", code),
                    f"{name} still invokes host `seq`: {line.strip()}")
            # And that each script still parses, so a loop rewrite cannot land broken.
            self.assertEqual(
                subprocess.run(["bash", "-n", str(HARNESS / name)],
                               capture_output=True, text=True).returncode,
                0, f"{name} does not parse")

    def test_warmups_use_the_measured_launch_validity_gate(self) -> None:
        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        shared_validation = benchmark.index('dd_validate_cold_launch_output "$out"')
        gate = benchmark.index(
            'if [ "$phase" = warmup ] || [ "$phase" = measure ]; then'
        )
        total_rejection = benchmark.index('elif [ -n "$launch_error" ]; then', gate)
        rejected_phase = benchmark.index('row_phase="${phase}_rejected"', gate)
        write_row = benchmark.index('tee -a "$OUT"', rejected_phase)
        abort = benchmark.index('die "[$arm] $phase launch $i $reject"', write_row)
        self.assertLess(shared_validation, gate)
        self.assertLess(gate, total_rejection)
        self.assertLess(total_rejection, rejected_phase)
        self.assertLess(gate, rejected_phase)
        self.assertLess(rejected_phase, write_row)
        self.assertLess(write_row, abort)

    def test_ramp_conditioning_launches_use_validity_and_liveness_gates(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            valid=$'Status: ok\\nLaunchState: COLD\\nTotalTime: 321'
            dd_validate_cold_launch_output "$valid"
            printf '%s|%s|%s\n' "$DD_LAUNCH_STATUS" "$DD_LAUNCH_STATE" "$DD_LAUNCH_TOTAL"
            invalid=$'Status: ok\\nLaunchState: WARM\\nTotalTime: 22'
            if dd_validate_cold_launch_output "$invalid"; then exit 9; fi
            printf '%s\n' "$DD_LAUNCH_ERROR"
            missing_total=$'Status: ok\\nLaunchState: COLD'
            if dd_validate_cold_launch_output "$missing_total"; then exit 10; fi
            printf '%s\n' "$DD_LAUNCH_ERROR"
            """,
            """
            #!/usr/bin/env bash
            exit 1
            """,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout.splitlines(),
            [
                "ok|COLD|321",
                "LaunchState='WARM', not COLD",
                "invalid TotalTime 'missing'",
            ],
        )

        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        loop = capture.index("for ((_i=1; _i<=SETTLE_LAUNCHES; _i++)); do")
        loop_end = capture.index("\ndone", loop)
        clear = capture.index('shell logcat -c', loop)
        stale_check = capture.index('_SETTLE_STALE=', clear)
        launch = capture.index('shell am start -W "${START_ARGS[@]}"', stale_check)
        validation = capture.index('dd_validate_cold_launch_output "$_SETTLE_OUT"', launch)
        target_marker = capture.index('_SETTLE_TARGET_DISPLAYED=', validation)
        foreign_marker = capture.index('dd_first_foreign_displayed_activity "$PKG"', target_marker)
        foreground = capture.index('_SETTLE_TOP=$(dd_top_activity)', foreign_marker)
        liveness = capture.index('dd_datadog_threads "$_SETTLE_PIDS"', foreground)
        self.assertLess(
            loop,
            clear,
        )
        self.assertLess(clear, stale_check)
        self.assertLess(stale_check, launch)
        self.assertLess(launch, validation)
        self.assertLess(validation, target_marker)
        self.assertLess(target_marker, foreign_marker)
        self.assertLess(foreign_marker, foreground)
        self.assertLess(foreground, liveness)
        self.assertLess(liveness, loop_end)
        self.assertNotIn(
            'shell am start -W "${START_ARGS[@]}" >/dev/null',
            capture[loop:loop_end],
        )

        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        probe = benchmark.index("probe_datadog() {")
        probe_end = benchmark.index("\n}", probe)
        probe_clear = benchmark.index('shell logcat -c', probe)
        probe_launch = benchmark.index('shell am start -W "${START_ARGS[@]}"', probe_clear)
        probe_validation = benchmark.index('dd_validate_cold_launch_output "$pout"', probe_launch)
        probe_target = benchmark.index('probe_ours=', probe_validation)
        probe_foreign = benchmark.index(
            'dd_first_foreign_displayed_activity "$PKG"', probe_target
        )
        probe_foreground = benchmark.index('probe_top=$(dd_top_activity)', probe_foreign)
        rejected_phase = benchmark.index("probe_rejected", probe_foreground)
        write_row = benchmark.index('>> "$OUT"', rejected_phase)
        abort = benchmark.index('die "[$arm] liveness probe launch:', write_row)
        probe_liveness = benchmark.index('pids=$(dd_pkg_pids "$PKG")', abort)
        self.assertLess(probe_clear, probe_launch)
        self.assertLess(probe_launch, probe_validation)
        self.assertLess(probe_validation, probe_target)
        self.assertLess(probe_target, probe_foreign)
        self.assertLess(probe_foreign, probe_foreground)
        self.assertLess(probe_foreground, write_row)
        self.assertLess(abort, probe_liveness)
        self.assertLess(probe_liveness, probe_end)
        self.assertLess(abort, probe_end)


class AbStatsRegressionTests(unittest.TestCase):
    COMPATIBLE_META = (
        "fp=build/fingerprint emulator=0 android_user=0 "
        "compile_filter=speed-profile animations=0 airplane=0 abi=arm64-v8a "
        "launcher=com.example/.MainActivity warmup=3"
    )

    @staticmethod
    def benchmark_csv(
        missing_treatment_block: int | None = None,
        invalid_treatment_block: int | None = None,
        rejected_treatment_block: int | None = None,
        metadata: str | None = None,
        constant_delta: int | None = None,
        baseline_zero: bool = False,
        runs_per_cell: int = 1,
        block_count: int = 4,
        baseline_offset: int = 100,
    ) -> str:
        rows = [
            "label,block,pos_in_block,phase,run,total_ms,launch_state,status,foreground,ttfd"
        ]
        if metadata is not None:
            rows.insert(0, f"# {metadata}")
        for block in range(1, block_count + 1):
            order = (("A_noDD", 1), ("B_withDD", 2)) if block % 2 else (
                ("B_withDD", 1),
                ("A_noDD", 2),
            )
            for label, position in order:
                baseline = 0 if baseline_zero else baseline_offset + block
                if label == "A_noDD":
                    value = baseline
                elif constant_delta is not None:
                    value = baseline + constant_delta
                else:
                    value = baseline + 12 + (block % 3 - 1)
                ttfd = "NA" if label == "B_withDD" and block == missing_treatment_block else value
                phase = (
                    "measure_rejected"
                    if label == "B_withDD" and block == rejected_treatment_block
                    else "measure"
                )
                status = (
                    "error"
                    if label == "B_withDD" and block == invalid_treatment_block
                    else "ok"
                )
                for run in range(1, runs_per_cell + 1):
                    rows.append(
                        f"{label},{block},{position},{phase},{run},{value},"
                        f"COLD,{status},ok,{ttfd}"
                    )
        return "\n".join(rows) + "\n"

    def run_stats(self, csv_body: str, *args: str) -> subprocess.CompletedProcess[str]:
        return self.run_stats_files([csv_body], *args)

    def run_stats_files(
        self,
        csv_bodies: list[str],
        *args: str,
    ) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as tmp:
            paths = []
            for index, csv_body in enumerate(csv_bodies):
                path = Path(tmp) / f"results_{index}.csv"
                path.write_text(csv_body, encoding="utf-8")
                paths.append(str(path))
            return subprocess.run(
                ["python3", str(AB_STATS), *paths, "--metric", "ttfd", *args],
                check=False,
                capture_output=True,
                text=True,
            )

    @staticmethod
    def erase_positions(csv_body: str, blocks: set[int] | None = None) -> str:
        output = []
        for line in csv_body.splitlines():
            if line.startswith("#"):
                output.append(line)
                continue
            fields = line.split(",")
            if fields[0] == "label":
                if blocks is None:
                    fields.pop(2)
            elif blocks is None:
                fields.pop(2)
            elif int(fields[1]) in blocks:
                fields[2] = ""
            output.append(",".join(fields))
        return "\n".join(output) + "\n"

    @staticmethod
    def erase_column(csv_body: str, column: str) -> str:
        lines = csv_body.splitlines()
        header_index = next(i for i, line in enumerate(lines) if not line.startswith("#"))
        header = lines[header_index].split(",")
        column_index = header.index(column)
        output = lines[:header_index]
        for line in lines[header_index:]:
            fields = line.split(",")
            fields.pop(column_index)
            output.append(",".join(fields))
        return "\n".join(output) + "\n"

    def test_selected_endpoint_missing_is_refused_by_default(self) -> None:
        result = self.run_stats(self.benchmark_csv(missing_treatment_block=2))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("refusing to report --metric ttfd", result.stderr)
        self.assertNotIn("PRIMARY ENDPOINT", result.stdout)

    def test_non_finite_endpoint_is_refused_as_invalid_evidence(self) -> None:
        stats = load_ab_stats_module()
        for value in ("NaN", "nan", "Infinity", "-Infinity", "inf", "-inf", "1e309"):
            self.assertIsNone(stats.parse_ms(value), value)

        original = "A_noDD,1,1,measure,1,101,COLD,ok,ok,101"
        for value in ("NaN", "Infinity", "-Infinity"):
            with self.subTest(value=value):
                replacement = f"A_noDD,1,1,measure,1,101,COLD,ok,ok,{value}"
                result = self.run_stats(self.benchmark_csv().replace(original, replacement))
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("refusing to report --metric ttfd", result.stderr)
                self.assertNotIn("Traceback", result.stderr)
                self.assertNotIn("PRIMARY ENDPOINT", result.stdout)

    def test_missing_endpoint_override_stays_non_reportable(self) -> None:
        result = self.run_stats(
            self.benchmark_csv(missing_treatment_block=2),
            "--allow-missing-endpoint",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("DIAGNOSTIC ONLY", result.stdout)
        self.assertIn("NOT REPORTABLE", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)

    def test_missing_endpoint_in_an_unanalysed_arm_does_not_block(self) -> None:
        """A third label's NA could never have contributed, so it must not refuse."""
        csv_body = self.benchmark_csv(
            metadata=f"{self.COMPATIBLE_META} blocks=4 runs=1"
        ) + "THIRD,99,2,measure,1,133,COLD,ok,ok,NA\n"
        result = self.run_stats(csv_body)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("refusing to report", result.stdout + result.stderr)
        self.assertIn("95% CI", result.stdout)

    def test_unrelated_labels_cannot_fill_missing_selected_arm_blocks(self) -> None:
        csv_body = self.benchmark_csv(
            metadata=f"{self.COMPATIBLE_META} blocks=6 runs=1",
            block_count=4,
        )
        for block in (5, 6):
            csv_body += f"C,{block},1,measure,1,100,COLD,ok,ok,100\n"
            csv_body += f"D,{block},2,measure,1,101,COLD,ok,ok,101\n"

        result = self.run_stats(csv_body)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("INCOMPLETE EXPERIMENT MATRIX", result.stdout)
        self.assertIn("A_noDD block 5: 0/1 launches", result.stdout)
        self.assertIn("B_withDD block 6: 0/1 launches", result.stdout)
        self.assertIn("refusing to analyze a truncated run", result.stderr)

    def test_complete_selected_endpoint_still_reports_primary_interval(self) -> None:
        result = self.run_stats(self.benchmark_csv())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--- PRIMARY ENDPOINT: paired block-level delta ---", result.stdout)
        self.assertIn("  95% CI                ", result.stdout)

    def test_missing_launch_validity_columns_suppress_primary_inference(self) -> None:
        for column in ("status", "launch_state", "foreground"):
            with self.subTest(column=column):
                result = self.run_stats(
                    self.erase_column(self.benchmark_csv(), column)
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("launch-validity evidence", result.stdout)
                self.assertIn("NOT REPORTABLE", result.stdout)
                self.assertNotIn("  95% CI                ", result.stdout)
                self.assertNotIn("  MDE at", result.stdout)
                self.assertNotIn("  => Significant", result.stdout)

    def test_empty_launch_validity_cell_suppresses_primary_inference(self) -> None:
        csv_body = self.benchmark_csv().replace(
            "A_noDD,1,1,measure,1,101,COLD,ok,ok,101",
            "A_noDD,1,1,measure,1,101,COLD,ok,,101",
        )
        result = self.run_stats(csv_body)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("launch-validity evidence", result.stdout)
        self.assertIn("NOT REPORTABLE", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)

    def test_byte_identical_csv_copy_is_refused(self) -> None:
        csv_body = self.benchmark_csv()
        result = self.run_stats_files([csv_body, csv_body])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("refusing byte-identical CSV inputs", result.stderr)
        self.assertIn("same observations", result.stderr)

    def test_zero_block_variance_suppresses_primary_inference(self) -> None:
        for delta in (0, 12):
            with self.subTest(delta=delta):
                result = self.run_stats(self.benchmark_csv(constant_delta=delta))
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("NOT ESTIMABLE", result.stdout)
                self.assertIn("sample variance is zero", result.stdout)
                self.assertNotIn("  95% CI                ", result.stdout)
                self.assertNotIn("  MDE at", result.stdout)
                self.assertNotIn("  => Significant", result.stdout)

    def test_zero_baseline_mean_keeps_absolute_primary_inference(self) -> None:
        result = self.run_stats(self.benchmark_csv(baseline_zero=True))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("relative % undefined: baseline mean is 0", result.stdout)
        self.assertIn("  95% CI                ", result.stdout)
        self.assertIn("  MDE at", result.stdout)

    def test_relative_effect_weights_baseline_by_contributing_block(self) -> None:
        result = self.run_stats_files(
            [
                self.benchmark_csv(
                    metadata=f"{self.COMPATIBLE_META} blocks=2 runs=1",
                    runs_per_cell=1,
                    block_count=2,
                    baseline_offset=100,
                ),
                self.benchmark_csv(
                    metadata=f"{self.COMPATIBLE_META} blocks=2 runs=20",
                    runs_per_cell=20,
                    block_count=2,
                    baseline_offset=1000,
                ),
            ]
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        # Four equally weighted contributing baselines: 101, 102, 1001, 1002.
        # The 12.5 ms mean delta is therefore 2.27%, regardless of row counts.
        self.assertIn("(+2.27% of baseline)", result.stdout)
        self.assertNotIn("(+1.30% of baseline)", result.stdout)

    def test_required_blocks_are_runnable_counterbalanced_designs(self) -> None:
        stats = load_ab_stats_module()
        self.assertEqual(stats.blocks_for(5, 10), 6)
        self.assertEqual(stats.blocks_for(5, 10) % 2, 0)

    def test_invalid_measured_row_is_refused_by_default(self) -> None:
        result = self.run_stats(self.benchmark_csv(invalid_treatment_block=2))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("refusing to report after excluding 1 invalid", result.stderr)
        self.assertNotIn("PRIMARY ENDPOINT", result.stdout)

    def test_invalid_row_override_stays_non_reportable(self) -> None:
        result = self.run_stats(
            self.benchmark_csv(invalid_treatment_block=2),
            "--allow-aborted",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("DIAGNOSTIC ONLY", result.stdout)
        self.assertIn("NOT REPORTABLE", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)

    def test_measure_rejected_without_abort_trailer_is_not_reportable(self) -> None:
        csv_body = self.benchmark_csv(
            rejected_treatment_block=4,
            metadata="blocks=4 runs=1",
        )
        refused = self.run_stats(csv_body)
        self.assertNotEqual(refused.returncode, 0)
        self.assertIn("refusing to report after excluding 1 invalid", refused.stderr)
        self.assertNotIn("PRIMARY ENDPOINT", refused.stdout)

        diagnostic = self.run_stats(csv_body, "--allow-aborted")
        self.assertEqual(diagnostic.returncode, 0, diagnostic.stderr)
        self.assertIn("DIAGNOSTIC ONLY", diagnostic.stdout)
        self.assertIn("NOT REPORTABLE", diagnostic.stdout)
        self.assertNotIn("  95% CI                ", diagnostic.stdout)

    def test_aborted_run_override_stays_non_reportable(self) -> None:
        result = self.run_stats(
            self.benchmark_csv(metadata="RUN ABORTED"),
            "--allow-aborted",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("the CSV is marked RUN ABORTED", result.stdout)
        self.assertIn("NOT REPORTABLE", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)

    def test_invalid_row_in_unanalysed_arm_does_not_block(self) -> None:
        csv_body = self.benchmark_csv()
        csv_body += "THIRD,1,2,measure,1,999,WARM,error,OTHER,999\n"
        csv_body += "THIRD,2,1,measure_rejected,1,999,COLD,ok,ok,999\n"
        result = self.run_stats(csv_body)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("refusing to report after excluding", result.stdout + result.stderr)
        self.assertIn("  95% CI                ", result.stdout)

    def test_pooling_different_sdk_liveness_expectations_is_refused(self) -> None:
        result = self.run_stats_files(
            [
                self.benchmark_csv(
                    metadata=f"{self.COMPATIBLE_META} expect_a=0 expect_b=1"
                ),
                self.benchmark_csv(
                    metadata=f"{self.COMPATIBLE_META} expect_a=0 expect_b=0"
                ),
            ]
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("different SDK-liveness expectations", result.stderr)
        self.assertIn("expect_b: 0 vs 1", result.stderr)

    def test_pooling_different_permission_outcomes_is_refused(self) -> None:
        def stamped(permission_b: str) -> str:
            lines = self.benchmark_csv(metadata=self.COMPATIBLE_META).splitlines()
            # The collector learns arm B only after arm A's rows already exist, so
            # these metadata comments are intentionally inside the CSV body.
            lines[3:3] = ["# permission_a=aaa", f"# permission_b={permission_b}"]
            return "\n".join(lines) + "\n"

        result = self.run_stats_files(
            [
                stamped("bbb"),
                stamped("ccc"),
            ]
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("different runtime-permission outcomes", result.stderr)
        self.assertIn("permission_b: bbb vs ccc", result.stderr)

    def test_t_quantiles_stay_conservative_above_largest_table_df(self) -> None:
        stats = load_ab_stats_module()
        self.assertEqual(stats.t_crit(119), 2.000)
        self.assertEqual(stats.t_crit(120), 1.980)
        self.assertEqual(stats.t_crit(121), 1.980)
        self.assertEqual(stats.t_crit(10_000), 1.980)
        self.assertEqual(stats.t_power80(120), 0.845)
        self.assertEqual(stats.t_power80(10_000), 0.845)

    def test_pooling_different_android_users_is_refused(self) -> None:
        result = self.run_stats_files(
            [
                self.benchmark_csv(metadata=self.COMPATIBLE_META),
                self.benchmark_csv(
                    metadata=self.COMPATIBLE_META.replace("android_user=0", "android_user=10")
                ),
            ]
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("android_user: 0 vs 10", result.stderr)

    def test_pooling_without_mandatory_metadata_is_refused(self) -> None:
        result = self.run_stats_files(
            [
                self.benchmark_csv(metadata="expect_a=0"),
                self.benchmark_csv(metadata="expect_a=1"),
            ]
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("without mandatory device/protocol metadata", result.stderr)
        self.assertIn("fp: absent from", result.stderr)

    def test_missing_all_order_evidence_suppresses_primary_inference(self) -> None:
        result = self.run_stats(self.erase_positions(self.benchmark_csv()))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("NOT REPORTABLE: only 0 of 4", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)
        self.assertNotIn("  MDE at", result.stdout)
        self.assertNotIn("  => Significant", result.stdout)

    def test_partial_order_evidence_suppresses_primary_inference(self) -> None:
        result = self.run_stats(
            self.erase_positions(self.benchmark_csv(), blocks={4})
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("NOT REPORTABLE: only 3 of 4", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)
        self.assertNotIn("  MDE at", result.stdout)
        self.assertNotIn("  => Significant", result.stdout)

    def test_same_position_for_both_arms_suppresses_primary_inference(self) -> None:
        lines = self.benchmark_csv().splitlines()
        header_index = next(i for i, line in enumerate(lines) if not line.startswith("#"))
        for index in range(header_index + 1, len(lines)):
            fields = lines[index].split(",")
            fields[2] = "1"
            lines[index] = ",".join(fields)

        result = self.run_stats("\n".join(lines) + "\n")

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("NOT REPORTABLE: 4 contributing block(s)", result.stdout)
        self.assertIn("stable, complementary pos_in_block pair", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)
        self.assertNotIn("  MDE at", result.stdout)
        self.assertNotIn("  => Significant", result.stdout)

    def test_unrelated_label_cannot_overwrite_selected_position_evidence(self) -> None:
        csv_body = self.benchmark_csv()
        csv_body += "THIRD,1,1,measure,1,999,COLD,ok,ok,999\n"

        result = self.run_stats(csv_body)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("  95% CI                ", result.stdout)
        self.assertNotIn("first-arm counts among contributing blocks", result.stdout)


if __name__ == "__main__":
    unittest.main()
