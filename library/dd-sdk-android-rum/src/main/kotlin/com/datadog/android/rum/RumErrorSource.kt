/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * Describe the source of a RUM Error.
 * @see [RumMonitor]
 */
enum class RumErrorSource {
    /** Error originated in the Network layer. */
    NETWORK,

    /** Error originated in the source code (usually a crash). */
    SOURCE,

    /** Error originated in a console (e.g.: in a webview). */
    CONSOLE,

    /** Error extracted from a logged error. */
    LOGGER,

    /** Error originated in an Agent. */
    AGENT,

    /** Error originated in a WebView. */
    WEBVIEW
}
