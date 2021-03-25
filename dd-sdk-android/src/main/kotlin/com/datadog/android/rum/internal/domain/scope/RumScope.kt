/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface RumScope {

    /**
     * Handles an incoming event.
     * If needed, writes a RumEvent to the provided writer.
     * @return this instance if this scope is still valid, or null if it no longer can process
     * events
     */
    fun handleEvent(
        event: RumRawEvent,
        writer: DataWriter<RumEvent>
    ): RumScope?

    fun getRumContext(): RumContext
}
