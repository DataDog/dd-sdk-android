/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.os.Handler
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.sessionreplay.internal.prerequisite.CPURequirementChecker
import com.datadog.android.sessionreplay.internal.prerequisite.MemoryRequirementChecker
import com.datadog.android.sessionreplay.internal.prerequisite.SystemRequirementChecker

/**
 * This class defines the system requirements necessary to enable the Session Replay feature.
 */
class SystemRequirementsConfiguration internal constructor(
    internal val minCPUCores: Int,
    internal val minRAMSizeMb: Int
) {

    internal fun runIfRequirementsMet(
        sdkCore: FeatureSdkCore,
        uiHandler: Handler,
        internalLogger: InternalLogger,
        runnable: () -> Unit
    ) {
        val checkers = listOf(
            CPURequirementChecker(minCPUCores, internalLogger = internalLogger),
            MemoryRequirementChecker(minRAMSizeMb, internalLogger = internalLogger)
        )
        (sdkCore as InternalSdkCore).getPersistenceExecutorService().submitSafe(OPERATION_NAME, internalLogger) {
            val checkResult = checkers.all {
                it.checkMinimumRequirement()
            }

            if (checkResult) {
                uiHandler.post {
                    runnable()
                }
            } else {
                internalLogger.log(
                    level = InternalLogger.Level.INFO,
                    listOf(InternalLogger.Target.TELEMETRY, InternalLogger.Target.USER),
                    messageBuilder = {
                        "Session replay is disabled because the system doesn't meet the minimum " +
                            "Session Replay requirements"
                    },
                    onlyOnce = true,
                    additionalProperties = getCheckerReport(checkers)
                )
            }
        }
    }

    private fun getCheckerReport(checkers: List<SystemRequirementChecker>): Map<String, Any?> {
        return mapOf(
            ATTRIBUTE_DEVICE_STATS_KEY to checkers.associate { it.name() to it.checkedValue() }
        )
    }

    /**
     * Builder class for configuring and creating instances of the [SystemRequirementsConfiguration] needed to
     * enable the Session Replay feature.
     *
     */
    class Builder {
        private var minCPUCoreNumber: Int = 0
        private var minRAMSizeMb: Int = 0

        /**
         * Sets the minimum CPU core number requirement.
         *
         * @param cpuCoreNumber The minimum CPU core number.
         */
        fun setMinCPUCoreNumber(cpuCoreNumber: Int): Builder {
            this.minCPUCoreNumber = cpuCoreNumber
            return this
        }

        /**
         * Sets the minimum requirement of total RAM of the device in megabytes.
         *
         * @param minRAMSizeMb The minimum RAM in megabytes.
         */
        fun setMinRAMSizeMb(minRAMSizeMb: Int): Builder {
            this.minRAMSizeMb = minRAMSizeMb
            return this
        }

        /**
         * Builds and returns an instance of the [SystemRequirementsConfiguration] with the configured parameters.
         *
         */
        fun build(): SystemRequirementsConfiguration {
            return SystemRequirementsConfiguration(
                minCPUCores = minCPUCoreNumber,
                minRAMSizeMb = minRAMSizeMb
            )
        }
    }

    companion object {

        private const val ATTRIBUTE_DEVICE_STATS_KEY = "device_stats"
        private const val OPERATION_NAME = "Check Session Replay requirements"

        /**
         * A preconfigured instance representing the basic system requirements for enabling the Session Replay feature.
         *
         * This instance provides reasonable basic values for most systems:
         * - Minimum RAM: 1024 MB
         * - Minimum CPU core number: 2
         *
         * Use this instance when standard system requirements are sufficient.
         */
        val BASIC: SystemRequirementsConfiguration = SystemRequirementsConfiguration(
            minCPUCores = 2,
            minRAMSizeMb = 1024
        )

        /**
         * A special instance representing no system requirements for enabling the Session Replay feature.
         *
         * With this instance, Session Replay will be enabled regardless of system specifications.
         * This is useful in cases where system requirements should not restrict functionality.
         */
        val NONE: SystemRequirementsConfiguration = SystemRequirementsConfiguration(
            minCPUCores = 0,
            minRAMSizeMb = 0
        )
    }
}
