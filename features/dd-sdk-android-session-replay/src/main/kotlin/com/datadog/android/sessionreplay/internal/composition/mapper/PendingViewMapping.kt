/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback

/** Joins the existing mapper callback contract without blocking either the UI or construction worker. */
internal class PendingViewMapping(
    override val isTerminal: Boolean
) : CapturedViewData, AsyncJobStatusCallback {
    private val lock = Any()

    // The initial job keeps synchronous callbacks from publishing before map() returns its list.
    private var jobs = 1
    private var wireframes: List<MobileSegment.Wireframe> = emptyList()
    private val listeners = mutableListOf<() -> Unit>()

    override fun jobStarted() {
        synchronized(lock) { if (jobs > 0) jobs++ }
    }

    override fun jobFinished() {
        val ready = synchronized(lock) {
            if (jobs <= 0) return
            jobs--
            if (jobs == 0) listeners.toList().also { listeners.clear() } else emptyList()
        }
        ready.forEach { it() }
    }

    fun mapped(result: List<MobileSegment.Wireframe>) {
        // Asynchronous mappers can append until their final job finishes. Construction copies
        // these values only after that callback has published them through this lock.
        synchronized(lock) { wireframes = result }
        jobFinished()
    }

    fun whenReady(listener: () -> Unit) {
        val ready = synchronized(lock) {
            if (jobs == 0) true else { listeners += listener; false }
        }
        if (ready) listener()
    }

    fun failed() {
        val ready = synchronized(lock) {
            wireframes = emptyList()
            jobs = 0
            listeners.toList().also { listeners.clear() }
        }
        ready.forEach { it() }
    }

    /** Called by construction only after every mapper job has finished. No UI objects are retained here. */
    override fun map(mappingContext: CapturedMappingContext): CapturedViewMapperResult = synchronized(lock) {
        if (jobs != 0) return CapturedViewMapperResult.None
        CapturedViewMapperResult.Wireframes(wireframes.map { it.toCapturedWireframe(mappingContext) }, isTerminal)
    }
}
