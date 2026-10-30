#!/usr/bin/env python3

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Run configured Android instrumentation tests on concurrent emulators."""

import argparse
import os
import re
import shlex
import signal
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from pathlib import Path
from typing import IO, List, Optional, Sequence, Tuple

from instrumentation_to_junit import convert_stream


FIRST_EMULATOR_PORT = 5554
BOOT_TIMEOUT_SECONDS = 360
ANDROID_ARCH = "arm64-v8a"
AVD_NAME_PREFIX = "android_emulator"
ANDROID_API_COMPONENTS = {
    23: ("23", "google_apis", "23.0.0"),
    28: ("28", "google_apis", "28.0.0"),
    37: ("37.0", "google_apis_ps16k", "37.0.0"),
}


@dataclass(frozen=True)
class InstrumentationRun:
    label: str
    test_apk_directory: str
    instrumentation_package: str
    app_apk_directory: Optional[str] = None
    arguments: Tuple[str, ...] = ()
    include_package: Optional[str] = None
    exclude_packages: Optional[str] = None


@dataclass(frozen=True)
class EmulatorConfiguration:
    label: str
    emulator_image: str
    android_platform: str
    android_build_tools: str
    runs: Tuple[InstrumentationRun, ...]


@dataclass(frozen=True)
class AndroidSdkTools:
    sdkmanager: Path
    avdmanager: Path
    emulator: Path
    adb: Path

    @classmethod
    def from_android_home(cls, android_home: Path) -> "AndroidSdkTools":
        command_line_tools = android_home / "cmdline-tools" / "latest" / "bin"
        return cls(
            sdkmanager=command_line_tools / "sdkmanager",
            avdmanager=command_line_tools / "avdmanager",
            emulator=android_home / "emulator" / "emulator",
            adb=android_home / "platform-tools" / "adb",
        )


@dataclass(frozen=True)
class EmulatorPlan:
    configuration: EmulatorConfiguration
    avd_name: str
    port: int
    serial: str


def create_argument_parser(description: str) -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate the configuration and print commands without executing them.",
    )
    return parser


def parse_arguments(description: str) -> argparse.Namespace:
    return create_argument_parser(description).parse_args()


def required_environment(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Required environment variable {name} is not set")
    return value


def find_apk(directory: str) -> Path:
    apks = sorted(Path(directory).rglob("*.apk"))
    if not apks:
        raise RuntimeError(f"No APK found in {directory}")
    return apks[0]


def emulator_for_api_level(
    label: str,
    api_level: int,
    runs: Sequence[InstrumentationRun],
) -> EmulatorConfiguration:
    try:
        api_version, system_image, build_tools_version = ANDROID_API_COMPONENTS[api_level]
    except KeyError as error:
        raise RuntimeError(f"Unsupported Android API level: {api_level}") from error
    return EmulatorConfiguration(
        label=label,
        emulator_image=f"system-images;android-{api_version};{system_image};{ANDROID_ARCH}",
        android_platform=f"platforms;android-{api_version}",
        android_build_tools=f"build-tools;{build_tools_version}",
        runs=tuple(runs),
    )


def required_android_components(
    configurations: Sequence[EmulatorConfiguration],
) -> List[str]:
    components = {"emulator", "platform-tools"}
    for configuration in configurations:
        components.update(
            {
                configuration.emulator_image,
                configuration.android_platform,
                configuration.android_build_tools,
            }
        )
    return sorted(components)


def create_emulator_plans(
    configurations: Sequence[EmulatorConfiguration],
) -> List[EmulatorPlan]:
    plans = []
    for index, configuration in enumerate(configurations):
        port = FIRST_EMULATOR_PORT + index * 2
        plans.append(
            EmulatorPlan(
                configuration=configuration,
                avd_name=f"{AVD_NAME_PREFIX}_{configuration.label.replace('-', '_')}",
                port=port,
                serial=f"emulator-{port}",
            )
        )
    return plans


def sdkmanager_install_command(
    tools: AndroidSdkTools,
    configurations: Sequence[EmulatorConfiguration],
) -> List[object]:
    return [tools.sdkmanager, "--install", *required_android_components(configurations)]


def avd_create_command(tools: AndroidSdkTools, plan: EmulatorPlan) -> List[object]:
    return [
        tools.avdmanager,
        "--verbose",
        "create",
        "avd",
        "--force",
        "--name",
        plan.avd_name,
        "--package",
        plan.configuration.emulator_image,
    ]


def emulator_launch_command(tools: AndroidSdkTools, plan: EmulatorPlan) -> List[object]:
    return [
        tools.emulator,
        "-avd",
        plan.avd_name,
        "-port",
        str(plan.port),
        "-grpc-use-jwt",
        "-no-snapstorage",
        "-no-audio",
        "-no-window",
        "-no-boot-anim",
        "-verbose",
        "-qemu",
        "-machine",
        "virt",
    ]


def install_android_components(
    tools: AndroidSdkTools,
    configurations: Sequence[EmulatorConfiguration],
    dry_run_only: bool,
) -> None:
    install_command = sdkmanager_install_command(tools, configurations)
    licenses_command = [tools.sdkmanager, "--licenses"]
    if dry_run_only:
        print("SDK components:")
        print_command(install_command)
        print_command(licenses_command)
        return
    subprocess.run(
        install_command,
        check=True,
        input="y\n" * 100,
        text=True,
    )
    subprocess.run(
        licenses_command,
        check=False,
        input="y\n" * 100,
        text=True,
    )


def create_avd(
    tools: AndroidSdkTools,
    plan: EmulatorPlan,
    dry_run_only: bool,
) -> None:
    command = avd_create_command(tools, plan)
    if dry_run_only:
        print(f"\nEmulator {plan.configuration.label}:")
        print_command(command)
        return
    subprocess.run(
        command,
        check=True,
        input="no\n",
        text=True,
    )


def launch_emulator(
    tools: AndroidSdkTools,
    plan: EmulatorPlan,
    dry_run_only: bool,
) -> Tuple[Optional[subprocess.Popen], Optional[IO[bytes]]]:
    command = emulator_launch_command(tools, plan)
    if dry_run_only:
        print_command(command)
        return None, None
    log = Path(f"emulator-{plan.configuration.label}.log").open("wb")
    process = subprocess.Popen(
        command,
        stdout=log,
        stderr=subprocess.STDOUT,
    )
    return process, log


def print_command(command: Sequence[object]) -> None:
    print(shlex.join(str(argument) for argument in command))


def instrumentation_output_paths(
    emulator_label: str,
    run: InstrumentationRun,
) -> Tuple[Path, Path]:
    output_suffix = f"-{emulator_label}-{run.label}"
    return (
        Path(f"instrumentation-output{output_suffix}.txt"),
        Path(f"instrumentation-results{output_suffix}.xml"),
    )


def upload_instrumented_junit(junit_reports: Sequence[Path]) -> int:
    exit_code = 0
    for junit_report in junit_reports:
        if not junit_report.is_file():
            continue
        api_match = re.search(r"api-([0-9]+)", junit_report.name)
        if api_match is None:
            print(
                f"Could not determine Android API level from {junit_report}",
                file=sys.stderr,
            )
            exit_code = 1
            continue
        result = subprocess.run(
            [
                "datadog-ci",
                "junit",
                "upload",
                "--service",
                "dd-sdk-android",
                "--tags",
                f"test.configuration.android_api:{api_match.group(1)}",
                "--xpath-tag",
                "test.suite=/testcase/@classname",
                junit_report,
            ],
            check=False,
        )
        if result.returncode != 0:
            exit_code = 1
    return exit_code


def apk_install_commands(
    run: InstrumentationRun,
    adb: Path,
    serial: str,
) -> List[List[object]]:
    commands: List[List[object]] = []
    if run.app_apk_directory:
        commands.append(
            [
                adb,
                "-s",
                serial,
                "install",
                "-r",
                "-t",
                find_apk(run.app_apk_directory),
            ]
        )
    commands.append(
        [
            adb,
            "-s",
            serial,
            "install",
            "-r",
            "-t",
            find_apk(run.test_apk_directory),
        ]
    )
    return commands


def instrumentation_command(
    run: InstrumentationRun,
    adb: Path,
    serial: str,
) -> List[object]:
    runner_arguments: List[str] = []
    if run.include_package:
        runner_arguments.extend(["-e", "package", run.include_package])
    if run.exclude_packages:
        runner_arguments.extend(["-e", "notPackage", run.exclude_packages])
    return [
        adb,
        "-s",
        serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        *run.arguments,
        *runner_arguments,
        f"{run.instrumentation_package}/androidx.test.runner.AndroidJUnitRunner",
    ]


def validate_plans(plans: Sequence[EmulatorPlan]) -> None:
    configuration_labels = set()
    for plan in plans:
        configuration = plan.configuration
        if configuration.label in configuration_labels:
            raise RuntimeError(f"Duplicate emulator label: {configuration.label}")
        configuration_labels.add(configuration.label)
        if not configuration.runs:
            raise RuntimeError(f"No runs configured for emulator {configuration.label}")

        run_labels = set()
        for run in configuration.runs:
            if run.label in run_labels:
                raise RuntimeError(
                    f"Duplicate run label on emulator {configuration.label}: {run.label}"
                )
            run_labels.add(run.label)


def wait_for_boot(adb: Path, serial: str) -> None:
    deadline = time.monotonic() + BOOT_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        try:
            result = subprocess.run(
                [adb, "-s", serial, "shell", "getprop", "sys.boot_completed"],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
            )
            if result.stdout.strip() == "1":
                return
        except subprocess.TimeoutExpired:
            pass
        time.sleep(2)
    raise RuntimeError(f"{serial} did not finish booting")


def install_apks(
    run: InstrumentationRun,
    adb: Path,
    serial: str,
    dry_run_only: bool,
) -> None:
    for command in apk_install_commands(run, adb, serial):
        if dry_run_only:
            print_command(command)
        else:
            subprocess.run(command, check=True)


def execute_instrumentation(
    run: InstrumentationRun,
    emulator_label: str,
    adb: Path,
    serial: str,
    dry_run_only: bool,
) -> int:
    command = instrumentation_command(run, adb, serial)
    if dry_run_only:
        print_command(command)
        return 0
    raw_output, junit_output = instrumentation_output_paths(emulator_label, run)
    instrumentation = subprocess.Popen(
        command,
        stdout=subprocess.PIPE,
        text=True,
    )
    if instrumentation.stdout is None:
        raise RuntimeError(f"Could not read instrumentation output for {run.label}")

    converter_exit_code = convert_stream(
        instrumentation.stdout,
        raw_output,
        junit_output,
    )
    instrumentation.stdout.close()
    instrumentation_exit_code = instrumentation.wait()
    return instrumentation_exit_code or converter_exit_code


def run_tests(
    run: InstrumentationRun,
    emulator_label: str,
    adb: Path,
    serial: str,
    dry_run_only: bool,
) -> int:
    if dry_run_only:
        print(f"  Run {run.label}:")
    install_apks(run, adb, serial, dry_run_only)
    return execute_instrumentation(
        run,
        emulator_label,
        adb,
        serial,
        dry_run_only,
    )


def run_on_emulator(
    configuration: EmulatorConfiguration,
    adb: Path,
    serial: str,
    dry_run_only: bool,
) -> int:
    if not dry_run_only:
        wait_for_boot(adb, serial)
    hide_error_dialogs_command = [
        adb, "-s", serial, "shell", "settings", "put", "global", "hide_error_dialogs", "1"
    ]
    if dry_run_only:
        print_command(hide_error_dialogs_command)
    else:
        subprocess.run(hide_error_dialogs_command, check=True)
    exit_code = 0
    for run in configuration.runs:
        if run_tests(run, configuration.label, adb, serial, dry_run_only) != 0:
            print(
                f"Instrumentation run {configuration.label}/{run.label} failed",
                file=sys.stderr,
            )
            exit_code = 1
    return exit_code


def run_instrumented_apks(
    configurations: Sequence[EmulatorConfiguration],
    dry_run_only: bool = False,
) -> int:
    if not configurations:
        raise RuntimeError("At least one emulator configuration is required")

    android_home = Path(required_environment("ANDROID_HOME"))
    tools = AndroidSdkTools.from_android_home(android_home)
    plans = create_emulator_plans(configurations)
    validate_plans(plans)
    junit_reports = [
        instrumentation_output_paths(plan.configuration.label, run)[1]
        for plan in plans
        for run in plan.configuration.runs
    ]

    emulator_processes: List[subprocess.Popen] = []
    emulator_logs: List[IO[bytes]] = []
    emulator_serials: List[str] = []

    def cleanup() -> None:
        for serial in emulator_serials:
            try:
                subprocess.run(
                    [tools.adb, "-s", serial, "emu", "kill"],
                    check=False,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    timeout=10,
                )
            except subprocess.TimeoutExpired:
                pass
        for process in emulator_processes:
            if process.poll() is None:
                process.terminate()
        for log in emulator_logs:
            log.close()

    signal.signal(signal.SIGTERM, lambda _signum, _frame: sys.exit(143))

    test_exit_code = 0
    upload_exit_code = 0
    try:
        install_android_components(tools, configurations, dry_run_only)

        for plan in plans:
            create_avd(tools, plan, dry_run_only)
            process, log = launch_emulator(tools, plan, dry_run_only)
            if process is not None and log is not None:
                emulator_serials.append(plan.serial)
                emulator_processes.append(process)
                emulator_logs.append(log)

        worker_count = 1 if dry_run_only else len(configurations)
        with ThreadPoolExecutor(max_workers=worker_count) as executor:
            futures = [
                executor.submit(
                    run_on_emulator,
                    plan.configuration,
                    tools.adb,
                    plan.serial,
                    dry_run_only,
                )
                for plan in plans
            ]
            for future in futures:
                if future.result() != 0:
                    test_exit_code = 1
    finally:
        cleanup()
        if not dry_run_only:
            upload_exit_code = upload_instrumented_junit(junit_reports)
    return test_exit_code or upload_exit_code
