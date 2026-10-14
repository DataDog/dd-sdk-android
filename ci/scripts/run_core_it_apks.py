#!/usr/bin/env python3

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Run the complete core-it suite concurrently on the supported API levels."""

import sys

from run_instrumented_apks import (
    EmulatorConfiguration,
    InstrumentationRun,
    parse_arguments,
    required_environment,
    run_instrumented_apks,
)


def main() -> int:
    arguments = parse_arguments(__doc__ or "Run core-it instrumented tests.")
    android_arch = required_environment("ANDROID_ARCH")
    run = InstrumentationRun(
        label="core-it",
        app_apk_directory="reliability/core-it/build/outputs/apk/debug",
        test_apk_directory="reliability/core-it/build/outputs/apk/androidTest/debug",
        instrumentation_package="com.datadog.android.core.integration.test",
    )
    return run_instrumented_apks(
        (
            EmulatorConfiguration(
                label="api-23",
                emulator_image=f"system-images;android-23;google_apis;{android_arch}",
                android_platform="platforms;android-23",
                android_build_tools="build-tools;23.0.0",
                runs=(run,),
            ),
            EmulatorConfiguration(
                label="api-28",
                emulator_image=f"system-images;android-28;google_apis;{android_arch}",
                android_platform="platforms;android-28",
                android_build_tools="build-tools;28.0.0",
                runs=(run,),
            ),
            EmulatorConfiguration(
                label="api-37",
                emulator_image=f"system-images;android-37.0;google_apis_ps16k;{android_arch}",
                android_platform="platforms;android-37.0",
                android_build_tools="build-tools;37.0.0",
                runs=(run,),
            ),
        ),
        dry_run_only=arguments.dry_run,
    )


if __name__ == "__main__":
    sys.exit(main())
