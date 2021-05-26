/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent

internal class RumApplicationScope(
    applicationId: String,
    internal val samplingRate: Float,
    private val firstPartyHostDetector: FirstPartyHostDetector
) : RumScope {

    private val rumContext = RumContext(applicationId = applicationId.toString())
    internal val childScope: RumScope = RumSessionScope(this, samplingRate, firstPartyHostDetector)

    // region RumScope

    override fun handleEvent(
        event: RumRawEvent,
        writer: DataWriter<RumEvent>
    ): RumScope {
        childScope.handleEvent(event, writer)
        return this
    }

    override fun getRumContext(): RumContext {
        return rumContext
    }

    // endregion
}
