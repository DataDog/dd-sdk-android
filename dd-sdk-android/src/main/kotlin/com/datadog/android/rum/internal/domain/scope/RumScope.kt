/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface RumScope {

    /**
     * Handles an incoming event.
     * If needed, writes a RumEvent to the provided writer.
     * @return this instance if this scope is still valid, or null if it no longer can process
     * events
     */
    @WorkerThread
    fun handleEvent(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ): RumScope?

    /**
     * @return whether the current scope is active. Note that an inactive scope can still process
     * events (to handle ongoing event started while it was active).
     */
    fun isActive(): Boolean

    /**
     * @return the context related to this scope
     */
    fun getRumContext(): RumContext
}
