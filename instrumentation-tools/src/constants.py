#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.

API_SURFACE_PATHS = [
    "dd-sdk-android-core/api/apiSurface",
    "integrations/dd-sdk-android-okhttp/api/apiSurface",
    "features/dd-sdk-android-logs/api/apiSurface",
    "features/dd-sdk-android-session-replay/api/apiSurface",
    "features/dd-sdk-android-session-replay-material/api/apiSurface",
    "features/dd-sdk-android-ndk/api/apiSurface",
    "features/dd-sdk-android-trace/api/apiSurface",
    "features/dd-sdk-android-webview/api/apiSurface",
    "features/dd-sdk-android-rum/api/apiSurface"
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
