/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger

internal class RumAppStartupTelemetryReporterImpl(
    private val internalLogger: InternalLogger,
    private val appStartupTimeNs: Long,
    private val contentProviderCreationTimeNs: Long,
    private val processStartImportance: Int
) : RumAppStartupTelemetryReporter {

    private val logger = DdSdkAndroidRumLogger(internalLogger)

    override fun reportTTID(
        info: RumTTIDInfo,
        indexInSession: Int
    ) {
        logger.logAppLaunchTtid(
            DdSdkAndroidRumLogger.AppLaunchTtid(
                scenario = info.scenario.name,
                durationNs = info.durationNs,
                indexInSession = indexInSession,
                cpProcessStartDiffNs = contentProviderCreationTimeNs - appStartupTimeNs,
                processStartImportance = processStartImportance,
                hasSavedInstanceState = info.scenario.hasSavedInstanceStateBundle,
                appStartActivityOnCreateGapNs = info.scenario.appStartActivityOnCreateGapNs
            )
        )
    }
}
