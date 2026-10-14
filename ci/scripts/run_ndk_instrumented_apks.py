#!/usr/bin/env python3

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Run the NDK instrumented tests."""

import sys

from run_instrumented_apks import (
    InstrumentationRun,
    emulator_from_environment,
    run_instrumented_apks,
)


def main() -> int:
    run = InstrumentationRun(
        label="ndk",
        test_apk_directory="features/dd-sdk-android-ndk/build/outputs/apk/androidTest/debug",
        instrumentation_package="com.datadog.android.ndk.test",
    )
    return run_instrumented_apks((emulator_from_environment("ndk", (run,)),))


if __name__ == "__main__":
    sys.exit(main())
