#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.

API_SURFACE_PATHS = [
    "dd-sdk-android/apiSurface",
    "integrations/dd-sdk-android-okhttp/apiSurface",
    "features/dd-sdk-android-logs/apiSurface",
    "features/dd-sdk-android-session-replay/apiSurface",
    "features/dd-sdk-android-logs/apiSurface",
    "features/dd-sdk-android-ndk/apiSurface",
    "features/dd-sdk-android-trace/apiSurface",
    "features/dd-sdk-android-webview/apiSurface",
    "features/dd-sdk-android-rum/apiSurface"
]
NIGHTLY_TESTS_DIRECTORY_PATH = "instrumented/nightly-tests/src/androidTest/kotlin"
NIGHTLY_TESTS_PACKAGE = "com/datadog/android/nightly"
IGNORED_TYPES = [
    "com.datadog.android.trace.model.SpanEvent$Span",
    "com.datadog.android.rum.model.ActionEvent$Dd",
    "com.datadog.android.rum.model.ErrorEvent$Dd",
    "com.datadog.android.rum.model.LongTaskEvent$Dd",
    "com.datadog.android.telemetry.model.TelemetryDebugEvent$Dd",
    "com.datadog.android.telemetry.model.TelemetryErrorEvent$Dd",
    "com.datadog.android.telemetry.model.TelemetryConfigurationEvent$Dd"
]
