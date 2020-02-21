/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.Datadog.initialize

/**
 * This object contains constant values for all the Datadog Endpoint urls used in the SDK.
 */
object DatadogEndpoint {

    /**
     * The endpoint for Logs (US based servers), used by default by the SDK.
     * @see [DatadogConfig]
     */
    const val LOGS_US: String = "https://mobile-http-intake.logs.datadoghq.com"

    /**
     * The endpoint for Logs (Europe based servers).
     * Use this in your [DatadogConfig] if you log on
     * [app.datadoghq.eu](https://app.datadoghq.eu/) instead of
     * [app.datadoghq.com](https://app.datadoghq.com/)
     */
    const val LOGS_EU: String = "https://mobile-http-intake.logs.datadoghq.eu"

    /**
     * The endpoint for Traces (US based servers), used by default by the SDK.
     * @see [initialize]
     */
    const val TRACES_US: String = "https://public-trace-http-intake.logs.datadoghq.com"

    /**
     * The endpoint for Traces (Europe based servers).
     * Use this in your [DatadogConfig] if you log on
     * [app.datadoghq.eu](https://app.datadoghq.eu/) instead of
     * [app.datadoghq.com](https://app.datadoghq.com/)
     */
    const val TRACES_EU: String = "https://public-trace-http-intake.logs.datadoghq.eu"

    /**
     * The endpoint for Real User Monitoring (US based servers), used by default by the SDK.
     * @see [DatadogConfig]
     */
    const val RUM_US: String = "https://rum-http-intake.logs.datadoghq.com"

    /**
     * The endpoint for Real User Monitoring (Europe based servers).
     * Use this in your [DatadogConfig] if you log on
     * [app.datadoghq.eu](https://app.datadoghq.eu/) instead of
     * [app.datadoghq.com](https://app.datadoghq.com/)
     */
    const val RUM_EU: String = "https://rum-http-intake.logs.datadoghq.eu"
}
