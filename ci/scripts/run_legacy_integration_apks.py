#!/usr/bin/env python3

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Run the legacy integration suite in three package shards."""

import os
import shlex
import sys
from typing import Optional, Tuple

from run_instrumented_apks import (
    InstrumentationRun,
    emulator_from_environment,
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
        arguments=arguments,
        include_package=include_package,
        exclude_packages=exclude_packages,
    )


def main() -> int:
    arguments = tuple(shlex.split(os.environ.get("INSTRUMENTATION_ARGUMENTS", "")))
    return run_instrumented_apks(
        (
            emulator_from_environment(
                "rum",
                (
                    instrumentation_run(
                        "rum",
                        arguments,
                        "com.datadog.android.sdk.integration.rum",
                    ),
                ),
            ),
            emulator_from_environment(
                "session-replay",
                (
                    instrumentation_run(
                        "session-replay",
                        arguments,
                        "com.datadog.android.sdk.integration.sessionreplay",
                    ),
                ),
            ),
            emulator_from_environment(
                "other",
                (
                    instrumentation_run(
                        "other",
                        arguments,
                        "com.datadog.android.sdk.integration",
                        "com.datadog.android.sdk.integration.rum,"
                        "com.datadog.android.sdk.integration.sessionreplay",
                    ),
                ),
            ),
        )
    )


if __name__ == "__main__":
    sys.exit(main())
