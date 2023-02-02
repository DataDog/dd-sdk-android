/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.storage.DataWriter

@Suppress("LongParameterList")
internal class RumApplicationScope(
    applicationId: String,
    sdkCore: SdkCore,
    internal val samplingRate: Float,
    internal val backgroundTrackingEnabled: Boolean,
    internal val trackFrustrations: Boolean,
    firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    cpuVitalMonitor: VitalMonitor,
    memoryVitalMonitor: VitalMonitor,
    frameRateVitalMonitor: VitalMonitor,
    sessionListener: RumSessionListener?,
    contextProvider: ContextProvider
) : RumScope {

    private val rumContext = RumContext(applicationId = applicationId)
    internal val childScope: RumScope = RumSessionScope(
        this,
        sdkCore,
        samplingRate,
        backgroundTrackingEnabled,
        trackFrustrations,
        firstPartyHostHeaderTypeResolver,
        cpuVitalMonitor,
        memoryVitalMonitor,
        frameRateVitalMonitor,
        sessionListener,
        contextProvider
    )

    // region RumScope

    @WorkerThread
    override fun handleEvent(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ): RumScope {
        childScope.handleEvent(event, writer)
        return this
    }

    override fun isActive(): Boolean {
        return true
    }

    override fun getRumContext(): RumContext {
        return rumContext
    }

    // endregion
}
