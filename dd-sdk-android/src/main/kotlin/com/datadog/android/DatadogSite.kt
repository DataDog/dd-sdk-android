/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

/**
 * Defines the Datadog sites you can send tracked data to.
 */
enum class DatadogSite {
    /**
     *  The US1 site: [app.datadoghq.com](https://app.datadoghq.com).
     */
    US1,

    /**
     *  The US3 site: [us3.datadoghq.com](https://us3.datadoghq.com).
     */
    US3,

    /**
     *  The US1_FED site (FedRAMP compatible): [app.ddog-gov.com](https://app.ddog-gov.com).
     */
    US1_FED,

    /**
     *  The EU1 site: [app.datadoghq.eu](https://app.datadoghq.eu).
     */
    EU1;

    /**
     * Returns the endpoint to use to upload Logs to this site.
     */
    fun logsEndpoint(): String {
        return when (this) {
            US1 -> DatadogEndpoint.LOGS_US1
            US3 -> DatadogEndpoint.LOGS_US3
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
            US1_FED -> DatadogEndpoint.RUM_US1_FED
            EU1 -> DatadogEndpoint.RUM_EU1
        }
    }
}
