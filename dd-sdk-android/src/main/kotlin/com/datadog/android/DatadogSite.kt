/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

/**
 * Defines the Datadog sites you can send tracked data to.
 *
 * @property siteName Explicit site name property introduced in order to have a consistent SDK
 * instance ID (because this value is used there) in case if enum values are renamed.
 */
// TODO RUMM-0000 Remove logs, traces, rum endpoint methods, because this class is for core SDK v2
// TODO RUMM-0000 Since it is used in SDK v2 context, what should be the value for custom endpoints?
enum class DatadogSite(val siteName: String) {
    /**
     *  The US1 site: [app.datadoghq.com](https://app.datadoghq.com).
     */
    US1("us1"),

    /**
     *  The US3 site: [us3.datadoghq.com](https://us3.datadoghq.com).
     */
    US3("us3"),

    /**
     *  The US5 site: [us5.datadoghq.com](https://us5.datadoghq.com).
     */
    US5("us5"),

    /**
     *  The US1_FED site (FedRAMP compatible): [app.ddog-gov.com](https://app.ddog-gov.com).
     */
    US1_FED("us1_fed"),

    /**
     *  The EU1 site: [app.datadoghq.eu](https://app.datadoghq.eu).
     */
    EU1("eu1");

    /**
     * Returns the endpoint to use to upload Logs to this site.
     */
    fun logsEndpoint(): String {
        return when (this) {
            US1 -> DatadogEndpoint.LOGS_US1
            US3 -> DatadogEndpoint.LOGS_US3
            US5 -> DatadogEndpoint.LOGS_US5
            US1_FED -> DatadogEndpoint.LOGS_US1_FED
            EU1 -> DatadogEndpoint.LOGS_EU1
        }
    }

    /**
     * Returns the endpoint to use to upload Spans to this site.
     */
    fun tracesEndpoint(): String {
        return when (this) {
            US1 -> DatadogEndpoint.TRACES_US1
            US3 -> DatadogEndpoint.TRACES_US3
            US5 -> DatadogEndpoint.TRACES_US5
            US1_FED -> DatadogEndpoint.TRACES_US1_FED
            EU1 -> DatadogEndpoint.TRACES_EU1
        }
    }

    /**
     * Returns the endpoint to use to upload RUM Events to this site.
     */
    fun rumEndpoint(): String {
        return when (this) {
            US1 -> DatadogEndpoint.RUM_US1
            US3 -> DatadogEndpoint.RUM_US3
            US5 -> DatadogEndpoint.RUM_US5
            US1_FED -> DatadogEndpoint.RUM_US1_FED
            EU1 -> DatadogEndpoint.RUM_EU1
        }
    }

    /**
     * Returns the endpoint to use to upload RUM Events to this site.
     */
    fun sessionReplayEndpoint(): String {
        return when (this) {
            US1 -> DatadogEndpoint.SESSION_REPLAY_US1
            US3 -> DatadogEndpoint.SESSION_REPLAY_US3
            US5 -> DatadogEndpoint.SESSION_REPLAY_US5
            US1_FED -> DatadogEndpoint.SESSION_REPLAY_US1_FED
            EU1 -> DatadogEndpoint.SESSION_REPLAY_EU1
        }
    }
}
