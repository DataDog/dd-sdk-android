#!/usr/bin/env python3

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Run configured Android instrumentation tests on concurrent emulators."""

import argparse
import os
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


def parse_arguments(description: str) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate the configuration and print commands without executing them.",
    )
    return parser.parse_args()


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


def emulator_from_environment(
    label: str,
    runs: Sequence[InstrumentationRun],
) -> EmulatorConfiguration:
    return EmulatorConfiguration(
        label=label,
        emulator_image=required_environment("ANDROID_EMULATOR_IMAGE"),
        android_platform=required_environment("ANDROID_PLATFORM"),
        android_build_tools=required_environment("ANDROID_BUILD_TOOLS"),
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


def install_android_components(
    sdkmanager: Path,
    configurations: Sequence[EmulatorConfiguration],
) -> None:

    subprocess.run(
        [sdkmanager, "--install", *required_android_components(configurations)],
        check=True,
        input="y\n" * 100,
        text=True,
    )
    subprocess.run(
        [sdkmanager, "--licenses"],
        check=False,
        input="y\n" * 100,
        text=True,
    )


def print_command(command: Sequence[object]) -> None:
    print(shlex.join(str(argument) for argument in command))


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


def dry_run(
    configurations: Sequence[EmulatorConfiguration],
    android_home: Path,
    emulator_name: str,
) -> None:
    sdkmanager = android_home / "cmdline-tools" / "latest" / "bin" / "sdkmanager"
    avdmanager = android_home / "cmdline-tools" / "latest" / "bin" / "avdmanager"
    emulator = android_home / "emulator" / "emulator"
    adb = android_home / "platform-tools" / "adb"

    print("SDK components:")
    print_command([sdkmanager, "--install", *required_android_components(configurations)])

    configuration_labels = set()
    for index, configuration in enumerate(configurations):
        if configuration.label in configuration_labels:
            raise RuntimeError(f"Duplicate emulator label: {configuration.label}")
        configuration_labels.add(configuration.label)
        if not configuration.runs:
            raise RuntimeError(f"No runs configured for emulator {configuration.label}")

        avd_name = f"{emulator_name}_{configuration.label.replace('-', '_')}"
        port = FIRST_EMULATOR_PORT + index * 2
        serial = f"emulator-{port}"
        print(f"\nEmulator {configuration.label}:")
        print_command(
            [
                avdmanager,
                "--verbose",
                "create",
                "avd",
                "--force",
                "--name",
                avd_name,
                "--package",
                configuration.emulator_image,
            ]
        )
        print_command(
            [
                emulator,
                "-avd",
                avd_name,
                "-port",
                port,
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
        )

        run_labels = set()
        for run in configuration.runs:
            if run.label in run_labels:
                raise RuntimeError(
                    f"Duplicate run label on emulator {configuration.label}: {run.label}"
                )
            run_labels.add(run.label)
            print(f"  Run {run.label}:")
            for command in apk_install_commands(run, adb, serial):
                print_command(command)
            print_command(instrumentation_command(run, adb, serial))


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


def run_tests(
    run: InstrumentationRun,
    emulator_label: str,
    adb: Path,
    serial: str,
) -> int:
    for command in apk_install_commands(run, adb, serial):
        subprocess.run(command, check=True)

    output_suffix = f"-{emulator_label}-{run.label}"
    instrumentation = subprocess.Popen(
        instrumentation_command(run, adb, serial),
        stdout=subprocess.PIPE,
        text=True,
    )
    if instrumentation.stdout is None:
        raise RuntimeError(f"Could not read instrumentation output for {run.label}")

    converter_exit_code = convert_stream(
        instrumentation.stdout,
        Path(f"instrumentation-output{output_suffix}.txt"),
        Path(f"instrumentation-results{output_suffix}.xml"),
    )
    instrumentation.stdout.close()
    instrumentation_exit_code = instrumentation.wait()
    return instrumentation_exit_code or converter_exit_code


def run_on_emulator(
    configuration: EmulatorConfiguration,
    adb: Path,
    serial: str,
) -> int:
    wait_for_boot(adb, serial)
    exit_code = 0
    for run in configuration.runs:
        if run_tests(run, configuration.label, adb, serial) != 0:
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
    emulator_name = required_environment("EMULATOR_NAME")
    if dry_run_only:
        dry_run(configurations, android_home, emulator_name)
        return 0

    adb = android_home / "platform-tools" / "adb"
    emulator = android_home / "emulator" / "emulator"
    sdkmanager = android_home / "cmdline-tools" / "latest" / "bin" / "sdkmanager"
    avdmanager = android_home / "cmdline-tools" / "latest" / "bin" / "avdmanager"

    emulator_processes: List[subprocess.Popen] = []
    emulator_logs: List[IO[bytes]] = []
    emulator_serials: List[str] = []

    def cleanup() -> None:
        for serial in emulator_serials:
            try:
                subprocess.run(
                    [adb, "-s", serial, "emu", "kill"],
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

    try:
        install_android_components(sdkmanager, configurations)

        for index, configuration in enumerate(configurations):
            avd_name = f"{emulator_name}_{configuration.label.replace('-', '_')}"
            subprocess.run(
                [
                    avdmanager,
                    "--verbose",
                    "create",
                    "avd",
                    "--force",
                    "--name",
                    avd_name,
                    "--package",
                    configuration.emulator_image,
                ],
                check=True,
                input="no\n",
                text=True,
            )

            port = FIRST_EMULATOR_PORT + index * 2
            serial = f"emulator-{port}"
            emulator_serials.append(serial)
            log = Path(f"emulator-{configuration.label}.log").open("wb")
            emulator_logs.append(log)
            emulator_processes.append(
                subprocess.Popen(
                    [
                        emulator,
                        "-avd",
                        avd_name,
                        "-port",
                        str(port),
                        "-grpc-use-jwt",
                        "-no-snapstorage",
                        "-no-audio",
                        "-no-window",
                        "-no-boot-anim",
                        "-verbose",
                        "-qemu",
                        "-machine",
                        "virt",
                    ],
                    stdout=log,
                    stderr=subprocess.STDOUT,
                )
            )

        with ThreadPoolExecutor(max_workers=len(configurations)) as executor:
            futures = [
                executor.submit(
                    run_on_emulator,
                    configuration,
                    adb,
                    emulator_serials[index],
                )
                for index, configuration in enumerate(configurations)
            ]
            exit_code = 0
            for future in futures:
                if future.result() != 0:
                    exit_code = 1
            return exit_code
    finally:
        cleanup()
