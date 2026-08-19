/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

internal class EmbeddedContentSlotRegistration(
    val slotId: String
) {
    private val active = AtomicBoolean(true)

    fun deactivate() {
        active.set(false)
    }

    fun isActive(): Boolean = active.get()
}

internal class EmbeddedContentSlotRegistry {
    private val registrations = mutableListOf<WeakReference<EmbeddedContentSlotRegistration>>()

    /**
     * The placeholder wireframe standing in for each slot, keyed by slot ID: which view it was
     * written in, and the timestamp it carries. This is what tells [EmbeddedContentReceiver] whether
     * a slot's records have something to composite into yet.
     */
    private val placeholders = mutableMapOf<String, Placeholder>()
    private val placeholderListeners = mutableListOf<(String) -> Unit>()

    internal data class Placeholder(val viewId: String, val timestamp: Long)

    @AnyThread
    fun placeholder(slotId: String): Placeholder? = synchronized(placeholders) {
        placeholders[slotId]
    }

    /**
     * Records the placeholders a snapshot written for [viewId] at [timestamp] carries, as [slotIds].
     *
     * [slotIds] is the complete set drawn at that moment, not an addition to it, so a slot absent
     * from it has no placeholder any more and its entry is dropped. A repeat in the same view keeps
     * the original timestamp — the first placeholder in a view is the one records have to follow —
     * and listeners fire only for a slot's first placeholder in a view, the moment anything held for
     * it becomes writable.
     */
    @AnyThread
    fun onPlaceholdersWritten(viewId: String, timestamp: Long, slotIds: Set<String>) {
        val newlyPlaced = synchronized(placeholders) {
            placeholders.keys.retainAll(slotIds)
            slotIds.filter { slotId ->
                val known = placeholders[slotId]
                if (known?.viewId == viewId) {
                    false
                } else {
                    placeholders[slotId] = Placeholder(viewId, timestamp)
                    true
                }
            }
        }
        if (newlyPlaced.isEmpty()) {
            return
        }
        val listeners = synchronized(placeholders) {
            placeholderListeners.toList()
        }
        newlyPlaced.forEach { slotId -> listeners.forEach { it(slotId) } }
    }

    @AnyThread
    fun addPlaceholderListener(listener: (String) -> Unit) {
        synchronized(placeholders) {
            placeholderListeners.add(listener)
        }
    }

    @AnyThread
    fun hasMarkedSlots(): Boolean = activeSlotIds().isNotEmpty()

    @AnyThread
    fun isSlotMarked(slotId: String): Boolean = slotId in activeSlotIds()

    @AnyThread
    fun activeSlotIds(): Set<String> {
        return synchronized(registrations) {
            removeInactiveRegistrations()
            // The transform only reads a weak reference and immutable slot ID.
            @Suppress("UnsafeThirdPartyFunctionCall")
            registrations.mapNotNullTo(mutableSetOf()) { it.get()?.slotId }
        }
    }

    @UiThread
    fun notifySlotChanged(
        previousRegistration: EmbeddedContentSlotRegistration?,
        newRegistration: EmbeddedContentSlotRegistration?
    ) {
        previousRegistration?.deactivate()
        synchronized(registrations) {
            registrations.removeAll {
                val registration = it.get()
                registration == null ||
                    !registration.isActive() ||
                    registration === previousRegistration
            }
            trackRegistration(newRegistration)
        }
    }

    @UiThread
    fun track(registration: EmbeddedContentSlotRegistration) {
        synchronized(registrations) {
            removeInactiveRegistrations()
            trackRegistration(registration)
        }
    }

    private fun trackRegistration(registration: EmbeddedContentSlotRegistration?) {
        val isAlreadyTracked = registrations.any { it.get() === registration }
        if (registration != null && registration.isActive() && !isAlreadyTracked) {
            @Suppress("UnsafeThirdPartyFunctionCall") // WeakReference construction has no documented exception.
            val weakRegistration = WeakReference(registration)
            registrations += weakRegistration
        }
    }

    private fun removeInactiveRegistrations() {
        registrations.removeAll {
            val registration = it.get()
            registration == null || !registration.isActive()
        }
    }
}
