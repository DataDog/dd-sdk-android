/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark

/**
 * Describes the configuration to be used in Datadog open telemetry exporter.
 * @param serviceName the name of the service.
 * @param resource the name of resource.
 * @param applicationName the name of host application.
 * @param applicationVersion the version of host application.
 * @param applicationId the id of the host application.
 * @param apiKey the api key for submitting metrics to datadog end points.
 * @param run the run description of benchmark test.
 * @param scenario the scenario of benchmark.
 * @param endPoint the endpoint to submit the metrics.
 * @param intervalInSeconds the interval of seconds of sampling and uploading vital data.
 */
data class DatadogExporterConfiguration internal constructor(
    val serviceName: String? = null,
    val resource: String? = null,
    val applicationName: String? = null,
    val applicationVersion: String? = null,
    val applicationId: String? = null,
    val apiKey: String,
    val run: String? = null,
    val scenario: String? = null,
    val endPoint: EndPoint = EndPoint.US1,
    val intervalInSeconds: Long = DEFAULT_INTERVAL_IN_SECONDS
) {
    /**
     * A Builder class for a [DatadogExporterConfiguration].
     *
     * @param apiKey api key to submit metrics to the Datadog endpoint
     */
    class Builder(private val apiKey: String) {
        private var serviceName: String? = null
        private var resource: String? = null
        private var applicationName: String? = null
        private var applicationVersion: String? = null
        private var applicationId: String? = null
        private var run: String? = null
        private var scenario: String? = null
        private var endPoint: EndPoint = EndPoint.US1
        private var intervalInSeconds: Long = DEFAULT_INTERVAL_IN_SECONDS

        /**
         * Sets the service name for the metric.
         *
         * @param serviceName service name used as resource service name in metric data
         */
        fun setServiceName(serviceName: String): Builder {
            this.serviceName = serviceName
            return this
        }

        /**
         * Sets the resource name for the metric.
         *
         * @param resource resource name
         */
        fun setResource(resource: String): Builder {
            this.resource = resource
            return this
        }

        /**
         * Sets the host application name for the metric.
         *
         * @param applicationName application name
         */
        fun setApplicationName(applicationName: String): Builder {
            this.applicationName = applicationName
            return this
        }

        /**
         * Sets the interval of meter sampling and upload.
         *
         * @param intervalInSeconds interval in seconds
         */
        fun setIntervalInSeconds(intervalInSeconds: Long): Builder {
            this.intervalInSeconds = intervalInSeconds
            return this
        }

        /**
         * Sets the host application id, default value is 10 seconds.
         *
         * @param applicationId host application id
         */
        fun setApplicationId(applicationId: String): Builder {
            this.applicationId = applicationId
            return this
        }

        /**
         * Sets the host application version name.
         *
         * @param applicationVersion application version name
         */
        fun setApplicationVersion(applicationVersion: String): Builder {
            this.applicationVersion = applicationVersion
            return this
        }

        /**
         * Sets the run environment description.
         *
         * @param run run environment description
         */
        fun setRun(run: String): Builder {
            this.run = run
            return this
        }

        /**
         * Sets the benchmark scenario.
         *
         * @param scenario benchmark scenario.
         */
        fun setScenario(scenario: String): Builder {
            this.scenario = scenario
            return this
        }

        /**
         * Sets the end point which the metric should be uploaded to.
         *
         * @param endPoint endpoint of the metric upload.
         */
        fun setEndpoint(endPoint: EndPoint): Builder {
            this.endPoint = endPoint
            return this
        }

        /**
         * Builds the instance of [DatadogExporterConfiguration].
         */
        fun build(): DatadogExporterConfiguration {
            return DatadogExporterConfiguration(
                serviceName = serviceName,
                resource = resource,
                applicationName = applicationName,
                applicationVersion = applicationVersion,
                applicationId = applicationId,
                apiKey = apiKey,
                run = run,
                scenario = scenario,
                endPoint = endPoint,
                intervalInSeconds = intervalInSeconds
            )
        }
    }

    companion object {
        private const val DEFAULT_INTERVAL_IN_SECONDS = 10L
    }
}
