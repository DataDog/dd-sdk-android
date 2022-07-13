/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.configuration.Configuration

/**
 * This object contains constant values for all the Datadog Endpoint urls used in the SDK.
 */
object DatadogEndpoint {

    //  region Logs

    /**
     * The US1 endpoint for Logs (US based servers), used by default by the SDK.
     * Use this in your [Configuration] if you log on [app.datadoghq.com](https://app.datadoghq.com)
     * @see [Configuration]
     */
    const val LOGS_US1: String = "https://logs.browser-intake-datadoghq.com"

    /**
     * The US3 endpoint for Logs (US based servers).
     * Use this in your [Configuration] if you log on [us3.datadoghq.com](https://us3.datadoghq.com)
     * @see [Configuration]
     */
    const val LOGS_US3: String = "https://logs.browser-intake-us3-datadoghq.com"

    /**
     * The US5 endpoint for Logs (US based servers).
     * Use this in your [Configuration] if you log on [us5.datadoghq.com](https://us5.datadoghq.com)
     * @see [Configuration]
     */
    const val LOGS_US5: String = "https://logs.browser-intake-us5-datadoghq.com"

    /**
     * The US1_FED endpoint for Logs (US based servers, FedRAMP compliant).
     * Use this in your [Configuration] if you log on [app.ddog-gov.com](https://app.ddog-gov.com)
     * @see [Configuration]
     */
    const val LOGS_US1_FED: String = "https://logs.browser-intake-ddog-gov.com"

    /**
     * The EU1 endpoint for Logs (EU based servers).
     * Use this in your [Configuration] if you log on [app.datadoghq.eu](https://app.datadoghq.eu)
     * @see [Configuration]
     */
    const val LOGS_EU1: String = "https://mobile-http-intake.logs.datadoghq.eu"

    // endregion

    //  region Trace

    /**
     * The US1 endpoint for Traces (US based servers), used by default by the SDK.
     * Use this in your [Configuration] if you log on [app.datadoghq.com](https://app.datadoghq.com)
     * @see [Configuration]
     */
    const val TRACES_US1: String = "https://trace.browser-intake-datadoghq.com"

    /**
     * The US3 endpoint for Traces (US based servers).
     * Use this in your [Configuration] if you log on [us3.datadoghq.com](https://us3.datadoghq.com)
     * @see [Configuration]
     */
    const val TRACES_US3: String = "https://trace.browser-intake-us3-datadoghq.com"

    /**
     * The US5 endpoint for Traces (US based servers).
     * Use this in your [Configuration] if you log on [us5.datadoghq.com](https://us5.datadoghq.com)
     * @see [Configuration]
     */
    const val TRACES_US5: String = "https://trace.browser-intake-us5-datadoghq.com"

    /**
     * The US1_FED endpoint for Traces (US based servers, FedRAMP compliant).
     * Use this in your [Configuration] if you log on [app.ddog-gov.com](https://app.ddog-gov.com)
     * @see [Configuration]
     */
    const val TRACES_US1_FED: String = "https://trace.browser-intake-ddog-gov.com"

    /**
     * The EU1 endpoint for Traces (EU based servers).
     * Use this in your [Configuration] if you log on [app.datadoghq.eu](https://app.datadoghq.eu)
     * @see [Configuration]
     */
    const val TRACES_EU1: String = "https:/public-trace-http-intake.logs.datadoghq.eu"

    // endregion

    //  region RUM

    /**
     * The US1 endpoint for RUM (US based servers), used by default by the SDK.
     * Use this in your [Configuration] if you log on [app.datadoghq.com](https://app.datadoghq.com)
     * @see [Configuration]
     */
    const val RUM_US1: String = "https://rum.browser-intake-datadoghq.com"

    /**
     * The US3 endpoint for RUM (US based servers).
     * Use this in your [Configuration] if you log on [us3.datadoghq.com](https://us3.datadoghq.com)
     * @see [Configuration]
     */
    const val RUM_US3: String = "https://rum.browser-intake-us3-datadoghq.com"

    /**
     * The US5 endpoint for RUM (US based servers).
     * Use this in your [Configuration] if you log on [us5.datadoghq.com](https://us5.datadoghq.com)
     * @see [Configuration]
     */
    const val RUM_US5: String = "https://rum.browser-intake-us5-datadoghq.com"

    /**
     * The US1_FED endpoint for RUM (US based servers, FedRAMP compliant).
     * Use this in your [Configuration] if you log on [app.ddog-gov.com](https://app.ddog-gov.com)
     * @see [Configuration]
     */
    const val RUM_US1_FED: String = "https://rum.browser-intake-ddog-gov.com"

    /**
     * The EU1 endpoint for RUM (EU based servers).
     * Use this in your [Configuration] if you log on [app.datadoghq.eu](https://app.datadoghq.eu)
     * @see [Configuration]
     */
    const val RUM_EU1: String = "https://rum-http-intake.logs.datadoghq.eu"

    // endregion

    //  region NTP

    /**
     * Endpoint for the Network Time Protocol time syncing.
     */
    const val NTP_0: String = "0.datadog.pool.ntp.org"

    /**
     * Endpoint for the Network Time Protocol time syncing.
     */
    const val NTP_1: String = "1.datadog.pool.ntp.org"

    /**
     * Endpoint for the Network Time Protocol time syncing.
     */
    const val NTP_2: String = "2.datadog.pool.ntp.org"

    /**
     * Endpoint for the Network Time Protocol time syncing.
     */
    const val NTP_3: String = "3.datadog.pool.ntp.org"

    // endregion

    // region Session Replay

    /**
     * The US1 endpoint for Session Replay (US based servers).
     * Use this in your [Configuration] if you want to point to
     * [app.datadoghq.com](https://app.datadoghq.com)
     * @see [Configuration]
     */
    const val SESSION_REPLAY_US1: String = "https://session-replay.browser-intake-datadoghq.com"

    /**
     * The US3 endpoint for Session Replay (US based servers).
     * Use this in your [Configuration] if you want to point to
     * [us3.datadoghq.com](https://us3.datadoghq.com)
     * @see [Configuration]
     */
    const val SESSION_REPLAY_US3: String = "https://session-replay.browser-intake-us3-datadoghq.com"

    /**
     * The US5 endpoint for Session replay (US based servers).
     * Use this in your [Configuration] if you want to point to
     * [us5.datadoghq.com](https://us5.datadoghq.com)
     * @see [Configuration]
     */
    const val SESSION_REPLAY_US5: String = "https://session-replay.browser-intake-us5-datadoghq.com"

    /**
     * The US1_FED endpoint for Session replay (US based servers, FedRAMP compliant).
     * Use this in your [Configuration] if you want to point to [app.ddog-gov.com](https://app
     * .ddog-gov.com)
     * @see [Configuration]
     */
    const val SESSION_REPLAY_US1_FED: String = "https://session-replay.browser-intake-ddog-gov.com"

    /**
     * The EU1 endpoint for Session Replay (EU based servers).
     * Use this in your [Configuration] if you want to point to
     * [app.datadoghq.eu](https://app.datadoghq.eu)
     * @see [Configuration]
     */
    const val SESSION_REPLAY_EU1: String = "https://session-replay.browser-intake-datadoghq.eu"

    // endregion
}
