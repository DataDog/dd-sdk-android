#!/usr/bin/env python3

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Run the complete legacy integration suite on one emulator."""

import os
import shlex
import sys
from typing import Optional, Tuple

from run_instrumented_apks import (
    InstrumentationRun,
    create_argument_parser,
    emulator_for_api_level,
    run_instrumented_apks,
)


def instrumentation_run(
    label: str,
    arguments: Tuple[str, ...],
    include_package: str,
    exclude_packages: Optional[str] = None,
) -> InstrumentationRun:
    return InstrumentationRun(
        label=label,
        app_apk_directory="instrumented/integration/build/outputs/apk/debug",
        test_apk_directory="instrumented/integration/build/outputs/apk/androidTest/debug",
        instrumentation_package="com.datadog.android.sdk.integration.test",
        arguments=(*arguments, "-e", "captureFailureDiagnostics", "true"),
        diagnostics_package="com.datadog.android.sdk.integration",
        include_package=include_package,
        exclude_packages=exclude_packages,
    )


def main() -> int:
    argument_parser = create_argument_parser(
        __doc__ or "Run legacy instrumented tests."
    )
    argument_parser.add_argument("--api-level", type=int, required=True)
    command_arguments = argument_parser.parse_args()
    instrumentation_arguments = tuple(
        shlex.split(os.environ.get("INSTRUMENTATION_ARGUMENTS", ""))
    )
    api_level = command_arguments.api_level
    return run_instrumented_apks(
        (
            emulator_for_api_level(
                f"api-{api_level}-all",
                api_level,
                (
                    instrumentation_run(
                        "all",
                        instrumentation_arguments,
                        "com.datadog.android.sdk.integration",
                    ),
                ),
            ),
        ),
        dry_run_only=command_arguments.dry_run,
    )


if __name__ == "__main__":
    sys.exit(main())
