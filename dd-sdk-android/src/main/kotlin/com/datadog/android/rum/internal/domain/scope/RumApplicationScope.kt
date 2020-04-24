/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import java.util.UUID

internal class RumApplicationScope(
    internal val applicationId: UUID
) : RumScope {

    private val childScope: RumScope = RumSessionScope(this)

    // region RumScope

    override fun handleEvent(
        event: RumRawEvent,
        writer: Writer<RumEvent>
    ): RumScope? {
        childScope.handleEvent(event, writer)
        return this
    }

    override fun getRumContext(): RumContext {
        return RumContext(applicationId = applicationId)
    }

    // endregion
}
