#!/usr/bin/env python3
"""Regression tests for cross-script cold-start harness contracts."""

import ast
import hashlib
import importlib.util
import os
import re
import shutil
from pathlib import Path
import stat
import subprocess
import tempfile
import textwrap
import types
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
        self.assertLess(capture.index(gate), capture.index("start_perfetto() {"))

    def test_fixed_performance_is_a_shared_fail_closed_precollection_gate(self) -> None:
        success = self.run_with_fake_adb(
            '. "$LIB"; dd_enable_fixed_performance_mode; echo enabled',
            """
            #!/usr/bin/env bash
            [ "$*" = "shell cmd power set-fixed-performance-mode-enabled true" ]
            """,
        )
        self.assertEqual(success.returncode, 0, success.stderr)
        self.assertEqual(success.stdout.strip(), "enabled")

        failure = self.run_with_fake_adb(
            '. "$LIB"; dd_enable_fixed_performance_mode',
            """
            #!/usr/bin/env bash
            exit 17
            """,
        )
        self.assertNotEqual(failure.returncode, 0)
        self.assertIn("did not accept Android fixed-performance mode", failure.stderr)
        self.assertIn("ALLOW_DYNAMIC_PERFORMANCE=1", failure.stderr)

        # Not every power HAL implements the mode. An unconditional hard requirement
        # left such a device with no way through at all, so the weaker scenario is
        # available explicitly -- and stamped, so it cannot be pooled with a pinned
        # run by accident.
        allowed = self.run_with_fake_adb(
            'ALLOW_DYNAMIC_PERFORMANCE=1; . "$LIB"'
            '; dd_enable_fixed_performance_mode; echo "mode=$DD_PERF_MODE"',
            """
            #!/usr/bin/env bash
            exit 17
            """,
        )
        self.assertEqual(allowed.returncode, 0, allowed.stderr)
        self.assertEqual(allowed.stdout.strip(), "mode=dynamic")
        self.assertIn("DYNAMIC CPU behavior", allowed.stderr)

        # `fixed` only when the device accepted it, so the restore path cannot undo a
        # mode this run never set.
        for name in ("coldstart_bench.sh", "capture_trace.sh"):
            source = (HARNESS / name).read_text(encoding="utf-8")
            gate_at = source.index("dd_enable_fixed_performance_mode || exit 2")
            guard = source.index('[ "$DD_PERF_MODE" = fixed ]', gate_at)
            ownership = source.index("_WE_SET_PERF=1", guard)
            self.assertLess(guard, ownership, name)
        # The outcome reaches the CSV, so analysis can refuse to mix the scenarios.
        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        self.assertIn("perf_mode=$DD_PERF_MODE", benchmark)

        gate = "dd_enable_fixed_performance_mode || exit 2"
        for name in ("coldstart_bench.sh", "capture_trace.sh"):
            source = (HARNESS / name).read_text(encoding="utf-8")
            self.assertEqual(source.count(gate), 1, name)
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        self.assertLess(capture.index(gate), capture.index("start_perfetto() {"))

    def test_package_process_enumeration_never_falls_back_to_pidof(self) -> None:
        complete = self.run_with_fake_adb(
            '. "$LIB"; dd_pkg_pids com.example.app',
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell ps -A -o PID -o NAME")
                printf 'PID NAME\n1 init\n123 com.example.app\n456 com.example.app:startup\n'
                ;;
              *) exit 90 ;;
            esac
            """,
        )
        self.assertEqual(complete.returncode, 0, complete.stderr)
        self.assertEqual(complete.stdout.splitlines(), ["123", "456"])

        unavailable = self.run_with_fake_adb(
            '. "$LIB"; dd_pkg_pids com.example.app',
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell ps -A -o PID -o NAME") exit 17 ;;
              "shell pidof com.example.app") printf '999\n' ;;
              *) exit 90 ;;
            esac
            """,
        )
        self.assertNotEqual(unavailable.returncode, 0)
        self.assertIn("full ps query failed", unavailable.stderr)
        self.assertNotEqual(unavailable.stdout.strip(), "999")

        no_match = self.run_with_fake_adb(
            '. "$LIB"; dd_pkg_pids com.example.app',
            """
            #!/usr/bin/env bash
            printf 'PID NAME\n1 init\n2 system_server\n'
            """,
        )
        self.assertEqual(no_match.returncode, 0, no_match.stderr)
        self.assertEqual(no_match.stdout, "\n")

        malformed = self.run_with_fake_adb(
            '. "$LIB"; dd_pkg_pids com.example.app',
            """
            #!/usr/bin/env bash
            printf 'PID NAME\n'
            """,
        )
        self.assertNotEqual(malformed.returncode, 0)
        self.assertIn("expected PID/NAME structure", malformed.stderr)

        lib = LIB.read_text(encoding="utf-8")
        helper = lib[lib.index("dd_pkg_pids() {"):lib.index("\n}", lib.index("dd_pkg_pids() {"))]
        self.assertNotIn('shell pidof', helper)

        # The aborts may state only what the check distinguishes: the listing failed
        # or was malformed. `pidof` is never run, so it cannot be the observed cause
        # -- only the reason there is no second attempt.
        for name in ("coldstart_bench.sh", "verify_sdk_active.sh"):
            source = (HARNESS / name).read_text(encoding="utf-8")
            self.assertNotIn("unknown because exact-name pidof", source, name)
            self.assertNotIn("is unknown because exact-name", source, name)
            self.assertIn("the full process listing failed or was not", source, name)
            self.assertIn("deliberately no", source, name)

    def test_achieved_compile_status_must_be_readable_and_stable(self) -> None:
        readable = self.run_with_fake_adb(
            '. "$LIB"; dd_package_compile_status com.example.app',
            """
            #!/usr/bin/env bash
            cat <<'EOF'
            [com.other]
              status=speed reason=install
            [com.example.app]
              path: /data/app/base.apk
              status=verify reason=install
            EOF
            """,
        )
        self.assertEqual(readable.returncode, 0, readable.stderr)
        self.assertEqual(readable.stdout.strip(), "verify")

        # The shape Android 9+ actually prints, which the fixture above is not:
        # bracketed statuses, one per code path per ABI. base.apk at speed-profile
        # while the secondary ABI and the split sit at verify is a real state, and
        # reporting only the first status called that "one AOT/JIT scenario".
        real_format = self.run_with_fake_adb(
            '. "$LIB"; dd_package_compile_status com.example.app',
            """
            #!/usr/bin/env bash
            cat <<'EOF'
            Current DexOpt state:
              [com.other.app]
                path: /data/app/~~aa==/com.other.app-bb==/base.apk
                  arm64: [status=speed] [reason=install] [primary-abi]
              [com.example.app]
                path: /data/app/~~cc==/com.example.app-dd==/base.apk
                  arm64: [status=speed-profile] [reason=install] [primary-abi]
                  arm: [status=verify] [reason=install]
                path: /data/app/~~cc==/com.example.app-dd==/split_config.en.apk
                  arm64: [status=verify] [reason=install]
              [com.zzz.app]
                path: /data/app/base.apk
                  arm64: [status=speed] [reason=install]
            EOF
            """,
        )
        self.assertEqual(real_format.returncode, 0, real_format.stderr)
        # Sorted, de-duplicated, and stopping at the next package section: the
        # trailing com.zzz.app `speed` must not leak in.
        self.assertEqual(real_format.stdout.strip(), "speed-profile+verify")

        failed = self.run_with_fake_adb(
            '. "$LIB"; dd_package_compile_status com.example.app',
            """
            #!/usr/bin/env bash
            exit 17
            """,
        )
        self.assertNotEqual(failed.returncode, 0)
        self.assertIn("dumpsys failed", failed.stderr)

        missing = self.run_with_fake_adb(
            '. "$LIB"; dd_package_compile_status com.example.app',
            """
            #!/usr/bin/env bash
            printf '[com.example.app]\n  reason=install\n[com.other]\n  status=speed\n'
            """,
        )
        self.assertNotEqual(missing.returncode, 0)
        self.assertIn("no usable", missing.stderr)

        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        self.assertIn('elif [ "$achieved_status" != "$RUN_COMPILE_STATUS" ]; then', benchmark)
        self.assertIn("compile_status=$RUN_COMPILE_STATUS", benchmark)
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        self.assertIn('TRACE_COMPILE_STATUS=$(dd_package_compile_status "$PKG")', capture)
        self.assertIn('EXPECTED_COMPILE_STATUS="${EXPECTED_COMPILE_STATUS:-}"', capture)
        self.assertIn(
            '[ "$TRACE_COMPILE_STATUS" = "$EXPECTED_COMPILE_STATUS" ]',
            capture,
        )
        self.assertIn("benchmark CSV header's compile_status", capture)

        env = os.environ.copy()
        for name in ("WARMUP", "ANIMATIONS", "AIRPLANE"):
            env.pop(name, None)
        env.update(
            PKG="com.example.app",
            EXPECTED_ANIMATIONS="1",
            EXPECTED_AIRPLANE="0",
            EXPECTED_FP="motorola/lisbon/lisbon:12/S3RQS/rel:user/release-keys",
            EXPECTED_ANDROID_USER="0",
            EXPECTED_LAUNCHER="com.example.app/.MainActivity",
            EXPECTED_SDK_LIVENESS="1",
            EXPECTED_APK_MD5="a" * 32,
            EXPECTED_PERMISSION_STATE_ID="b" * 32,
            EXPECTED_COMPILE_STATUS="verify+speed-profile",
            EXPECTED_PERF_MODE="fixed",
            EXPECTED_WARMUP="3",
        )
        compound = subprocess.run(
            ["bash", str(HARNESS / "capture_trace.sh"), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=env,
        )
        self.assertIn("APK not found", compound.stderr)
        self.assertNotIn("invalid EXPECTED_COMPILE_STATUS", compound.stderr)

        env["EXPECTED_COMPILE_STATUS"] = "verify/status"
        malformed = subprocess.run(
            ["bash", str(HARNESS / "capture_trace.sh"), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=env,
        )
        self.assertIn("invalid EXPECTED_COMPILE_STATUS", malformed.stderr)

    def test_trace_requires_benchmark_artifact_and_permission_identity(self) -> None:
        capture_path = HARNESS / "capture_trace.sh"
        capture = capture_path.read_text(encoding="utf-8")
        self.assertIn('EXPECTED_APK_MD5="${EXPECTED_APK_MD5:-}"', capture)
        self.assertIn(
            'EXPECTED_PERMISSION_STATE_ID="${EXPECTED_PERMISSION_STATE_ID:-}"',
            capture,
        )
        self.assertIn('[ "$HOST_MD5" = "$EXPECTED_APK_MD5" ]', capture)
        self.assertIn(
            '[ "$DD_PERMISSION_STATE_ID" = "$EXPECTED_PERMISSION_STATE_ID" ]',
            capture,
        )
        # A wrong host artifact is rejected before the script even requires a device
        # or applies settings. Permission identity is checked immediately after the
        # shared grant helper and before any conditioning launch.
        self.assertLess(
            capture.index('[ "$HOST_MD5" = "$EXPECTED_APK_MD5" ]'),
            capture.index("\ndd_require_device || exit 2"),
        )
        permission_setup = capture.index("\ngrant_runtime_permissions\n")
        permission_gate = capture.index("\nattest_permission_state\n", permission_setup)
        self.assertLess(permission_setup, permission_gate)
        self.assertLess(permission_gate, capture.index("\nACT=$(", permission_gate))

        base_env = os.environ.copy()
        for name in ("EXPECTED_APK_MD5", "EXPECTED_PERMISSION_STATE_ID", "WARMUP",
                     "ANIMATIONS", "AIRPLANE"):
            base_env.pop(name, None)
        base_env.update(
            PKG="com.example.app",
            EXPECTED_ANIMATIONS="1",
            EXPECTED_AIRPLANE="0",
            EXPECTED_FP="motorola/lisbon/lisbon:12/S3RQS/rel:user/release-keys",
            EXPECTED_ANDROID_USER="0",
            EXPECTED_LAUNCHER="com.example.app/.MainActivity",
            EXPECTED_SDK_LIVENESS="1",
            EXPECTED_COMPILE_STATUS="verify",
            EXPECTED_PERF_MODE="fixed",
            EXPECTED_WARMUP="3",
        )

        missing_digest = subprocess.run(
            ["bash", str(capture_path), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=base_env,
        )
        self.assertNotEqual(missing_digest.returncode, 0)
        self.assertIn("set EXPECTED_APK_MD5", missing_digest.stderr)

        base_env["EXPECTED_APK_MD5"] = "not-an-md5"
        malformed_digest = subprocess.run(
            ["bash", str(capture_path), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=base_env,
        )
        self.assertNotEqual(malformed_digest.returncode, 0)
        self.assertIn("invalid EXPECTED_APK_MD5", malformed_digest.stderr)

        base_env["EXPECTED_APK_MD5"] = "a" * 32
        missing_permission = subprocess.run(
            ["bash", str(capture_path), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=base_env,
        )
        self.assertNotEqual(missing_permission.returncode, 0)
        self.assertIn("set EXPECTED_PERMISSION_STATE_ID", missing_permission.stderr)

        base_env["EXPECTED_PERMISSION_STATE_ID"] = "not-an-md5"
        malformed_permission = subprocess.run(
            ["bash", str(capture_path), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=base_env,
        )
        self.assertNotEqual(malformed_permission.returncode, 0)
        self.assertIn("invalid EXPECTED_PERMISSION_STATE_ID", malformed_permission.stderr)

        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "arm.apk"
            apk.write_bytes(b"benchmarked apk")
            actual_md5 = hashlib.md5(apk.read_bytes()).hexdigest()
            base_env.update(
                EXPECTED_PERMISSION_STATE_ID="b" * 32,
                EXPECTED_APK_MD5="0" * 32,
                ADB="/bin/false",
            )
            mismatch = subprocess.run(
                ["bash", str(capture_path), str(apk), "trace", "1"],
                check=False,
                capture_output=True,
                text=True,
                env=base_env,
            )
            self.assertNotEqual(mismatch.returncode, 0)
            self.assertIn(f"trace APK md5={actual_md5}", mismatch.stderr)
            self.assertIn("selected benchmark arm recorded", mismatch.stderr)

            base_env["EXPECTED_APK_MD5"] = actual_md5
            matched = subprocess.run(
                ["bash", str(capture_path), str(apk), "trace", "1"],
                check=False,
                capture_output=True,
                text=True,
                env=base_env,
            )
            self.assertNotEqual(matched.returncode, 0)
            self.assertNotIn("selected benchmark arm recorded", matched.stderr)
            self.assertIn("benchmark APK digest matched", matched.stderr)

        gate_start = capture.index("attest_permission_state() {")
        gate_end = capture.index("\n}\n", gate_start) + 3
        permission_gate_function = capture[gate_start:gate_end]

        def run_permission_gate(actual: str, expected: str) -> subprocess.CompletedProcess[str]:
            env = os.environ.copy()
            env.update(
                DD_PERMISSION_STATE_ID=actual,
                EXPECTED_PERMISSION_STATE_ID=expected,
            )
            return subprocess.run(
                [
                    "bash",
                    "-c",
                    "die() { echo \"FATAL: $*\" >&2; exit 1; }; "
                    "log() { echo \"$*\" >&2; };\n"
                    f"{permission_gate_function}\nattest_permission_state",
                ],
                check=False,
                capture_output=True,
                text=True,
                env=env,
            )

        permission_id = "b" * 32
        permission_match = run_permission_gate(permission_id, permission_id)
        self.assertEqual(permission_match.returncode, 0, permission_match.stderr)
        self.assertIn("benchmark permission state matched", permission_match.stderr)

        permission_mismatch = run_permission_gate("a" * 32, permission_id)
        self.assertNotEqual(permission_mismatch.returncode, 0)
        self.assertIn("trace permission state=", permission_mismatch.stderr)
        self.assertIn("different startup scenario", permission_mismatch.stderr)

    def test_trace_requires_benchmark_performance_mode_and_warmup(self) -> None:
        capture_path = HARNESS / "capture_trace.sh"
        capture = capture_path.read_text(encoding="utf-8")
        self.assertIn('EXPECTED_PERF_MODE="${EXPECTED_PERF_MODE:-}"', capture)
        self.assertIn('EXPECTED_WARMUP="${EXPECTED_WARMUP:-}"', capture)
        self.assertIn('[ "$DD_PERF_MODE" = "$EXPECTED_PERF_MODE" ]', capture)
        # `warmup` is DERIVED, not accepted twice. The old pair -- WARMUP defaulting
        # to EXPECTED_WARMUP and then checked against it -- compared two operator
        # inputs, so a consistently wrong pair passed and the only thing the check
        # added was a second place to mistype the value.
        self.assertIn("SETTLE_LAUNCHES=$((EXPECTED_WARMUP + 1))", capture)
        self.assertNotIn('WARMUP="${WARMUP:-$EXPECTED_WARMUP}"', capture)
        self.assertNotIn('[ "$WARMUP" = "$EXPECTED_WARMUP" ]', capture)
        self.assertNotIn("post-install launch ordinal", capture)

        base_env = os.environ.copy()
        for name in ("EXPECTED_PERF_MODE", "EXPECTED_WARMUP", "WARMUP",
                     "ANIMATIONS", "AIRPLANE"):
            base_env.pop(name, None)
        base_env.update(
            PKG="com.example.app",
            EXPECTED_ANIMATIONS="1",
            EXPECTED_AIRPLANE="0",
            EXPECTED_FP="motorola/lisbon/lisbon:12/S3RQS/rel:user/release-keys",
            EXPECTED_ANDROID_USER="0",
            EXPECTED_LAUNCHER="com.example.app/.MainActivity",
            EXPECTED_SDK_LIVENESS="1",
            EXPECTED_APK_MD5="a" * 32,
            EXPECTED_PERMISSION_STATE_ID="b" * 32,
            EXPECTED_COMPILE_STATUS="verify",
        )

        missing_perf = subprocess.run(
            ["bash", str(capture_path), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=base_env,
        )
        self.assertNotEqual(missing_perf.returncode, 0)
        self.assertIn("set EXPECTED_PERF_MODE", missing_perf.stderr)

        base_env["EXPECTED_PERF_MODE"] = "turbo"
        malformed_perf = subprocess.run(
            ["bash", str(capture_path), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=base_env,
        )
        self.assertNotEqual(malformed_perf.returncode, 0)
        self.assertIn("EXPECTED_PERF_MODE must be fixed or dynamic", malformed_perf.stderr)

        base_env["EXPECTED_PERF_MODE"] = "fixed"
        missing_warmup = subprocess.run(
            ["bash", str(capture_path), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=base_env,
        )
        self.assertNotEqual(missing_warmup.returncode, 0)
        self.assertIn("set EXPECTED_WARMUP", missing_warmup.stderr)

        base_env["EXPECTED_WARMUP"] = "three"
        malformed_warmup = subprocess.run(
            ["bash", str(capture_path), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=base_env,
        )
        self.assertNotEqual(malformed_warmup.returncode, 0)
        self.assertIn("EXPECTED_WARMUP must be a non-negative integer", malformed_warmup.stderr)

        base_env["EXPECTED_WARMUP"] = "0"
        inherited_warmup = subprocess.run(
            ["bash", str(capture_path), "/missing.apk", "trace", "1"],
            check=False,
            capture_output=True,
            text=True,
            env=base_env,
        )
        self.assertNotEqual(inherited_warmup.returncode, 0)
        self.assertIn("APK not found", inherited_warmup.stderr)
        self.assertNotIn("post-install launch ordinal", inherited_warmup.stderr)

        # Refused rather than ignored, and refused even when it AGREES with the
        # benchmark: it is no longer an input, and a silently discarded environment
        # variable is how a run lost RUNS and BLOCKS to a positional signature.
        # An explicitly empty value is caught too, which `${WARMUP:-}` would not.
        for leftover in ("3", "9", ""):
            base_env["WARMUP"] = leftover
            rejected_warmup = subprocess.run(
                ["bash", str(capture_path), "/missing.apk", "trace", "1"],
                check=False,
                capture_output=True,
                text=True,
                env=base_env,
            )
            self.assertNotEqual(rejected_warmup.returncode, 0, leftover)
            self.assertIn("WARMUP is not an input", rejected_warmup.stderr)
            self.assertIn("derived from\n       EXPECTED_WARMUP", rejected_warmup.stderr)
            # The settle count is never silently taken from it.
            self.assertNotIn("settling:", rejected_warmup.stderr)
        del base_env["WARMUP"]

        gate_start = capture.index("attest_performance_mode() {")
        gate_end = capture.index("\n}\n", gate_start) + 3
        performance_gate_function = capture[gate_start:gate_end]

        def run_performance_gate(
            actual: str,
            expected: str,
        ) -> subprocess.CompletedProcess[str]:
            env = os.environ.copy()
            env.update(DD_PERF_MODE=actual, EXPECTED_PERF_MODE=expected)
            return subprocess.run(
                [
                    "bash",
                    "-c",
                    "die() { echo \"FATAL: $*\" >&2; exit 1; }; "
                    "log() { echo \"$*\" >&2; };\n"
                    f"{performance_gate_function}\nattest_performance_mode",
                ],
                check=False,
                capture_output=True,
                text=True,
                env=env,
            )

        for mode in ("fixed", "dynamic"):
            matched = run_performance_gate(mode, mode)
            self.assertEqual(matched.returncode, 0, matched.stderr)
            self.assertIn(f"benchmark performance mode matched: {mode}", matched.stderr)

        for actual, expected in (("fixed", "dynamic"), ("dynamic", "fixed")):
            mismatched = run_performance_gate(actual, expected)
            self.assertNotEqual(mismatched.returncode, 0)
            self.assertIn(f"trace achieved perf_mode={actual}", mismatched.stderr)
            self.assertIn(
                f"benchmark recorded\n         perf_mode={expected}",
                mismatched.stderr,
            )

        enable = capture.index("\ndd_enable_fixed_performance_mode || exit 2")
        ownership = capture.index("\n  _WE_SET_PERF=1", enable)
        attestation = capture.index("\nattest_performance_mode\n", ownership)
        dexopt = capture.index("\ndd_disable_background_dexopt || exit 2", attestation)
        self.assertLess(enable, ownership)
        self.assertLess(ownership, attestation)
        self.assertLess(attestation, dexopt)

    def test_trace_requires_benchmark_scenario_controls_and_device(self) -> None:
        """The controls a trace can silently differ on, bound like WARMUP.

        `animations`, `airplane` and `fp` are all _MUST_MATCH keys: ab_stats.py
        refuses to POOL two CSVs that disagree on them. A trace exists to explain one
        of those CSVs, and could disagree with it unchecked -- `ANIMATIONS` and
        `AIRPLANE` defaulted to 0 while the guide recommends ANIMATIONS=1 as the
        honest per-frame measurement, and nothing compared the device at all.
        """
        capture_path = HARNESS / "capture_trace.sh"
        capture = capture_path.read_text(encoding="utf-8")
        for decl in ('EXPECTED_ANIMATIONS="${EXPECTED_ANIMATIONS:-}"',
                     'ANIMATIONS="${ANIMATIONS:-$EXPECTED_ANIMATIONS}"',
                     'EXPECTED_AIRPLANE="${EXPECTED_AIRPLANE:-}"',
                     'AIRPLANE="${AIRPLANE:-$EXPECTED_AIRPLANE}"',
                     'EXPECTED_FP="${EXPECTED_FP:-}"',
                     '[ "$ANIMATIONS" = "$EXPECTED_ANIMATIONS" ]',
                     '[ "$AIRPLANE" = "$EXPECTED_AIRPLANE" ]',
                     '[ "$DEV_FP" = "$EXPECTED_FP" ]'):
            self.assertIn(decl, capture)
        # No silent fallback survives for either scale.
        self.assertNotIn('ANIMATIONS="${ANIMATIONS:-0}"', capture)
        self.assertNotIn('AIRPLANE="${AIRPLANE:-0}"', capture)

        # The device is checked before anything is installed or uninstalled, so a
        # wrong-device capture cannot wipe the app's data on the way to failing.
        fp_gate = capture.index('[ "$DEV_FP" = "$EXPECTED_FP" ]')
        self.assertLess(fp_gate, capture.index("dd_ensure_uninstalled", fp_gate))
        self.assertLess(fp_gate, capture.index('"$ADB" install', fp_gate))

        base_env = os.environ.copy()
        for name in ("EXPECTED_ANIMATIONS", "EXPECTED_AIRPLANE", "EXPECTED_FP",
                     "ANIMATIONS", "AIRPLANE", "WARMUP"):
            base_env.pop(name, None)
        base_env.update(
            PKG="com.example.app",
            EXPECTED_APK_MD5="a" * 32,
            EXPECTED_PERMISSION_STATE_ID="b" * 32,
            EXPECTED_COMPILE_STATUS="verify",
            EXPECTED_PERF_MODE="fixed",
            EXPECTED_WARMUP="3",
            EXPECTED_ANDROID_USER="0",
            EXPECTED_LAUNCHER="com.example.app/.MainActivity",
            EXPECTED_SDK_LIVENESS="1",
        )

        def run(**overrides):
            env = dict(base_env)
            env.update(overrides)
            return subprocess.run(
                ["bash", str(capture_path), "/missing.apk", "trace", "1"],
                check=False, capture_output=True, text=True, env=env,
            )

        for missing, needle in (
            (dict(EXPECTED_AIRPLANE="0", EXPECTED_FP="fp1"), "set EXPECTED_ANIMATIONS"),
            (dict(EXPECTED_ANIMATIONS="1", EXPECTED_FP="fp1"), "set EXPECTED_AIRPLANE"),
            (dict(EXPECTED_ANIMATIONS="1", EXPECTED_AIRPLANE="0"), "set EXPECTED_FP"),
        ):
            result = run(**missing)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(needle, result.stderr)

        complete = dict(EXPECTED_ANIMATIONS="1", EXPECTED_AIRPLANE="0",
                        EXPECTED_FP="motorola/lisbon/lisbon:12/S3RQS/rel:user/release-keys")
        for bad, needle in (
            (dict(EXPECTED_ANIMATIONS="yes"), "EXPECTED_ANIMATIONS must be 0 or 1"),
            (dict(EXPECTED_AIRPLANE="2"), "EXPECTED_AIRPLANE must be 0 or 1"),
            (dict(EXPECTED_FP="has space"), "invalid EXPECTED_FP"),
        ):
            result = run(**{**complete, **bad})
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(needle, result.stderr)

        # A real fingerprint carries '/', ':', '.' and '-'; the charset must accept
        # one, or every capture would be refused.
        accepted = run(**complete)
        self.assertNotIn("invalid EXPECTED_FP", accepted.stderr)

        # An explicit disagreement aborts before device access. Unlike `warmup`,
        # these two are applied to the device and read back, so they stay inputs.
        for override, needle in (
            (dict(ANIMATIONS="0"), "would not be the same scenario"),
            (dict(AIRPLANE="1"), "must be the one the A/B measured"),
        ):
            result = run(**{**complete, **override})
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(needle, result.stderr)

        # Unset, each takes the benchmark's value rather than a default of 0.
        inherited = run(**complete)
        self.assertIn("APK not found", inherited.stderr)
        self.assertNotIn("same scenario", inherited.stderr)

    def test_trace_binds_app_endpoint_liveness_and_android_user(self) -> None:
        capture_path = HARNESS / "capture_trace.sh"
        capture = capture_path.read_text(encoding="utf-8")
        for declaration in (
            'EXPECTED_APP_TRACE_ID="${EXPECTED_APP_TRACE_ID:-}"',
            'EXPECTED_SDK_LIVENESS="${EXPECTED_SDK_LIVENESS:-}"',
            'EXPECTED_ANDROID_USER="${EXPECTED_ANDROID_USER:-}"',
            '[ "$TRACE_APP_TRACE_ID" = "$EXPECTED_APP_TRACE_ID" ]',
            '[ "$EXPECT_DD" = "$EXPECTED_SDK_LIVENESS" ]',
            '[ "$DD_ANDROID_USER" = "$EXPECTED_ANDROID_USER" ]',
        ):
            self.assertIn(declaration, capture)
        user_gate = capture.index('[ "$DD_ANDROID_USER" = "$EXPECTED_ANDROID_USER" ]')
        self.assertLess(user_gate, capture.index('dd_reserve_output_files "$TRACE_FILE"'))
        self.assertLess(user_gate, capture.index('dd_apply_animation_scales "$ANIMATIONS"'))

        base_env = os.environ.copy()
        for name in (
            "ANDROID_SERIAL", "ANIMATIONS", "AIRPLANE", "WARMUP",
            "EXPECTED_ANDROID_USER", "EXPECTED_SDK_LIVENESS",
            "EXPECTED_APP_TRACE_ID", "APP_TRACE_REGEX", "TRACE_ENDPOINT",
            "EXPECTED_LAUNCHER",
        ):
            base_env.pop(name, None)
        base_env.update(
            PKG="com.example.app",
            EXPECTED_APK_MD5="a" * 32,
            EXPECTED_PERMISSION_STATE_ID="b" * 32,
            EXPECTED_COMPILE_STATUS="verify",
            EXPECTED_PERF_MODE="fixed",
            EXPECTED_WARMUP="3",
            EXPECTED_ANIMATIONS="1",
            EXPECTED_AIRPLANE="0",
            EXPECTED_FP="build/fingerprint",
            EXPECTED_LAUNCHER="com.example.app/.MainActivity",
        )

        def run(env_overrides=None, expect="1", apk="/missing.apk"):
            env = dict(base_env)
            env.update(env_overrides or {})
            return subprocess.run(
                ["bash", str(capture_path), apk, "trace", expect],
                check=False,
                capture_output=True,
                text=True,
                env=env,
            )

        missing_user = run({"EXPECTED_SDK_LIVENESS": "1"})
        self.assertIn("set EXPECTED_ANDROID_USER", missing_user.stderr)
        malformed_user = run({
            "EXPECTED_ANDROID_USER": "owner",
            "EXPECTED_SDK_LIVENESS": "1",
        })
        self.assertIn("EXPECTED_ANDROID_USER must be a non-negative integer",
                      malformed_user.stderr)

        missing_liveness = run({"EXPECTED_ANDROID_USER": "0"})
        self.assertIn("set EXPECTED_SDK_LIVENESS", missing_liveness.stderr)
        malformed_liveness = run({
            "EXPECTED_ANDROID_USER": "0",
            "EXPECTED_SDK_LIVENESS": "yes",
        })
        self.assertIn("EXPECTED_SDK_LIVENESS must be 0 or 1", malformed_liveness.stderr)
        mismatched_liveness = run({
            "EXPECTED_ANDROID_USER": "0",
            "EXPECTED_SDK_LIVENESS": "0",
        })
        self.assertIn("selected benchmark arm recorded", mismatched_liveness.stderr)

        complete = {"EXPECTED_ANDROID_USER": "0", "EXPECTED_SDK_LIVENESS": "1"}
        regex = "cold launch complete: [0-9]+"
        regex_id = hashlib.md5(regex.encode()).hexdigest()
        missing_app_id = run({
            **complete,
            "TRACE_ENDPOINT": "app_trace_ms",
            "APP_TRACE_REGEX": regex,
        })
        self.assertIn("set EXPECTED_APP_TRACE_ID", missing_app_id.stderr)
        malformed_app_id = run({
            **complete,
            "TRACE_ENDPOINT": "app_trace_ms",
            "APP_TRACE_REGEX": regex,
            "EXPECTED_APP_TRACE_ID": "not-an-md5",
        })
        self.assertIn("invalid EXPECTED_APP_TRACE_ID", malformed_app_id.stderr)
        mismatched_app_id = run({
            **complete,
            "TRACE_ENDPOINT": "app_trace_ms",
            "APP_TRACE_REGEX": regex,
            "EXPECTED_APP_TRACE_ID": "c" * 32,
        })
        self.assertIn(f"APP_TRACE_REGEX hashes to {regex_id}", mismatched_app_id.stderr)
        matched_app_id = run({
            **complete,
            "TRACE_ENDPOINT": "app_trace_ms",
            "APP_TRACE_REGEX": regex,
            "EXPECTED_APP_TRACE_ID": regex_id,
        })
        self.assertIn("APK not found", matched_app_id.stderr)

        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "arm.apk"
            apk.write_bytes(b"benchmarked apk")
            fake_adb = Path(tmp) / "adb"
            fake_adb.write_text(
                "#!/usr/bin/env bash\n"
                "case \"$*\" in\n"
                "  devices) printf 'List of devices attached\\nserial\\tdevice\\n' ;;\n"
                "  'shell am get-current-user') printf '10\\n' ;;\n"
                "  *) exit 44 ;;\n"
                "esac\n",
                encoding="utf-8",
            )
            fake_adb.chmod(0o755)
            wrong_user = run({
                **complete,
                "ADB": str(fake_adb),
                "EXPECTED_APK_MD5": hashlib.md5(apk.read_bytes()).hexdigest(),
            }, apk=str(apk))
        self.assertIn("active Android user is 10", wrong_user.stderr)
        self.assertIn("benchmark\n       recorded android_user=0", wrong_user.stderr)

    BENCHMARK_HEADER = (
        "# device=moto_g_60_s sdk=31 abi=arm64-v8a emulator=0 android_user=0 "
        "compile_filter=speed-profile compile_status=verify perf_mode=fixed blocks=8 "
        "runs=4 warmup=3 animations=1 "
        "fp=motorola/lisbon/lisbon:12/S3RQS/rel:user/release-keys "
        "launcher=com.example.app/.Main airplane=0 baseline_md5=" + "a" * 32
        + " treatment_md5=" + "c" * 32 + " label_a=A_noDD label_b=B_withDD "
        "expect_a=0 expect_b=1 app_trace_id=none\n"
        "# permission_a=" + "d" * 32 + "\n# permission_b=" + "e" * 32 + "\n"
    )
    BENCHMARK_ROWS = (
        "label,block,pos_in_block,phase,run,total_ms,launch_state,status,foreground,ttfd\n"
        "A_noDD,1,1,measure,1,100,COLD,ok,ok,100\n"
        "# RUN COMPLETE\n"
    )

    def test_trace_binds_to_the_benchmark_csv_instead_of_transcription(self) -> None:
        """One binding replaces twelve hand-copied values, and refuses rather than guess.

        Two of those values are 32-character digests and one is a build fingerprint;
        a mistyped one aborted with a message indistinguishable from a real mismatch.
        Reading them from the run's own header removes the transcription without
        moving a single gate: a conflicting explicit value is rejected as stale.
        """
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "t.apk"
            apk.write_text("apk", encoding="utf-8")
            good = root / "results.csv"
            good.write_text(self.BENCHMARK_HEADER + self.BENCHMARK_ROWS, encoding="utf-8")
            pooled = root / "pooled.csv"
            pooled.write_text(self.BENCHMARK_HEADER + self.BENCHMARK_HEADER
                              + self.BENCHMARK_ROWS, encoding="utf-8")
            aborted = root / "aborted.csv"
            # Built WITHOUT the completion marker, like the harness leaves it: the
            # EXIT trap stamps RUN ABORTED instead of reaching the completion write,
            # so no real file carries both. Carrying both made this case pass only
            # because lib.sh happens to test ABORTED before COMPLETE, and the
            # assertion would have silently changed meaning if that order changed.
            aborted_body = (self.BENCHMARK_HEADER
                            + self.BENCHMARK_ROWS.replace("# RUN COMPLETE\n", "")
                            + "# RUN ABORTED (exit 1) -- dialog\n")
            assert "# RUN COMPLETE" not in aborted_body
            aborted.write_text(aborted_body, encoding="utf-8")
            incomplete_format = root / "incomplete-format.csv"
            incomplete_format.write_text(
                self.BENCHMARK_HEADER.replace(" perf_mode=fixed", "")
                + self.BENCHMARK_ROWS, encoding="utf-8")
            interrupted = root / "interrupted.csv"
            interrupted.write_text(
                self.BENCHMARK_HEADER
                + self.BENCHMARK_ROWS.replace("# RUN COMPLETE\n", ""),
                encoding="utf-8",
            )
            duplicate_completion = root / "duplicate-completion.csv"
            duplicate_completion.write_text(
                self.BENCHMARK_HEADER + self.BENCHMARK_ROWS + "# RUN COMPLETE\n",
                encoding="utf-8",
            )

            def run(expect_dd="1", **env_overrides):
                env = os.environ.copy()
                for name in ("WARMUP", "ANIMATIONS", "AIRPLANE", "COMPILE_FILTER",
                             "TRACE_ENDPOINT", "APP_TRACE_REGEX"):
                    env.pop(name, None)
                for name in list(env):
                    if name.startswith("EXPECTED_"):
                        env.pop(name, None)
                env.update(PKG="com.example.app", AAPT2="/nonexistent")
                env.update(env_overrides)
                return subprocess.run(
                    ["bash", str(HARNESS / "capture_trace.sh"), str(apk), "trace", expect_dd],
                    check=False, capture_output=True, text=True, env=env,
                )

            # Refusals, each naming what it saw.
            for env, needle in (
                (dict(BENCHMARK_CSV=str(pooled), BENCHMARK_ARM="B_withDD"),
                 "pools more"),
                (dict(BENCHMARK_CSV=str(aborted), BENCHMARK_ARM="B_withDD"),
                 "is an aborted run"),
                (dict(BENCHMARK_CSV=str(apk), BENCHMARK_ARM="B_withDD"),
                 "not a benchmark"),
                (dict(BENCHMARK_CSV=str(root / "absent.csv"), BENCHMARK_ARM="B_withDD"),
                 "benchmark CSV not found"),
                (dict(BENCHMARK_CSV=str(good), BENCHMARK_ARM="B_wrong"),
                 "is not an arm of"),
                (dict(BENCHMARK_CSV=str(good)), "set BENCHMARK_ARM"),
                (dict(BENCHMARK_ARM="B_withDD"), "there is no header"),
                (dict(BENCHMARK_CSV=str(interrupted), BENCHMARK_ARM="B_withDD"),
                 "RUN COMPLETE"),
                (dict(BENCHMARK_CSV=str(duplicate_completion), BENCHMARK_ARM="B_withDD"),
                 "has 2 '# RUN COMPLETE' markers"),
                # There is no unreleased legacy-format contract. Named, not invented.
                (dict(BENCHMARK_CSV=str(incomplete_format), BENCHMARK_ARM="B_withDD"),
                 "records no perf_mode"),
            ):
                result = run(**env)
                self.assertNotEqual(result.returncode, 0, needle)
                self.assertIn(needle, result.stderr)

            # The arm is selected BY LABEL, so the treatment digest is the one gated.
            treatment = run(BENCHMARK_CSV=str(good), BENCHMARK_ARM="B_withDD")
            self.assertIn(f"bound to {good} arm B_withDD (b)", treatment.stderr)
            self.assertIn("c" * 32, treatment.stderr)
            self.assertNotIn("a" * 32, treatment.stderr)

            # ...and the baseline arm carries expect_a=0, which the positional
            # argument must agree with.
            baseline = run(BENCHMARK_CSV=str(good), BENCHMARK_ARM="A_noDD")
            self.assertIn("expect-datadog=1, but the selected benchmark arm", baseline.stderr)

            # A bound CSV is authoritative; a stale explicit value cannot select a
            # different binary while still calling the trace bound to this arm.
            override = run(BENCHMARK_CSV=str(good), BENCHMARK_ARM="B_withDD",
                           EXPECTED_APK_MD5="f" * 32)
            self.assertNotEqual(override.returncode, 0)
            self.assertIn("conflict with the bound CSV", override.stderr)
            self.assertIn("f" * 32, override.stderr)
            self.assertIn("c" * 32, override.stderr)

            # `app_trace_id=none` is a run with no app-owned endpoint, not a malformed
            # digest; the md5 charset error pointed at the wrong problem entirely.
            no_endpoint = run(BENCHMARK_CSV=str(good), BENCHMARK_ARM="B_withDD",
                              TRACE_ENDPOINT="app_trace_ms", APP_TRACE_REGEX="x [0-9]+")
            self.assertIn("ran without", no_endpoint.stderr)
            self.assertIn("no app-owned endpoint", no_endpoint.stderr)
            self.assertNotIn("hexadecimal", no_endpoint.stderr)

            capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
            launcher_shape = capture.index('case "$ACT" in')
            launcher_binding = capture.index('[ "$ACT" != "$EXPECTED_LAUNCHER" ]')
            self.assertLess(launcher_shape, launcher_binding)
            self.assertIn("trace would exercise a different cold-start", capture)

    UNBOUND_IDENTITIES = dict(
        EXPECTED_APK_MD5="a" * 32,
        EXPECTED_PERMISSION_STATE_ID="b" * 32,
        EXPECTED_COMPILE_STATUS="verify",
        EXPECTED_PERF_MODE="fixed",
        EXPECTED_WARMUP="3",
        EXPECTED_ANIMATIONS="1",
        EXPECTED_AIRPLANE="0",
        EXPECTED_FP="motorola/lisbon/lisbon:12/S3RQS/rel:user/release-keys",
        EXPECTED_ANDROID_USER="0",
        EXPECTED_LAUNCHER="com.example.app/.MainActivity",
        EXPECTED_SDK_LIVENESS="1",
    )

    def test_trace_derives_the_identities_that_have_no_observable(self) -> None:
        """Two of the twelve identities are derived because they cannot be attested.

        Ten are each compared against an independent observable: the file's own
        digest, the device, or the state the script achieved. `warmup` and the SDK
        expectation were compared against a second operator-supplied value, so a
        consistently wrong pair passed and the docs presented all eleven uniformly.
        Both are derived from the bound CSV now, the positional survives for the
        unbound path where it is the only thing that catches the other arm's
        expectation, and the output names what rests on derivation rather than
        observation.
        """
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        # Optional, not defaulted: `${3:?}` aborted before the header could supply it.
        self.assertIn('EXPECT_DD="${3:-}"', capture)
        self.assertNotIn('EXPECT_DD="${3:?}"', capture)
        self.assertIn('[ "$EXPECT_DD" = "$EXPECTED_SDK_LIVENESS" ]', capture)
        # Derivation happens after the header read and before the value is validated,
        # so a derived value faces exactly the gates a typed one does.
        header_read = capture.index("dd_read_benchmark_header")
        derivation = capture.index('EXPECT_DD="$EXPECTED_SDK_LIVENESS"', header_read)
        self.assertLess(derivation,
                        capture.index('case "$EXPECT_DD" in 0|1)', derivation))

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "t.apk"
            apk.write_text("apk", encoding="utf-8")
            csv = root / "results.csv"
            csv.write_text(self.BENCHMARK_HEADER + self.BENCHMARK_ROWS, encoding="utf-8")

            def run(*positional, **env_overrides):
                env = os.environ.copy()
                for name in ("WARMUP", "ANIMATIONS", "AIRPLANE", "COMPILE_FILTER",
                             "TRACE_ENDPOINT", "APP_TRACE_REGEX", "BENCHMARK_CSV",
                             "BENCHMARK_ARM"):
                    env.pop(name, None)
                for name in list(env):
                    if name.startswith("EXPECTED_"):
                        env.pop(name, None)
                env.update(PKG="com.example.app", AAPT2="/nonexistent",
                           ADB="/nonexistent-adb")
                env.update(env_overrides)
                return subprocess.run(
                    ["bash", str(HARNESS / "capture_trace.sh"), str(apk), "trace",
                     *positional],
                    check=False, capture_output=True, text=True, env=env,
                )

            bound = dict(BENCHMARK_CSV=str(csv))

            # Bound and omitted: each arm derives its OWN stamp, not a default.
            for arm, expect in (("B_withDD", "1"), ("A_noDD", "0")):
                derived = run(**bound, BENCHMARK_ARM=arm)
                self.assertIn(f"expect-datadog={expect} are", derived.stderr, arm)
                self.assertIn("derived from the bound CSV", derived.stderr, arm)
                self.assertIn("warmup=3", derived.stderr, arm)
                # Neither the old hard `${3:?}` abort nor a wrong-arm refusal.
                self.assertNotIn("parameter null or not set", derived.stderr, arm)
                self.assertNotIn("must prove the same SDK runtime state", derived.stderr, arm)

            # Bound and contradicted: still refused, so the positional is an override
            # that faces its comparison rather than being ignored once derivable.
            contradicted = run("1", **bound, BENCHMARK_ARM="A_noDD")
            self.assertNotEqual(contradicted.returncode, 0)
            self.assertIn("must prove the same SDK runtime state", contradicted.stderr)
            agreed = run("0", **bound, BENCHMARK_ARM="A_noDD")
            self.assertNotIn("must prove the same SDK runtime state", agreed.stderr)
            malformed = run("yes", **bound, BENCHMARK_ARM="A_noDD")
            self.assertNotEqual(malformed.returncode, 0)
            self.assertIn("expect-datadog must be 0 or 1", malformed.stderr)

            # Unbound and omitted: refused. There is nothing to derive it from, and
            # defaulting it would pick one arm's expectation for the other's APK.
            unbound_missing = run(**self.UNBOUND_IDENTITIES)
            self.assertNotEqual(unbound_missing.returncode, 0)
            self.assertIn("required for an unbound capture", unbound_missing.stderr)
            self.assertNotIn("no trace-time observable", unbound_missing.stderr)

            # Unbound and supplied: accepted, and reported as asserted rather than
            # verified, because both values are the operator's.
            unbound = run("1", **self.UNBOUND_IDENTITIES)
            self.assertIn("no CSV to derive from", unbound.stderr)
            self.assertIn("asserted, not verified", unbound.stderr)
            self.assertNotIn("(header)", unbound.stderr)
            # One-valued labels were removed with the source variables: a bound
            # capture cannot report anything but derived-from-the-CSV.
            capture_src = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
            self.assertNotIn("_WARMUP_SOURCE", capture_src)
            self.assertNotIn("_LIVENESS_SOURCE", capture_src)

            # Bound but contradicted: explicit input cannot replace the arm metadata.
            overridden = run(**bound, BENCHMARK_ARM="B_withDD",
                             EXPECTED_WARMUP="5", EXPECTED_SDK_LIVENESS="1")
            self.assertNotEqual(overridden.returncode, 0)
            self.assertIn("EXPECTED_WARMUP='5'", overridden.stderr)
            self.assertIn("bound CSV is authoritative", overridden.stderr)

            # WARMUP is refused on the bound path too, including when it agrees with
            # the header: it is not an input, and silence would hand the operator a
            # settle count they did not choose.
            for leftover in ("3", "9", ""):
                refused = run(**bound, BENCHMARK_ARM="B_withDD", WARMUP=leftover)
                self.assertNotEqual(refused.returncode, 0, leftover)
                self.assertIn("WARMUP is not an input", refused.stderr)
                self.assertNotIn("no trace-time observable", refused.stderr)

    def test_launcher_identity_is_required_and_compared_on_every_path(self) -> None:
        """The entry point is an OBSERVABLE, so an unbound capture can attest it too.

        Gating the comparison on BENCHMARK_CSV accepted EXPECTED_LAUNCHER on the
        unbound path and never compared it, while the usage block listed it among
        the values to give by hand. That is the WARMUP trap: a documented input,
        silently discarded. The device resolves the component either way, so the
        gate belongs to the expectation, not to where the expectation came from.
        """
        capture_path = HARNESS / "capture_trace.sh"
        capture = capture_path.read_text(encoding="utf-8")
        self.assertIn('if [ "$ACT" != "$EXPECTED_LAUNCHER" ]; then', capture)
        self.assertNotIn('[ -n "$BENCHMARK_CSV" ] && [ "$ACT" != "$EXPECTED_LAUNCHER" ]',
                         capture)

        # The gate itself, both directions, without a device.
        gate_start = capture.index('if [ "$ACT" != "$EXPECTED_LAUNCHER" ]; then')
        # Through the success log, so the quiet direction is exercised too and not
        # merely the refusal: a gate that never confirms a match is a gate whose
        # passing path nothing reads.
        gate_end = capture.index("\n", capture.index(
            'log "benchmark launcher matched', gate_start)) + 1
        gate = capture[gate_start:gate_end]

        def run_gate(act: str, expected: str) -> subprocess.CompletedProcess[str]:
            env = os.environ.copy()
            env.update(ACT=act, EXPECTED_LAUNCHER=expected)
            return subprocess.run(
                ["bash", "-c",
                 'die() { echo "FATAL: $*" >&2; exit 1; }; '
                 'log() { echo "$*" >&2; };\n' + gate],
                check=False, capture_output=True, text=True, env=env,
            )

        matched = run_gate("com.example.app/.Main", "com.example.app/.Main")
        self.assertEqual(matched.returncode, 0, matched.stderr)
        self.assertIn("benchmark launcher matched", matched.stderr)

        # An activity-alias change keeps the application id and moves the entry point.
        mismatched = run_gate("com.example.app/.SplashAlias", "com.example.app/.Main")
        self.assertNotEqual(mismatched.returncode, 0)
        self.assertIn("trace launcher=com.example.app/.SplashAlias", mismatched.stderr)
        self.assertIn("different cold-start", mismatched.stderr)

        # And the preflight, on the UNBOUND path, where it used to do nothing.
        base_env = os.environ.copy()
        for name in ("WARMUP", "ANIMATIONS", "AIRPLANE", "BENCHMARK_CSV",
                     "BENCHMARK_ARM"):
            base_env.pop(name, None)
        base_env.update(PKG="com.example.app", **self.UNBOUND_IDENTITIES)

        def run_unbound(**overrides):
            env = dict(base_env)
            for key, value in overrides.items():
                if value is None:
                    env.pop(key, None)
                else:
                    env[key] = value
            return subprocess.run(
                ["bash", str(capture_path), "/missing.apk", "trace", "1"],
                check=False, capture_output=True, text=True, env=env,
            )

        missing = run_unbound(EXPECTED_LAUNCHER=None)
        self.assertNotEqual(missing.returncode, 0)
        self.assertIn("set EXPECTED_LAUNCHER", missing.stderr)

        # A component of another application id cannot be what this run resolves,
        # so it is a typo rather than a mismatch, and it aborts before install.
        for bad in ("com.other.app/.Main", ".Main", "com.example.app", "com.example.app/"):
            wrong_shape = run_unbound(EXPECTED_LAUNCHER=bad)
            self.assertNotEqual(wrong_shape.returncode, 0, bad)
            self.assertIn("has to start with 'com.example.app/'", wrong_shape.stderr, bad)

        # Supplied and well-formed: preflight passes it through to the APK check.
        accepted = run_unbound()
        self.assertIn("APK not found", accepted.stderr)

    def test_launcher_is_required_of_a_bound_csv_header(self) -> None:
        """A CSV without `launcher=` is not a current-format file, and is named."""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "t.apk"
            apk.write_text("apk", encoding="utf-8")
            csv_path = root / "results.csv"
            csv_path.write_text(
                self.BENCHMARK_HEADER.replace("launcher=com.example.app/.Main ", "")
                + self.BENCHMARK_ROWS, encoding="utf-8")
            env = os.environ.copy()
            for name in ("WARMUP", "ANIMATIONS", "AIRPLANE", "COMPILE_FILTER"):
                env.pop(name, None)
            for name in list(env):
                if name.startswith("EXPECTED_"):
                    env.pop(name, None)
            env.update(PKG="com.example.app", AAPT2="/nonexistent",
                       BENCHMARK_CSV=str(csv_path), BENCHMARK_ARM="B_withDD")
            result = subprocess.run(
                ["bash", str(HARNESS / "capture_trace.sh"), str(apk), "trace"],
                check=False, capture_output=True, text=True, env=env,
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("records no launcher", result.stderr)

    def test_an_input_set_to_the_empty_string_is_refused(self) -> None:
        """`${VAR:-}` cannot distinguish "unset" from "expanded to nothing".

        Every identity is declared as `${VAR:-}` before lib.sh runs, so by binding
        time an operator-supplied empty string is indistinguishable from a declared
        one and took the CSV's value silently. The substituted value is the
        authoritative one, so nothing measured came out wrong; what was lost is the
        operator learning their variable was empty. `BENCHMARK_CSV=` was worse: it
        silently unbound a capture written to be bound.
        """
        capture_path = HARNESS / "capture_trace.sh"
        capture = capture_path.read_text(encoding="utf-8")
        # Load-bearing ordering: after the declarations, EVERY identity is set-and-
        # empty and the guard would refuse every capture.
        guard = capture.index("for _empty_input in ${!EXPECTED_@}")
        first_declaration = capture.index('EXPECTED_APK_MD5="${EXPECTED_APK_MD5:-}"')
        self.assertLess(guard, first_declaration)
        # Prefix expansion, not a list: a future EXPECTED_* cannot be forgotten.
        self.assertIn("${!EXPECTED_@} BENCHMARK_CSV BENCHMARK_ARM", capture)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "t.apk"
            apk.write_text("apk", encoding="utf-8")
            csv_path = root / "results.csv"
            csv_path.write_text(self.BENCHMARK_HEADER + self.BENCHMARK_ROWS,
                                encoding="utf-8")

            def run(*positional, **env_overrides):
                env = os.environ.copy()
                for name in ("WARMUP", "ANIMATIONS", "AIRPLANE", "COMPILE_FILTER",
                             "TRACE_ENDPOINT", "APP_TRACE_REGEX"):
                    env.pop(name, None)
                for name in list(env):
                    if name.startswith("EXPECTED_"):
                        env.pop(name, None)
                env.update(PKG="com.example.app", AAPT2="/nonexistent",
                           ADB="/nonexistent-adb", BENCHMARK_CSV=str(csv_path),
                           BENCHMARK_ARM="B_withDD")
                env.update(env_overrides)
                return subprocess.run(
                    ["bash", str(capture_path), str(apk), "trace", *positional],
                    check=False, capture_output=True, text=True, env=env,
                )

            # An identity the CSV would otherwise have supplied.
            for name in ("EXPECTED_APK_MD5", "EXPECTED_WARMUP", "EXPECTED_LAUNCHER",
                         "EXPECTED_SDK_LIVENESS"):
                empty = run(**{name: ""})
                self.assertNotEqual(empty.returncode, 0, name)
                self.assertIn(f"{name} is set to the empty string", empty.stderr)
                self.assertNotIn("benchmark APK digest matched", empty.stderr)

            # The binding itself: empty used to select the unbound path in silence.
            unbound_by_accident = run("1", BENCHMARK_CSV="", BENCHMARK_ARM="")
            self.assertNotEqual(unbound_by_accident.returncode, 0)
            self.assertIn("BENCHMARK_CSV is set to the empty string",
                          unbound_by_accident.stderr)

            # A misspelled identity is caught by the same guard rather than ignored.
            misspelled = run(EXPECTED_LAUNCHR="")
            self.assertNotEqual(misspelled.returncode, 0)
            self.assertIn("EXPECTED_LAUNCHR is set to the empty string",
                          misspelled.stderr)

            # Quiet on the good input: unset stays unset, and the run proceeds to
            # the gate it was going to reach anyway.
            accepted = run()
            self.assertNotIn("empty string", accepted.stderr)
            self.assertIn("trace APK md5=", accepted.stderr)

    def test_analyzer_requires_exactly_the_metadata_the_collector_writes(self) -> None:
        """The "complete current format" gate must describe the current collector.

        ab_stats.py restates the collector's header and column contract as
        _CURRENT_META_KEYS / _CURRENT_COLUMNS, and every CSV fixture in this file is
        hand-written, so nothing tied the two together: add a stamp to
        coldstart_bench.sh and the gate keeps accepting files without it; rename a
        column and the gate refuses legitimate current output. Derive the contract
        from the producer instead of trusting two copies to stay equal.

        Equality on the metadata side, deliberately. A collector stamp that no gate
        validates is the same defect as a gate requiring a stamp nobody writes: both
        mean the declared "current format" is not the one being produced.
        """
        bench = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        ab = load_ab_stats_module()

        # EVERY site that writes a `#` line to the CSV, found generically. Naming the
        # `# device=` line and the two permission stamps individually made the test
        # blind to exactly what it exists to catch: a NEW standalone stamp site would
        # not enter `written`, so the analyzer could keep validating an older
        # contract while this test passed.
        writes = re.findall(r'(?:echo|printf)\s+(["\'])(.+?)\1[^\n]*>>\s*"\$OUT"', bench)
        written = set()
        metadata_sites = 0
        for _quote, payload in writes:
            if not payload.lstrip("'\"%s\\n ").startswith("#"):
                continue
            keys = re.findall(r'([a-z_][a-z0-9_]*)=', payload)
            if keys:
                metadata_sites += 1
                written.update(keys)
        # The header line and the two permission stamps at minimum; more if the
        # collector grew one, which is the case this test is for.
        self.assertGreaterEqual(metadata_sites, 3, f"found only {metadata_sites} sites")
        for stamp in ("device", "permission_a", "permission_b"):
            self.assertIn(stamp, written, stamp)

        self.assertEqual(
            written, set(ab._CURRENT_META_KEYS),
            "coldstart_bench.sh and ab_stats._CURRENT_META_KEYS disagree about the "
            "current header contract; the difference is "
            f"{written ^ set(ab._CURRENT_META_KEYS)}",
        )

        # Columns are a SUBSET check: the analyzer requires the launch-validity and
        # pairing columns, while the metric columns (displayed, ttfd, app_trace_ms)
        # and the liveness columns are legitimately selected per run.
        column_line = re.search(r'echo "(label,[^"]*)" >> "\$OUT"', bench)
        self.assertIsNotNone(column_line, "coldstart_bench.sh writes a CSV column header")
        emitted = set(column_line.group(1).split(","))
        missing = ab._CURRENT_COLUMNS - emitted
        self.assertFalse(
            missing,
            f"ab_stats requires columns the collector does not write: {sorted(missing)}",
        )
        # And the metric this harness reports by default is one of them, so a rename
        # of the primary column cannot pass this test quietly.
        self.assertIn("total_ms", emitted)

    def test_completion_marker_has_one_definition_across_the_toolchain(self) -> None:
        """Writer, shell reader and Python reader must spell it identically.

        A reader more permissive than its writer accepts a file the rest of the
        toolchain refuses: `.strip()` accepted a marker carrying a trailing space or
        a CR that `grep -x -F` counts as a different line, so the same CSV analyzed
        as reportable and could not be bound to a trace.
        """
        lib = LIB.read_text(encoding="utf-8")
        bench = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        stats = AB_STATS.read_text(encoding="utf-8")

        shell_literal = re.search(r'DD_RUN_COMPLETE_MARKER="([^"]*)"', lib)
        python_literal = re.search(r'_RUN_COMPLETE = "([^"]*)"', stats)
        self.assertIsNotNone(shell_literal, "lib.sh defines the marker")
        self.assertIsNotNone(python_literal, "ab_stats.py restates the marker")
        self.assertEqual(shell_literal.group(1), python_literal.group(1))

        # Both readers compare the WHOLE line, with no whitespace tolerance.
        self.assertIn('grep -c -x -F -- "$DD_RUN_COMPLETE_MARKER"', lib)
        self.assertIn('.rstrip("\\n") == _RUN_COMPLETE', stats)
        # The writer uses the shared definition rather than its own copy.
        self.assertIn('printf \'%s\\n\' "$DD_RUN_COMPLETE_MARKER" >> "$OUT"', bench)
        self.assertNotIn('echo "# RUN COMPLETE"', bench)

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

    def test_known_off_radios_cannot_be_overridden_as_enabled(self) -> None:
        result = self.run_with_fake_adb(
            """
            set -euo pipefail
            sleep() { :; }
            ALLOW_UNVERIFIED_RADIOS=1
            . "$LIB"
            dd_apply_radio_state 0
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell svc wifi enable") exit 0 ;;
              "shell settings get global wifi_on") printf '0\n' ;;
              "shell settings get global mobile_data") printf '0\n' ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("both controlled radios", result.stderr)
        self.assertIn("cannot override a readable contradictory", result.stderr)
        self.assertNotIn("WITHOUT proof a radio was enabled", result.stderr)

        indeterminate = self.run_with_fake_adb(
            """
            set -euo pipefail
            sleep() { :; }
            ALLOW_UNVERIFIED_RADIOS=1
            . "$LIB"
            dd_apply_radio_state 0
            """,
            """
            #!/usr/bin/env bash
            case "$*" in
              "shell svc wifi enable") exit 0 ;;
              "shell settings get global wifi_on") exit 0 ;;
              "shell settings get global mobile_data") printf '0\n' ;;
              *) exit 1 ;;
            esac
            """,
        )
        self.assertEqual(indeterminate.returncode, 0, indeterminate.stderr)
        self.assertIn("WITHOUT proof a radio was enabled", indeterminate.stderr)

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

    def test_run_has_positive_completion_and_no_partial_recovery(self) -> None:
        source = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        completion = source.index(
            'printf \'%s\\n\' "$DD_RUN_COMPLETE_MARKER" >> "$OUT"')
        measured = source.rindex('measure "$arm" "$b" "measure" "$RUNS" "$pos"')
        self.assertLess(measured, completion)
        self.assertIn('echo "# RUN ABORTED (exit $rc)', source)
        self.assertIn("Partial-run recovery is not a reportable protocol", source)
        self.assertNotIn("completed_blocks", source)
        self.assertNotIn("recover-completed-blocks", source)

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

    def test_only_silent_pm_path_exit_one_means_absent(self) -> None:
        for remote_result, detail in (
            ("printf '__dd_rc=2\\n'", "exit 2"),
            ("printf 'permission denied\\n__dd_rc=1\\n'", "ambiguous absence"),
        ):
            with self.subTest(remote_result=remote_result):
                result = self.run_with_fake_adb(
                    """
                    . "$LIB"
                    DD_ANDROID_USER=0
                    dd_package_path com.example.app
                    """,
                    f"""
                    #!/usr/bin/env bash
                    {remote_result}
                    """,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(detail, result.stderr)

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

    def test_trace_settle_reproduces_probe_and_warmup_cadences(self) -> None:
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        loop = capture.index("for ((_i=1; _i<=SETTLE_LAUNCHES; _i++)); do")
        loop_end = capture.index("\ndone", loop)
        body = capture[loop:loop_end]

        for assignment in (
            '_SETTLE_KIND="liveness probe"',
            "_SETTLE_PRE_SLEEP=3",
            "_SETTLE_CHECK_SLEEP=8",
            "_SETTLE_FINAL_SLEEP=0",
            '_SETTLE_KIND="warm-up"',
            "_SETTLE_PRE_SLEEP=5",
            "_SETTLE_CHECK_SLEEP=6",
            "_SETTLE_FINAL_SLEEP=4",
        ):
            self.assertIn(assignment, body)

        force_stop = body.index("shell am force-stop")
        pre_sleep = body.index('sleep "$_SETTLE_PRE_SLEEP"', force_stop)
        launch = body.index('shell am start -W "${START_ARGS[@]}"', pre_sleep)
        probe_split = body.index("_SETTLE_BEFORE_PERFETTO=", launch)
        probe_perfetto = body.index("start_perfetto", probe_split)
        ready_sleep = body.index('sleep "$_PERFETTO_READY_WAIT"', probe_perfetto)
        check_sleep = body.index('sleep "$_SETTLE_CHECK_SLEEP"', ready_sleep)
        liveness = body.index('dd_datadog_threads "$_SETTLE_PIDS"', check_sleep)
        warmup_perfetto = body.index("start_perfetto", liveness)
        final_sleep = body.index('sleep "$_SETTLE_FINAL_SLEEP"', warmup_perfetto)
        self.assertLess(force_stop, pre_sleep)
        self.assertLess(pre_sleep, launch)
        self.assertLess(launch, probe_perfetto)
        self.assertLess(probe_perfetto, ready_sleep)
        self.assertLess(ready_sleep, check_sleep)
        self.assertLess(check_sleep, liveness)
        self.assertLess(liveness, warmup_perfetto)
        self.assertLess(warmup_perfetto, final_sleep)

        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        probe = benchmark[benchmark.index("probe_datadog() {"):
                          benchmark.index("\n}", benchmark.index("probe_datadog() {"))]
        measure = benchmark[benchmark.index("measure() {"):
                            benchmark.index("\n}", benchmark.index("measure() {"))]
        self.assertIn("sleep 3", probe)
        self.assertIn("sleep 8", probe)
        self.assertIn("sleep 5", measure)
        self.assertIn("sleep 6", measure)
        self.assertIn("sleep 4", measure)

    def test_trace_duration_pays_for_the_in_capture_pre_launch_cadence(self) -> None:
        """Moving the pre-launch cadence inside the capture spends `duration_ms`.

        Reproducing measure()'s force-stop and 5s wait after Perfetto starts put 9s
        of the budget before `am start -W` instead of 4s, silently cutting the
        post-launch window from ~16s to ~11s. The endpoint wait is bounded by
        Perfetto's lifetime, so that is exactly what makes a late `Fully drawn` or
        app-trace marker die with "was not reached before Perfetto stopped".
        """
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        duration_ms = int(re.search(r"duration_ms:\s*(\d+)", capture).group(1))
        ready_s = int(re.search(r"_PERFETTO_READY_WAIT=(\d+)", capture).group(1))
        settle = capture.index("for ((_i=1; _i<=SETTLE_LAUNCHES; _i++)); do")
        after_settle = capture.index("\ndone", settle) + len("\ndone")
        force_stop = capture.index("shell am force-stop", after_settle)
        launch = capture.index('LAUNCH_OUT=$("$ADB" shell am start -W', force_stop)
        measured_waits = [int(m.group(1)) for m in re.finditer(
            r"^\s*sleep (\d+)\s*$", capture[force_stop:launch], re.M
        )]
        self.assertEqual(measured_waits, [5])
        # The 4s readiness period is the last conditioning wait, then the measured
        # launch contributes its own 5s force-stop wait inside the capture.
        in_capture_s = ready_s + sum(measured_waits)
        self.assertEqual(in_capture_s, 9)
        post_launch_ms = duration_ms - in_capture_s * 1000
        self.assertGreaterEqual(
            post_launch_ms, 15000,
            f"{in_capture_s}s of the {duration_ms}ms capture elapses before the traced "
            f"launch, leaving only {post_launch_ms}ms for the launch and its endpoint. "
            "Raise duration_ms to pay for any added pre-launch wait.",
        )

    def test_traced_launch_reproduces_measured_final_pre_start_cadence(self) -> None:
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        perfetto_function = capture.index("start_perfetto() {")
        settle = capture.index("for ((_i=1; _i<=SETTLE_LAUNCHES; _i++)); do")
        after_settle = capture.index("\ndone", settle) + len("\ndone")
        probe_perfetto = capture.index("start_perfetto", settle)
        ready_wait = capture.index('sleep "$_PERFETTO_READY_WAIT"', probe_perfetto)
        liveness = capture.index('dd_datadog_threads "$_SETTLE_PIDS"', ready_wait)
        warmup_perfetto = capture.index("start_perfetto", liveness)
        force_stop = capture.index("shell am force-stop", after_settle)
        measured_wait = capture.index("sleep 5", force_stop)
        clear = capture.index("shell logcat -c", measured_wait)
        watcher = capture.index('_ENDPOINT_FILE=$(mktemp', clear)
        launch = capture.index('LAUNCH_OUT=$("$ADB" shell am start -W', watcher)

        self.assertLess(perfetto_function, settle)
        self.assertLess(probe_perfetto, ready_wait)
        self.assertLess(ready_wait, liveness)
        self.assertLess(liveness, warmup_perfetto)
        self.assertLess(warmup_perfetto, after_settle)
        self.assertNotIn('sleep "$_PERFETTO_READY_WAIT"', capture[after_settle:force_stop])
        self.assertLess(ready_wait, force_stop)
        self.assertLess(force_stop, measured_wait)
        self.assertLess(measured_wait, clear)
        self.assertLess(clear, watcher)
        self.assertLess(watcher, launch)

        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        measure_start = benchmark.index("measure() {")
        measure_end = benchmark.index("\n}", measure_start)
        measure = benchmark[measure_start:measure_end]
        bench_stop = measure.index("shell am force-stop")
        bench_wait = measure.index("sleep 5", bench_stop)
        bench_clear = measure.index("shell logcat -c", bench_wait)
        bench_launch = measure.index('shell am start "${START_ARGS[@]}"', bench_clear)
        self.assertLess(bench_stop, bench_wait)
        self.assertLess(bench_wait, bench_clear)
        self.assertLess(bench_clear, bench_launch)

    def test_trace_liveness_uses_only_the_post_force_stop_generation(self) -> None:
        source = (HARNESS / "verify_trace.py").read_text(encoding="utf-8")
        tree = ast.parse(source)
        helper = next(
            node for node in tree.body
            if isinstance(node, ast.FunctionDef) and node.name == "final_launch_processes"
        )
        namespace: dict[str, object] = {}
        exec(compile(ast.Module(body=[helper], type_ignores=[]), "verify_trace.py", "exec"),
             namespace)
        select = namespace["final_launch_processes"]

        class FakeTraceProcessor:
            """Answers the scheduler-boundary query and counts whether it was needed."""

            def __init__(self, boundary):
                self.boundary = boundary
                self.queries = []

            def query(self, sql):
                self.queries.append(sql)
                return [types.SimpleNamespace(b=self.boundary)]

        old = types.SimpleNamespace(upid=1, start_ts=None, end_ts=100)
        # This private process starts after the old main's hypothetical final sched
        # slice, but while that process is still alive. It extends the force-stop
        # boundary, and the next private process proves the closure is transitive.
        late_conditioning = types.SimpleNamespace(upid=2, start_ts=90, end_ts=110)
        chained_conditioning = types.SimpleNamespace(upid=3, start_ts=105, end_ts=115)
        final_main = types.SimpleNamespace(upid=4, start_ts=200, end_ts=None)
        final_private = types.SimpleNamespace(upid=5, start_ts=210, end_ts=None)

        exact = FakeTraceProcessor(999)
        scoped, boundary, basis = select(
            exact, [old, late_conditioning, chained_conditioning, final_main, final_private])
        self.assertEqual(boundary, 115)
        # Both private conditioning processes are excluded; only the launch after
        # the complete force-stop boundary contributes to liveness.
        self.assertEqual([p.upid for p in scoped], [4, 5])
        self.assertEqual(basis, "lifetime")
        # Complete lifetimes need no scheduler query at all.
        self.assertEqual(exact.queries, [])

        # An incomplete lifetime closure DEGRADES rather than discarding the capture.
        # `end_ts` needs one event per process out of a RING_BUFFER carrying 25s of
        # sched_switch, and every trace predating sched/sched_process_free has it on
        # no process at all -- refusing outright made those captures worthless.
        incomplete_private = types.SimpleNamespace(upid=6, start_ts=95, end_ts=None)
        degraded = FakeTraceProcessor(120)
        fallback, boundary, basis = select(
            degraded, [old, incomplete_private, final_main])
        self.assertEqual([p.upid for p in fallback], [4])
        self.assertEqual(boundary, 120)
        self.assertEqual(basis, "sched")
        self.assertIn("from sched s", degraded.queries[0])

        # Fail closed only when NEITHER method can locate the boundary.
        unbounded, boundary, basis = select(
            FakeTraceProcessor(None), [old, incomplete_private, final_main])
        self.assertEqual(unbounded, [])
        self.assertIsNone(boundary)
        self.assertIsNone(basis)

        # Boundary known, but the traced launch is not in the capture. Also unusable,
        # and for a different reason the operator has to be able to tell apart.
        missing, boundary, basis = select(
            FakeTraceProcessor(999), [old, late_conditioning, chained_conditioning])
        self.assertEqual(missing, [])
        self.assertEqual(boundary, 115)

        # A capture taken before Perfetto moved inside the conditioning wait has no
        # old generation, and needs no boundary at all.
        clean = FakeTraceProcessor(1)
        no_old_generation, boundary, basis = select(clean, [final_main, final_private])
        self.assertIsNone(no_old_generation)
        self.assertIsNone(boundary)
        self.assertIsNone(basis)
        self.assertEqual(clean.queries, [])

        # A scheduler boundary is checked by the SEPARATION the protocol guarantees,
        # not refused for being a scheduler boundary. Refusing on the method's name
        # made the treatment arm permanently unverifiable: `end_ts` is NULL on every
        # process of a real capture from the target device even with
        # sched/sched_process_free enabled (measured: 849/849 processes,
        # 4429/4429 threads), so the basis is "sched" on every real trace and any
        # gate keyed on it alone fires on every capture that has SDK threads.
        self.assertIn("MIN_SEPARATION_NS = 1_000_000_000", source)
        self.assertIn('if boundary_basis == "sched" and separation_ns is not None', source)
        self.assertIn("separation_ns < MIN_SEPARATION_NS", source)
        self.assertIn("THE TWO PROCESS GENERATIONS ARE NOT SEPARATED", source)
        self.assertNotIn('boundary_basis == "sched" and active', source)
        # The margin is reported whenever a boundary was used, so the number behind
        # the verdict is visible rather than implied.
        self.assertIn("s after that boundary", source)

        # The unusable verdict must name which of the two failures it saw.
        self.assertIn("nor any scheduler activity", source)
        self.assertIn('ftrace_events: "sched/sched_process_free"',
                      (HARNESS / "capture_trace.sh").read_text(encoding="utf-8"))
        self.assertIn("but no package process started after that", source)
        self.assertIn("select upid, pid, name, start_ts, end_ts from process", source)

        self.assertIn(
            'upids = ",".join(str(r.upid) for r in verdict_procs)',
            source,
        )
        self.assertIn("where upid in ({upids}) and lower(name) glob 'datadog-*'", source)
        self.assertIn("tp, {r.upid for r in verdict_procs}, args.package", source)
        self.assertIn("final_launch_processes(tp, procs)", source)

    def test_thread_liveness_fails_closed_on_unreadable_processes(self) -> None:
        success = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_datadog_threads "123 456"
            """,
            """
            #!/usr/bin/env bash
            case "$2" in
              "cat /proc/123/task/*/comm 2>/dev/null") printf 'main\nRenderThread\n' ;;
              "cat /proc/456/task/*/comm 2>/dev/null") printf 'datadog-storage\ndatadog-worker\n' ;;
              *) exit 90 ;;
            esac
            """,
        )
        self.assertEqual(success.returncode, 0, success.stderr)
        self.assertEqual(success.stdout.strip(), "2")

        partial = self.run_with_fake_adb(
            """
            set -euo pipefail
            . "$LIB"
            dd_datadog_threads "123 456"
            """,
            """
            #!/usr/bin/env bash
            case "$2" in
              "cat /proc/123/task/*/comm 2>/dev/null") printf 'main\n' ;;
              "cat /proc/456/task/*/comm 2>/dev/null") exit 17 ;;
              *) exit 90 ;;
            esac
            """,
        )
        self.assertNotEqual(partial.returncode, 0)
        # The fake adb answers nothing about /proc/456, so the read is classified
        # as a process that is gone rather than as transient thread churn.
        self.assertIn("PID 456's thread list could not be", partial.stderr)

        empty = self.run_with_fake_adb(
            '. "$LIB"; dd_datadog_threads "123"',
            """
            #!/usr/bin/env bash
            exit 0
            """,
        )
        self.assertNotEqual(empty.returncode, 0)
        self.assertIn("PID 123 returned no thread names", empty.stderr)

        benchmark = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        capture = (HARNESS / "capture_trace.sh").read_text(encoding="utf-8")
        self.assertIn('if ! dd_thr=$(dd_datadog_threads "$_pids"); then', benchmark)
        self.assertIn('if ! _SETTLE_DD=$(dd_datadog_threads "$_SETTLE_PIDS"); then', capture)
        self.assertIn("Unknown\n       liveness is never accepted", benchmark)

    def test_probe_refusal_still_aborts_through_the_pipeline(self) -> None:
        """A documented dependency that nothing enforced is not enforced.

        probe_datadog runs on the LEFT of `| tail -1`, so it is a subshell and its
        `die` exits only that subshell -- `tail` then exits 0. `set -o pipefail` is
        the only reason the failure reaches the assignment and `set -e`. Drop it and
        an unverifiable liveness probe becomes a count, which is the false negative
        the fail-closed oracle exists to prevent.
        """
        source = (HARNESS / "coldstart_bench.sh").read_text(encoding="utf-8")
        self.assertIn("set -euo pipefail", source)
        self.assertIn('dd_count=$(probe_datadog "$arm" "$b" "$pos" | tail -1)', source)

        # And prove the propagation itself, rather than trusting the reading of it.
        script = (
            'set -euo pipefail\n'
            'die() { echo "FATAL: liveness unverifiable" >&2; exit 1; }\n'
            'probe() { echo "log line"; die; echo "never reached"; }\n'
            'trap \'rc=$?; echo "TRAP rc=$rc"\' EXIT\n'
            'dd_count=$(probe | tail -1)\n'
            'echo "SURVIVED dd_count=$dd_count"\n'
        )
        aborted = subprocess.run(["bash", "-c", script], capture_output=True, text=True)
        self.assertEqual(aborted.returncode, 1)
        self.assertIn("FATAL: liveness unverifiable", aborted.stderr)
        self.assertIn("TRAP rc=1", aborted.stdout)
        self.assertNotIn("SURVIVED", aborted.stdout)

        # The same script without pipefail is what the comment warns about.
        survived = subprocess.run(
            ["bash", "-c", script.replace("set -euo pipefail", "set -eu")],
            capture_output=True, text=True,
        )
        self.assertIn("SURVIVED dd_count=log line", survived.stdout)

    def test_liveness_oracle_separates_thread_churn_from_a_dead_process(self) -> None:
        """`cat /proc/<pid>/task/*/comm` fails when ONE thread exits under the glob.

        That is ordinary churn on a live app and says nothing about the SDK, but it
        is the same failure a crashed process gives. Treating them alike aborts an
        hour-long run over a millisecond of churn; treating them alike the other way
        would accept a crash as evidence of absence. The process is re-checked and
        the read retried, so each case gets its own answer.
        """
        churn = self.run_with_fake_adb(
            'set -uo pipefail; . "$LIB"; dd_datadog_threads "123"',
            """
            #!/bin/sh
            case "$2" in
              "cat /proc/123/task/*/comm 2>/dev/null")
                  if [ -f "$FAKE_ADB_STATE" ]; then
                    printf 'main\ndatadog-storage\n'; exit 0
                  fi
                  : > "$FAKE_ADB_STATE"; printf 'main\n'; exit 1 ;;
              "[ -d /proc/123 ] && echo __dd_alive") echo __dd_alive ;;
              *) exit 90 ;;
            esac
            """,
        )
        self.assertEqual(churn.returncode, 0, churn.stderr)
        self.assertEqual(churn.stdout.strip(), "1")

        crashed = self.run_with_fake_adb(
            'set -uo pipefail; . "$LIB"; dd_datadog_threads "123"',
            """
            #!/bin/sh
            case "$2" in
              "cat /proc/123/task/*/comm 2>/dev/null") exit 1 ;;
              "[ -d /proc/123 ] && echo __dd_alive") exit 1 ;;
              *) exit 90 ;;
            esac
            """,
        )
        self.assertNotEqual(crashed.returncode, 0)
        self.assertIn("the process is gone", crashed.stderr)

        denied = self.run_with_fake_adb(
            'set -uo pipefail; . "$LIB"; dd_datadog_threads "123"',
            """
            #!/bin/sh
            case "$2" in
              "cat /proc/123/task/*/comm 2>/dev/null") exit 1 ;;
              "[ -d /proc/123 ] && echo __dd_alive") echo __dd_alive ;;
              *) exit 90 ;;
            esac
            """,
        )
        self.assertNotEqual(denied.returncode, 0)
        self.assertIn("could not be read twice", denied.stderr)

    def test_mapped_library_count_separates_zero_from_unreadable(self) -> None:
        """`grep -c` exits 1 on a count of zero, which is an answer, and >=2 on no read.

        Piping adb straight into it collapsed both into 0, so an unreadable
        /proc/<pid>/maps printed `libdatadog-ndk.so mapped : 0` -- indistinguishable
        from a build that genuinely does not map it.
        """
        counted = self.run_with_fake_adb(
            'set -uo pipefail; . "$LIB"; dd_mapped_lib_count libdatadog-ndk "111 222"',
            """
            #!/bin/sh
            case "$2" in
              *"grep -c libdatadog-ndk /proc/111/maps"*) printf '3\n__dd_rc=0\n' ;;
              *"grep -c libdatadog-ndk /proc/222/maps"*) printf '0\n__dd_rc=1\n' ;;
              *) exit 90 ;;
            esac
            """,
        )
        self.assertEqual(counted.returncode, 0, counted.stderr)
        # grep's exit 1 for PID 222 is the answer "zero", not a failed read.
        self.assertEqual(counted.stdout.strip(), "3")

        unreadable = self.run_with_fake_adb(
            'set -uo pipefail; . "$LIB"; dd_mapped_lib_count libdatadog-ndk "111"',
            """
            #!/bin/sh
            case "$2" in
              *"grep -c libdatadog-ndk /proc/111/maps"*) printf '__dd_rc=2\n' ;;
              *) exit 90 ;;
            esac
            """,
        )
        self.assertNotEqual(unreadable.returncode, 0)
        self.assertIn("was not read", unreadable.stderr)

        no_marker = self.run_with_fake_adb(
            'set -uo pipefail; . "$LIB"; dd_mapped_lib_count libdatadog-ndk "111"',
            """
            #!/bin/sh
            exit 1
            """,
        )
        self.assertNotEqual(no_marker.returncode, 0)
        self.assertIn("reported no exit status", no_marker.stderr)

    def test_informational_lines_say_unknown_rather_than_zero(self) -> None:
        """A line nobody takes a verdict from still may not overclaim.

        `libdatadog-ndk.so mapped : 0` and `Datadog logcat lines : 0` both used to
        appear when the read had failed, which reads as evidence of absence.
        """
        source = (HARNESS / "verify_sdk_active.sh").read_text(encoding="utf-8")
        self.assertNotIn("|| _n=0", source)
        self.assertNotIn("logcat -d 2>/dev/null | grep -ci datadog", source)
        self.assertIn('if NDKMAP=$(dd_mapped_lib_count libdatadog-ndk "$PIDS"); then', source)
        self.assertIn('libdatadog-ndk.so mapped : unknown', source)
        self.assertIn("Datadog logcat lines     : unknown", source)

    def test_every_liveness_reader_goes_through_the_shared_oracle(self) -> None:
        """One definition of a readable thread list, or the weakest one decides.

        The probe gates the arm and `verify_sdk_active.sh` is the standalone verdict
        the skill sends agents to; both read /proc themselves with the failure
        swallowed, so an unreadable process counted as zero `datadog-*` threads and
        confirmed SDK absence on evidence never obtained.
        """
        readers = {
            name: (HARNESS / name).read_text(encoding="utf-8")
            for name in ("coldstart_bench.sh", "capture_trace.sh", "verify_sdk_active.sh")
        }
        for name, source in readers.items():
            self.assertNotIn(
                'shell "cat /proc/', source,
                f"{name} reads thread names itself instead of via dd_thread_names",
            )
        self.assertIn('names=$(dd_thread_names "$pids")', readers["coldstart_bench.sh"])
        self.assertIn('ALL=$(dd_thread_names "$PIDS")', readers["verify_sdk_active.sh"])
        # The only remaining direct read, behind the retry/refusal logic.
        lib = LIB.read_text(encoding="utf-8")
        self.assertEqual(lib.count('shell "cat /proc/'), 1)

    def test_unverifiable_liveness_is_a_setup_failure_not_a_not_live_verdict(self) -> None:
        """verify_sdk_active.sh exit 1 means "the SDK is not initializing".

        An unreadable /proc must not borrow that code: it used to fall out of the
        `grep .` below under `set -e` and exit 1 with nothing printed at all.
        """
        source = (HARNESS / "verify_sdk_active.sh").read_text(encoding="utf-8")
        self.assertIn('ALL=$(dd_thread_names "$PIDS")', source)
        call = source.index('ALL=$(dd_thread_names "$PIDS")')
        self.assertIn("|| die", source[call:call + 200])
        self.assertIn("die() { echo \"FATAL: $*\" >&2; exit 2; }", source)


class AbStatsRegressionTests(unittest.TestCase):
    COMPATIBLE_META = (
        "device=pixel sdk=31 fp=build/fingerprint emulator=0 android_user=0 "
        "compile_filter=speed-profile compile_status=verify perf_mode=fixed animations=0 "
        "airplane=0 abi=arm64-v8a "
        "launcher=com.example/.MainActivity warmup=3 "
        "baseline_md5=aaa treatment_md5=bbb label_a=A_noDD label_b=B_withDD "
        "expect_a=0 expect_b=1 app_trace_id=none permission_a=p1 permission_b=p2"
    )

    @classmethod
    def benchmark_csv(
        cls,
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
        if metadata is None:
            metadata = f"{cls.COMPATIBLE_META} blocks={block_count} runs={runs_per_cell}"
        rows = [
            "label,block,pos_in_block,phase,run,total_ms,launch_state,status,foreground,ttfd"
        ]
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
        rows.append("# RUN COMPLETE")
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
            if line.startswith("#"):
                output.append(line)
                continue
            fields = line.split(",")
            fields.pop(column_index)
            output.append(",".join(fields))
        return "\n".join(output) + "\n"

    def test_selected_endpoint_missing_is_refused_by_default(self) -> None:
        result = self.run_stats(self.benchmark_csv(missing_treatment_block=2))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("refusing to report --metric ttfd", result.stderr)
        self.assertNotIn("PRIMARY ENDPOINT", result.stdout)

    def test_non_finite_or_negative_endpoint_is_refused_as_invalid_evidence(self) -> None:
        stats = load_ab_stats_module()
        for value in (
            "NaN", "nan", "Infinity", "-Infinity", "inf", "-inf", "1e309",
            "-1", "-0.5", "-1e-9",
        ):
            self.assertIsNone(stats.parse_ms(value), value)

        original = "A_noDD,1,1,measure,1,101,COLD,ok,ok,101"
        for value in ("NaN", "Infinity", "-Infinity", "-1", "-0.5"):
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

    def test_duplicate_run_id_cannot_satisfy_the_declared_matrix(self) -> None:
        csv_body = self.benchmark_csv(
            metadata=f"{self.COMPATIBLE_META} blocks=4 runs=2",
            runs_per_cell=2,
        ).replace(
            "A_noDD,1,1,measure,2,101,COLD,ok,ok,101",
            "A_noDD,1,1,measure,1,101,COLD,ok,ok,101",
        )

        result = self.run_stats(csv_body)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("INCOMPLETE EXPERIMENT MATRIX", result.stdout)
        self.assertIn("A_noDD block 1: invalid run IDs", result.stdout)
        self.assertIn("missing 2", result.stdout)
        self.assertIn("duplicate 1x2", result.stdout)
        self.assertNotIn("PRIMARY ENDPOINT", result.stdout)

    def test_partially_present_or_malformed_matrix_metadata_is_refused(self) -> None:
        for declaration in ("blocks=4", "runs=1"):
            with self.subTest(declaration=declaration):
                result = self.run_stats(self.benchmark_csv(
                    metadata=f"{self.COMPATIBLE_META} {declaration}"
                ))
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("complete current harness format", result.stderr)
                self.assertNotIn("PRIMARY ENDPOINT", result.stdout)
        for declaration in ("blocks=oops runs=1", "blocks=4 runs=oops", "blocks=4 runs=0"):
            with self.subTest(declaration=declaration):
                result = self.run_stats(self.benchmark_csv(
                    metadata=f"{self.COMPATIBLE_META} {declaration}"
                ))
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("malformed experiment matrix metadata", result.stderr)
                self.assertIn("both be positive integers", result.stderr)
                self.assertNotIn("PRIMARY ENDPOINT", result.stdout)

    def test_complete_selected_endpoint_still_reports_primary_interval(self) -> None:
        result = self.run_stats(self.benchmark_csv())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--- PRIMARY ENDPOINT: paired block-level delta ---", result.stdout)
        self.assertIn("  95% CI                ", result.stdout)

    def test_missing_launch_validity_columns_are_not_current_format(self) -> None:
        for column in ("status", "launch_state", "foreground"):
            with self.subTest(column=column):
                result = self.run_stats(
                    self.erase_column(self.benchmark_csv(), column)
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("complete current harness format", result.stderr)
                self.assertIn(f"missing columns {column}", result.stderr)
                self.assertNotIn("PRIMARY ENDPOINT", result.stdout)

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

    def aborted_csv(
        self,
        completed_blocks: int,
        declared_blocks: int = 8,
        invalid_treatment_block: int | None = None,
        drop_baseline_block: int | None = None,
    ) -> str:
        """A CSV shaped like the one an abort mid-block leaves behind.

        Whole blocks up to `completed_blocks`, then a partial block whose last row
        is the rejected launch that stopped the run, then the harness's trailer.
        """
        rows = self.benchmark_csv(
            metadata=self.recoverable_metadata(declared_blocks),
            runs_per_cell=2,
            block_count=completed_blocks,
            invalid_treatment_block=invalid_treatment_block,
        ).rstrip("\n").split("\n")
        rows = [row for row in rows if row != "# RUN COMPLETE"]
        if drop_baseline_block is not None:
            rows = [r for r in rows
                    if not r.startswith(f"A_noDD,{drop_baseline_block},")]
        partial = completed_blocks + 1
        rows.append(f"B_withDD,{partial},1,measure,1,118,COLD,ok,ok,118")
        rows.append(f"B_withDD,{partial},2,measure_rejected,2,NA,NA,ok,OTHER_MID,NA")
        rows.append("# RUN ABORTED (exit 1) -- another activity took the foreground: "
                    "com.android.settings/.homepage.SettingsHomepageActivity")
        return "\n".join(rows) + "\n"

    def recoverable_metadata(self, blocks: int, runs: int = 2) -> str:
        return (f"device=pixel sdk=31 abi=arm64-v8a emulator=0 android_user=0 "
                f"compile_filter=speed-profile compile_status=verify "
                f"perf_mode=fixed "
                f"blocks={blocks} runs={runs} warmup=3 "
                f"animations=0 fp=fp1 launcher=com.example/.Main airplane=0 "
                f"baseline_md5=aaa treatment_md5=bbb label_a=A_noDD label_b=B_withDD "
                f"expect_a=0 expect_b=1 app_trace_id=none permission_a=p1 permission_b=p2")

    def test_aborted_run_is_refused_by_naming_the_abort_not_a_missing_marker(self):
        """An aborted run has no completion marker BY DEFINITION.

        Reporting that absence first replaced "the harness aborted, and here is the
        exit status it recorded" with "a marker is missing", on the default path,
        where the operator has no reason to pass a flag yet. The recorded trailer is
        the only thing that says what to fix before re-running.
        """
        # Two whole blocks: below the paired minimum, so the run is refused and the
        # question is only WHICH refusal speaks. Six whole blocks of eight declared
        # is reportable now, deliberately, and is covered separately.
        result = self.run_stats(self.aborted_csv(2))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("refusing to analyze an aborted run", result.stderr)
        self.assertNotIn("did not positively complete", result.stderr)
        # The abort banner and the CSV's own trailer, without needing a flag.
        self.assertIn("This CSV is a PARTIAL run", result.stdout)
        self.assertIn("RUN ABORTED (exit 1)", result.stdout)
        self.assertIn("another activity took the foreground", result.stdout)

        # A run killed outright leaves neither marker, and there the missing
        # completion evidence IS the most specific thing that can be said.
        killed = self.benchmark_csv().replace("# RUN COMPLETE\n", "")
        killed_result = self.run_stats(killed)
        self.assertNotEqual(killed_result.returncode, 0)
        self.assertIn("did not positively complete", killed_result.stderr)
        self.assertNotIn("refusing to analyze an aborted run", killed_result.stderr)

    def test_interrupted_run_with_its_registered_design_is_reportable(self) -> None:
        """A run that collected whole blocks before dying is not a chosen prefix.

        The block collection stopped inside is incomplete and drops out by
        construction, and nothing about a later abort biases the blocks before it.
        The paired estimator is unbiased at any even k, so the shortfall costs
        POWER, which the MDE at k already reports.
        """
        result = self.run_stats(self.aborted_csv(6))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("INTERRUPTED RUN, ANALYZED OVER 6 WHOLE BLOCKS of the 8", result.stdout)
        self.assertIn("Blocks analyzed: 1, 2, 3, 4, 5, 6", result.stdout)
        # The estimate itself is computed on six blocks, and is reportable.
        self.assertRegex(result.stdout, r"blocks\s+6\n")
        self.assertIn("MDE at 6 blocks", result.stdout)
        self.assertNotIn("NOT REPORTABLE", result.stdout)
        self.assertNotIn("DIAGNOSTIC ONLY", result.stdout)
        # Why it stopped is what decides whether the survivors are usable, so the
        # recorded trailer travels with the result rather than being replaced by it.
        self.assertIn("another activity took the foreground", result.stdout)
        self.assertIn("WHY THE RUN STOPPED DECIDES WHETHER THIS IS USABLE", result.stdout)
        # And the shortfall is in the block that gets copied into a report, not only
        # in a banner above it.
        self.assertIn("INTERRUPTED RUN: this is 6 whole blocks of the 8", result.stdout)
        self.assertIn("Re-run the full design", result.stdout)
        # The partial block contributes nothing at all.
        self.assertIn("row(s) belonging to blocks that did not complete", result.stdout)
        self.assertNotIn("block   7", result.stdout)

    def test_interrupted_run_floors_its_block_count_to_even(self) -> None:
        """An odd count cannot be counterbalanced; the last block goes, not a chosen one."""
        result = self.run_stats(self.aborted_csv(5))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("ANALYZED OVER 4 WHOLE BLOCKS of the 8", result.stdout)
        self.assertIn("Block 5 was whole but dropped", result.stdout)
        self.assertIn("does not depend on the result", result.stdout)
        self.assertRegex(result.stdout, r"blocks\s+4\n")

    def test_a_block_missing_one_launch_is_not_whole(self) -> None:
        """Completeness is per cell, so a hole excludes its whole block.

        Six blocks collected, one launch missing from block 6: five whole blocks,
        floored to four. Not five, and not six.
        """
        body = self.aborted_csv(6)
        holed = "".join(line for line in body.splitlines(True)
                        if not line.startswith("A_noDD,6,2,measure,2,"))
        self.assertNotEqual(body, holed, "the fixture prefix must exist")
        result = self.run_stats(holed)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("ANALYZED OVER 4 WHOLE BLOCKS of the 8", result.stdout)
        self.assertIn("Block 5 was whole but dropped", result.stdout)

    def test_a_block_holding_a_rejected_launch_is_not_whole(self) -> None:
        """A cell with a rejected launch is not a smaller cell; the block drops.

        The fixture is the shape the collector actually leaves: the rejection is in
        the LAST block it reached, because an invalid launch aborts the run. An
        earlier version put it in block 4 with whole blocks 5 and 6 after it, which
        no collector run can produce -- and the consecutive-prefix rule now refuses
        that file, correctly.
        """
        rows = self.benchmark_csv(
            metadata=self.recoverable_metadata(8), block_count=6, runs_per_cell=2
        ).replace("# RUN COMPLETE\n", "")
        # One launch of block 6's treatment cell rejected: the cell still holds two
        # rows, so this is the full-count-with-a-hole case rather than a short cell.
        target = "B_withDD,6,1,measure,2,"
        self.assertIn(target, rows)
        rows = rows.replace(target, "B_withDD,6,1,measure_rejected,2,")
        rows += "# RUN ABORTED (exit 1) -- launch rejected\n"

        result = self.run_stats(rows)
        self.assertEqual(result.returncode, 0, result.stderr)
        # Blocks 1-5 are whole, block 6 is not, and five floors to four.
        self.assertIn("ANALYZED OVER 4 WHOLE BLOCKS of the 8", result.stdout)
        self.assertIn("Block 5 was whole but dropped", result.stdout)
        self.assertNotIn("not a collected prefix", result.stderr)
        # The rejected launch is excluded with its block, so it never reaches the
        # invalid-launch refusal it would otherwise trigger.
        self.assertNotIn("were rejected or failed", result.stdout)

    def test_contradictory_completion_evidence_is_never_recovered(self) -> None:
        """Recovery needs ZERO completion markers, not "not exactly one".

        An interrupted run has none: the marker is the collector's last act. One
        marker beside an abort trailer, or two markers, are evidence no collector run
        can produce, and both reached recovery and emitted a 95% CI while the gate
        read `!= 1`. Contradictory evidence is a refusal, never an input to a
        reportable interval.
        """
        aborted = self.aborted_csv(6)

        def with_extra_markers(count):
            lines = aborted.splitlines(True)
            for _ in range(count):
                lines.insert(-1, "# RUN COMPLETE\n")
            return "".join(lines)

        # A completion marker beside the abort trailer.
        both = self.run_stats(with_extra_markers(1))
        self.assertNotEqual(both.returncode, 0)
        self.assertIn("refusing to analyze an aborted run", both.stderr)
        self.assertNotIn("ANALYZED OVER", both.stdout)
        self.assertNotIn("95% CI", both.stdout)

        # Two markers beside the abort trailer: the duplicates are the precise fact,
        # so they are named rather than deferred to the generic abort message.
        duplicated = self.run_stats(with_extra_markers(2))
        self.assertNotEqual(duplicated.returncode, 0)
        self.assertIn("2 RUN COMPLETE markers (expected 1)", duplicated.stdout)
        self.assertNotIn("ANALYZED OVER", duplicated.stdout)

        # Two markers with no trailer at all, over an otherwise recoverable shortfall.
        killed = self.benchmark_csv(
            metadata=self.recoverable_metadata(8), block_count=6, runs_per_cell=2
        ) + "# RUN COMPLETE\n"
        twice = self.run_stats(killed)
        self.assertNotEqual(twice.returncode, 0)
        self.assertIn("2 RUN COMPLETE markers (expected 1)", twice.stdout)
        self.assertNotIn("ANALYZED OVER", twice.stdout)
        self.assertNotIn("95% CI", twice.stdout)

    def test_recovery_requires_a_consecutive_prefix_of_whole_blocks(self) -> None:
        """A whole block after an incomplete one cannot exist in a collected file.

        The collector runs blocks in order and aborts on an invalid launch. Taking
        every INDIVIDUALLY whole block analyzed 1,2,4,5 as a counterbalanced design:
        balanced, reportable, and not an experiment anything ran.
        """
        gapped = self.benchmark_csv(
            metadata=self.recoverable_metadata(8), block_count=5, runs_per_cell=2
        ).replace("# RUN COMPLETE\n", "")
        gapped = "".join(line for line in gapped.splitlines(True)
                         if not line.startswith(("A_noDD,3,", "B_withDD,3,")))
        result = self.run_stats(gapped)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not a collected prefix", result.stderr)
        self.assertIn("whole blocks after the first incomplete one: 4, 5", result.stderr)
        self.assertIn("consecutive prefix from block 1: 1, 2", result.stderr)
        self.assertIn("cannot produce a whole block after an incomplete one", result.stderr)
        self.assertNotIn("ANALYZED OVER", result.stdout)
        self.assertNotIn("95% CI", result.stdout)

        # The abort trailer still reaches the operator: it is what says whether any
        # of the file is trustworthy, and this refusal is about the file's shape.
        gapped_aborted = gapped + "# RUN ABORTED (exit 1) -- dialog\n"
        with_trailer = self.run_stats(gapped_aborted)
        self.assertNotEqual(with_trailer.returncode, 0)
        self.assertIn("RUN ABORTED (exit 1)", with_trailer.stdout)
        self.assertIn("not a collected prefix", with_trailer.stderr)

        # A genuine prefix is unaffected.
        clean = self.run_stats(self.aborted_csv(6))
        self.assertEqual(clean.returncode, 0, clean.stderr)
        self.assertNotIn("not a collected prefix", clean.stderr)

    def test_recovery_refuses_an_odd_declared_block_count(self) -> None:
        """Recovery cannot legitimize a design the collector refuses to register."""
        interrupted = self.run_stats(self.aborted_csv(4, declared_blocks=5))
        self.assertNotEqual(interrupted.returncode, 0)
        self.assertIn("refusing to recover an interrupted run", interrupted.stderr)
        self.assertIn("blocks=5", interrupted.stderr)
        self.assertIn("must be even", interrupted.stderr)
        self.assertNotIn("ANALYZED OVER", interrupted.stdout)
        self.assertNotIn("95% CI", interrupted.stdout)

    def test_interrupted_run_below_the_paired_minimum_is_still_refused(self) -> None:
        """Three whole blocks floor to two, and a paired interval needs three."""
        refused = self.run_stats(self.aborted_csv(3))
        self.assertNotEqual(refused.returncode, 0)
        self.assertIn("refusing to analyze an aborted run", refused.stderr)
        self.assertNotIn("ANALYZED OVER", refused.stdout)

        diagnostic = self.run_stats(self.aborted_csv(3), "--allow-aborted")
        self.assertEqual(diagnostic.returncode, 0, diagnostic.stderr)
        self.assertIn("NOT REPORTABLE", diagnostic.stdout)

    def test_a_completed_run_declaring_a_shortfall_is_still_refused(self) -> None:
        """RUN COMPLETE and a truncated matrix contradict each other.

        The collector cannot emit both, so this file was edited or the collector is
        broken. Recovery applies to a run that is INTERRUPTED; it must not become a
        way to analyze a complete run that is missing rows.
        """
        complete = self.benchmark_csv(block_count=4, runs_per_cell=2)
        short = "".join(line for line in complete.splitlines(True)
                        if not line.startswith("B_withDD,3,"))
        result = self.run_stats(short)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("refusing to analyze a truncated run", result.stderr)
        self.assertNotIn("ANALYZED OVER", result.stdout)

    def test_recovery_never_pools_an_interrupted_run_with_another_file(self) -> None:
        """Combining a prefix with a top-up run is the protocol that was removed."""
        meta = self.recoverable_metadata(8)
        complete = self.benchmark_csv(metadata=meta, block_count=8, runs_per_cell=2)
        result = self.run_stats_files([self.aborted_csv(6), complete])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("refusing to analyze an aborted run", result.stderr)
        self.assertNotIn("ANALYZED OVER", result.stdout)

    def test_a_run_killed_without_a_trailer_says_it_has_no_reason(self) -> None:
        """No trailer means nothing says whether the analyzed blocks are trustworthy."""
        killed = self.benchmark_csv(
            metadata=self.recoverable_metadata(8), block_count=6, runs_per_cell=2
        ).replace("# RUN COMPLETE\n", "")
        result = self.run_stats(killed)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("ANALYZED OVER 6 WHOLE BLOCKS of the 8", result.stdout)
        self.assertIn("No abort trailer", result.stdout)
        self.assertIn("nothing here says whether the analyzed blocks are trustworthy",
                      result.stdout)

    def test_a_completed_run_is_unaffected_by_the_recovery_path(self) -> None:
        """This capability must be invisible when the run finished."""
        result = self.run_stats(self.benchmark_csv(block_count=4, runs_per_cell=2))
        self.assertEqual(result.returncode, 0, result.stderr)
        for needle in ("INTERRUPTED RUN", "ANALYZED OVER", "whole but dropped",
                       "blocks that did not complete"):
            self.assertNotIn(needle, result.stdout, needle)

    def test_block_completeness_shares_one_definition_with_the_aggregator(self) -> None:
        """The completeness pass and the row loop must agree on an admissible launch."""
        ab = load_ab_stats_module()
        rows = [
            {"label": "A", "block": "1", "phase": "measure", "run": "1",
             "status": "ok", "launch_state": "COLD", "foreground": "ok"},
            {"label": "B", "block": "1", "phase": "measure", "run": "1",
             "status": "ok", "launch_state": "COLD", "foreground": "ok"},
            # Block 2: the treatment cell holds a rejected launch.
            {"label": "A", "block": "2", "phase": "measure", "run": "1",
             "status": "ok", "launch_state": "COLD", "foreground": "ok"},
            {"label": "B", "block": "2", "phase": "measure_rejected", "run": "1",
             "status": "ok", "launch_state": "COLD", "foreground": "OTHER_MID"},
            # Block 3: baseline only.
            {"label": "A", "block": "3", "phase": "measure", "run": "1",
             "status": "ok", "launch_state": "COLD", "foreground": "ok"},
        ]
        self.assertEqual(ab.complete_block_ids(rows, "A", "B", 1), {"1"})
        self.assertTrue(ab.launch_rejected(rows[3]))
        self.assertFalse(ab.launch_rejected(rows[0]))
        # The aggregation loop must use the same helper rather than its own copy.
        source = AB_STATS.read_text(encoding="utf-8")
        self.assertIn("if launch_rejected(r):", source)
        self.assertEqual(source.count("def launch_rejected("), 1)

    def test_incomplete_run_is_diagnostic_only_when_explicitly_allowed(self) -> None:
        interrupted = self.benchmark_csv().replace("# RUN COMPLETE\n", "")
        refused = self.run_stats(interrupted)
        self.assertNotEqual(refused.returncode, 0)
        self.assertIn("did not positively complete", refused.stderr)

        diagnostic = self.run_stats(interrupted, "--allow-aborted")
        self.assertEqual(diagnostic.returncode, 0, diagnostic.stderr)
        self.assertIn("DIAGNOSTIC ONLY", diagnostic.stdout)
        self.assertIn("NOT REPORTABLE", diagnostic.stdout)
        self.assertNotIn("  95% CI                ", diagnostic.stdout)

    def test_partial_run_recovery_option_does_not_exist(self) -> None:
        result = self.run_stats(self.aborted_csv(6), "--recover-completed-blocks")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unrecognized arguments: --recover-completed-blocks", result.stderr)

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
                self.assertIn("spread is zero to the precision these observations carry", result.stdout)
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

    def test_explicit_unknown_foreground_suppresses_primary_inference(self) -> None:
        lines = self.benchmark_csv().splitlines()
        for index, line in enumerate(lines):
            if line.startswith("B_withDD,"):
                fields = line.split(",")
                fields[8] = "NA"
                lines[index] = ",".join(fields)
                break
        result = self.run_stats("\n".join(lines) + "\n")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("foreground=NA", result.stdout)
        self.assertIn("NOT REPORTABLE", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)
        self.assertNotIn("  MDE at", result.stdout)

    def test_unknown_foreground_in_conditioning_suppresses_primary_inference(self) -> None:
        csv_body = self.benchmark_csv()
        csv_body += "A_noDD,1,1,warmup,1,101,COLD,ok,NA,101\n"
        result = self.run_stats(csv_body)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("conditioning launch(es) carry", result.stdout)
        self.assertIn("foreground=NA", result.stdout)
        self.assertIn("NOT REPORTABLE", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)
        self.assertNotIn("  MDE at", result.stdout)

        unselected = self.benchmark_csv()
        unselected += "THIRD,1,1,probe,1,999,COLD,ok,NA,999\n"
        unrelated = self.run_stats(unselected)
        self.assertEqual(unrelated.returncode, 0, unrelated.stderr)
        self.assertNotIn("conditioning launch(es) carry", unrelated.stdout)
        self.assertIn("  95% CI                ", unrelated.stdout)

    def test_unknown_foreground_in_unselected_arm_does_not_suppress(self) -> None:
        csv_body = self.benchmark_csv()
        csv_body += "THIRD,1,2,measure,1,999,COLD,ok,NA,999\n"
        result = self.run_stats(csv_body)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("foreground=NA", result.stdout)
        self.assertIn("  95% CI                ", result.stdout)

    def test_measure_rejected_without_abort_trailer_is_not_reportable(self) -> None:
        csv_body = self.benchmark_csv(
            rejected_treatment_block=4,
            metadata=f"{self.COMPATIBLE_META} blocks=4 runs=1",
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
            self.benchmark_csv().replace("# RUN COMPLETE", "# RUN ABORTED"),
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
                    metadata=f"{self.COMPATIBLE_META} blocks=4 runs=1"
                ),
                self.benchmark_csv(
                    metadata=(f"{self.COMPATIBLE_META} blocks=4 runs=1"
                              .replace("expect_b=1", "expect_b=0"))
                ),
            ]
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("different SDK-liveness expectations", result.stderr)
        self.assertIn("expect_b: 0 vs 1", result.stderr)

    def test_pooling_different_permission_outcomes_is_refused(self) -> None:
        def stamped(permission_b: str) -> str:
            metadata = (f"{self.COMPATIBLE_META} blocks=4 runs=1"
                        .replace("permission_b=p2", f"permission_b={permission_b}"))
            return self.benchmark_csv(metadata=metadata)

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
                self.benchmark_csv(
                    metadata=f"{self.COMPATIBLE_META} blocks=4 runs=1"
                ),
                self.benchmark_csv(
                    metadata=(f"{self.COMPATIBLE_META} blocks=4 runs=1"
                              .replace("android_user=0", "android_user=10"))
                ),
            ]
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("android_user: 0 vs 10", result.stderr)

    def test_missing_current_metadata_is_refused_even_with_allow_mixed(self) -> None:
        metadata = (f"{self.COMPATIBLE_META} blocks=4 runs=1"
                    .replace(" compile_status=verify", ""))
        for args in ((), ("--allow-mixed",)):
            with self.subTest(args=args):
                result = self.run_stats(self.benchmark_csv(metadata=metadata), *args)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("complete current harness format", result.stderr)
                self.assertIn("missing metadata compile_status", result.stderr)

    def test_pooling_different_cpu_scheduling_scenarios_is_refused(self) -> None:
        """A pinned-CPU run and a dynamic one are two experiments, not more samples."""
        fixed = f"{self.COMPATIBLE_META} blocks=4 runs=1"
        dynamic = fixed.replace("perf_mode=fixed", "perf_mode=dynamic")
        result = self.run_stats_files(
            [self.benchmark_csv(metadata=fixed),
             self.benchmark_csv(metadata=dynamic, baseline_offset=140)]
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("different runtime controls", result.stderr)
        self.assertIn("perf_mode: dynamic vs fixed", result.stderr)

    def test_pooling_different_achieved_compile_states_is_refused(self) -> None:
        result = self.run_stats_files(
            [
                self.benchmark_csv(
                    metadata=f"{self.COMPATIBLE_META} blocks=4 runs=1"
                ),
                self.benchmark_csv(
                    metadata=(f"{self.COMPATIBLE_META} blocks=4 runs=1"
                              .replace("compile_status=verify",
                                       "compile_status=speed-profile"))
                ),
            ]
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("compile_status: speed-profile vs verify", result.stderr)

    def test_present_meta_differences_still_reports_known_disagreement(self) -> None:
        stats = load_ab_stats_module()
        identity_keys = (
            *stats._BUILD_KEYS,
            *stats._ARM_KEYS,
            *stats._LIVENESS_KEYS,
            *stats._PERMISSION_KEYS,
            *stats._CONTROL_KEYS,
            stats._APP_TRACE_KEY,
        )
        for key in identity_keys:
            with self.subTest(key=key):
                self.assertEqual(
                    stats.present_meta_differences(
                        [{}, {key: "known-a"}, {key: "known-b"}],
                        (key,),
                    ),
                    {key: ["known-a", "known-b"]},
                )

    def test_csv_without_current_metadata_is_refused(self) -> None:
        result = self.run_stats_files(
            [
                self.benchmark_csv(metadata="expect_a=0"),
                self.benchmark_csv(metadata="expect_a=1"),
            ]
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("complete current harness format", result.stderr)
        self.assertIn("missing metadata device", result.stderr)

    def test_missing_order_column_is_not_current_format(self) -> None:
        result = self.run_stats(self.erase_positions(self.benchmark_csv()))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("missing columns pos_in_block", result.stderr)
        self.assertNotIn("PRIMARY ENDPOINT", result.stdout)

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
            if lines[index].startswith("#"):
                continue
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

    def test_imbalanced_first_arm_counts_suppress_primary_inference(self) -> None:
        lines = self.benchmark_csv().splitlines()
        for index, line in enumerate(lines):
            fields = line.split(",")
            if fields[0] in ("A_noDD", "B_withDD") and fields[1] == "4":
                fields[2] = "1" if fields[0] == "A_noDD" else "2"
                lines[index] = ",".join(fields)

        result = self.run_stats("\n".join(lines) + "\n")

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("NOT REPORTABLE: contributing blocks are not counterbalanced", result.stdout)
        self.assertIn("'A_noDD': 3, 'B_withDD': 1", result.stdout)
        self.assertNotIn("  95% CI                ", result.stdout)
        self.assertNotIn("  MDE at", result.stdout)
        self.assertNotIn("  => Significant", result.stdout)

    def test_zero_order_variance_suppresses_the_order_effect_interval(self) -> None:
        """Rounded equal order deltas do not establish zero population variance."""
        lines = self.benchmark_csv().splitlines()
        for index, line in enumerate(lines):
            fields = line.split(",")
            if fields[0] not in ("A_noDD", "B_withDD"):
                continue
            block = int(fields[1])
            position = int(fields[2])
            value = 100 + 20 * block + (10 if position == 2 else 0)
            fields[5] = str(value)
            fields[9] = str(value)
            lines[index] = ",".join(fields)

        result = self.run_stats("\n".join(lines) + "\n")

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("2nd-minus-1st = +10.0 ms over 4 blocks  (descriptive)", result.stdout)
        self.assertIn("Every block carries the same 2nd-minus-1st delta", result.stdout)
        self.assertIn("no interval or", result.stdout)
        self.assertNotIn("2nd-minus-1st = +10.0 ms over 4 blocks  95% CI", result.stdout)
        self.assertNotIn("no significant order effect", result.stdout)
        # The interval is suppressed; the WARNING is not. A +10 ms shift repeated in
        # every block is stronger evidence of an order effect than a noisy one, and
        # 8a15f9b3d asserted its absence -- this assertion is reversed on purpose.
        self.assertIn("Ordering moves the measurement: every block shifts by +10.0 ms",
                      result.stdout)
        self.assertIn("what", result.stdout)
        self.assertIn("is missing is only the interval", result.stdout)
        self.assertIn("Re-run with more blocks", result.stdout)

    ROUNDED_CELLS = (1014, 246, 90, 118)

    def rounded_equal_delta_csv(self, constant_effect: int = 10) -> str:
        """Blocks whose deltas are mathematically equal but float-unequal.

        Three runs per cell, so each cell mean is an integer sum over 3. The bases are
        chosen to land on different float representations of the same delta, which is
        how `st.stdev(...) == 0` missed this case entirely.
        """
        header = "# " + self.recoverable_metadata(4, runs=3) + "\n"
        rows = ["label,block,pos_in_block,phase,run,total_ms,launch_state,status,"
                "foreground,ttfd"]
        for index, base in enumerate(self.ROUNDED_CELLS, start=1):
            baseline = [base, base, base + 1]
            treatment = [value + constant_effect for value in baseline]
            baseline_pos, treatment_pos = ("1", "2") if index % 2 else ("2", "1")
            for run, value in enumerate(baseline, 1):
                rows.append(f"A_noDD,{index},{baseline_pos},measure,{run},{value},"
                            f"COLD,ok,ok,{value}")
            for run, value in enumerate(treatment, 1):
                rows.append(f"B_withDD,{index},{treatment_pos},measure,{run},{value},"
                            f"COLD,ok,ok,{value}")
        return header + "\n".join(rows) + "\n# RUN COMPLETE\n"

    def test_rounded_equal_deltas_do_not_produce_a_primary_interval(self) -> None:
        """`stdev == 0` missed float-unequal deltas, and the PRIMARY result paid.

        A mathematically constant +10 ms effect over 3-run cells gives per-block
        deltas [9.999999999999886, ..., 10.000000000000014]. The exact test therefore
        did not fire and the tool printed `between-block sd 0.0 ms`, `95% CI
        [+10.0, +10.0]`, `MDE ~0 ms` and `=> Significant` -- fake precision in the one
        number this tool exists to produce, and labelled `sd 0.0` so it read exactly
        like the case the guard does catch.
        """
        result = self.run_stats(self.rounded_equal_delta_csv())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("NOT ESTIMABLE: all 4 contributing block deltas are +10.0 ms",
                      result.stdout)
        self.assertIn("spread is zero to the precision these observations carry",
                      result.stdout)
        self.assertNotIn("95% CI                [+10.0, +10.0] ms", result.stdout)
        self.assertNotIn("MDE at 4 blocks", result.stdout)
        self.assertNotIn("=> Significant", result.stdout)

    def test_the_degenerate_test_does_not_suppress_a_real_difference(self) -> None:
        """The tolerance must sit far below the smallest difference the data can hold.

        Deltas are differences of k-run means of integer milliseconds, so the smallest
        spread two genuinely different deltas can have is 1/k ms. At k=15 that is
        0.067 ms; float noise from the same arithmetic is ~1e-14 relative. The
        threshold has to separate the two by orders of magnitude in both directions.
        """
        ab = load_ab_stats_module()
        # Exactly equal, and equal-after-rounding: both degenerate.
        self.assertTrue(ab.degenerate_spread([10.0, 10.0, 10.0]))
        self.assertTrue(ab.degenerate_spread(
            [9.999999999999886, 9.999999999999972, 10.0, 10.000000000000014]))
        # The smallest real spread at 15 runs per cell, and at 2: not degenerate.
        self.assertFalse(ab.degenerate_spread([10.0, 10.0 + 1 / 15]))
        self.assertFalse(ab.degenerate_spread([10.0, 10.5]))
        # Scale-relative, so a large delta with a real 1/k difference still counts.
        self.assertFalse(ab.degenerate_spread([5000.0, 5000.0 + 1 / 15]))
        # All zeros is degenerate, and a single delta has no spread to speak of.
        self.assertTrue(ab.degenerate_spread([0.0, 0.0]))
        self.assertTrue(ab.degenerate_spread([7.0]))

    def test_a_clean_aa_order_diagnostic_is_not_asked_to_re_run(self) -> None:
        """m ~ 0 with zero spread is the ideal A/A, not a run needing more blocks."""
        lines = self.benchmark_csv(constant_delta=0).splitlines()
        for index, line in enumerate(lines):
            fields = line.split(",")
            if fields[0] not in ("A_noDD", "B_withDD"):
                continue
            fields[5] = fields[9] = "100"
            lines[index] = ",".join(fields)
        result = self.run_stats("\n".join(lines) + "\n")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("2nd-minus-1st = +0.0 ms over 4 blocks  (descriptive)", result.stdout)
        self.assertIn("counterbalancing", result.stdout)
        self.assertIn("does not need a longer run", result.stdout)
        self.assertNotIn("Ordering moves the measurement", result.stdout)
        # The order section must not demand a re-run for an ideal input. The primary
        # section legitimately does, because zero spread there IS non-estimable.
        order = result.stdout[result.stdout.index("order effect (position"):]
        self.assertNotIn("Re-run with more blocks", order)

    def test_unrelated_label_cannot_overwrite_selected_position_evidence(self) -> None:
        csv_body = self.benchmark_csv()
        csv_body += "THIRD,1,1,measure,1,999,COLD,ok,ok,999\n"

        result = self.run_stats(csv_body)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("  95% CI                ", result.stdout)
        self.assertNotIn("first-arm counts among contributing blocks", result.stdout)


if __name__ == "__main__":
    unittest.main()
