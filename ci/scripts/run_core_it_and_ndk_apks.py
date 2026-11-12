#!/usr/bin/env python3

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Run core-it on all supported API levels and NDK tests on the latest API."""

import sys

from run_instrumented_apks import (
    InstrumentationRun,
    emulator_for_api_level,
    parse_arguments,
    run_instrumented_apks,
)


def main() -> int:
    arguments = parse_arguments(__doc__ or "Run core-it and NDK instrumented tests.")
    core_it_run = InstrumentationRun(
        label="core-it",
        app_apk_directory="reliability/core-it/build/outputs/apk/debug",
        test_apk_directory="reliability/core-it/build/outputs/apk/androidTest/debug",
        instrumentation_package="com.datadog.android.core.integration.test",
    )
    ndk_run = InstrumentationRun(
        label="ndk",
        test_apk_directory="features/dd-sdk-android-ndk/build/outputs/apk/androidTest/debug",
        instrumentation_package="com.datadog.android.ndk.test",
    )
    return run_instrumented_apks(
        (
            emulator_for_api_level(
                label="api-23",
                api_level=23,
                runs=(core_it_run,),
            ),
            emulator_for_api_level(
                label="api-28",
                api_level=28,
                runs=(core_it_run,),
            ),
            emulator_for_api_level(
                label="api-37",
                api_level=37,
                runs=(core_it_run, ndk_run),
            ),
        ),
        dry_run_only=arguments.dry_run,
    )


if __name__ == "__main__":
    sys.exit(main())
