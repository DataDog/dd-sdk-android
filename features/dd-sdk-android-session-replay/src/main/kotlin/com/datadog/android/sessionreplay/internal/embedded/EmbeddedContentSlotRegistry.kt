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

@Suppress("TooManyFunctions")
internal class EmbeddedContentSlotRegistry {
    private val registrations = mutableListOf<WeakReference<EmbeddedContentSlotRegistration>>()

    /** The placeholder wireframe standing in for each slot, keyed by slot ID. */
    private val placeholders = mutableMapOf<String, Placeholder>()
    private val placeholderListeners = mutableListOf<(String) -> Unit>()
    private val snapshotListeners = mutableListOf<(Set<String>) -> Unit>()

    internal data class Placeholder(val viewId: String, val timestamp: Long)

    @AnyThread
    fun placeholder(slotId: String): Placeholder? = synchronized(placeholders) {
        placeholders[slotId]
    }

    /**
     * Records the placeholders a snapshot written for [viewId] at [timestamp] carries, as [slotIds].
     *
     * [slotIds] is the complete set drawn at that moment, not an addition to it, so a slot absent
     * from it has no placeholder any more. A repeat in the same view keeps the original timestamp —
     * the first placeholder in a view is the one records have to follow — so listeners fire only for
     * a slot's first placeholder in a view.
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
        val (placeholderNotified, snapshotNotified) = synchronized(placeholders) {
            placeholderListeners.toList() to snapshotListeners.toList()
        }
        newlyPlaced.forEach { slotId -> placeholderNotified.forEach { it(slotId) } }
        // Fired for every snapshot, including one that placed nothing new: a listener waiting on a
        // slot learns from the snapshots that leave it out, not from the ones that include it.
        snapshotNotified.forEach { it(slotIds) }
    }

    @AnyThread
    fun addPlaceholderListener(listener: (String) -> Unit) {
        synchronized(placeholders) {
            placeholderListeners.add(listener)
        }
    }

    /**
     * Registers [listener] to receive the complete slot set of every snapshot that reports
     * placeholders, whether or not any of them are new.
     */
    @AnyThread
    fun addSnapshotListener(listener: (Set<String>) -> Unit) {
        synchronized(placeholders) {
            snapshotListeners.add(listener)
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
            registrations += WeakReference(registration)
        }
    }

    private fun removeInactiveRegistrations() {
        registrations.removeAll {
            val registration = it.get()
            registration == null || !registration.isActive()
        }
    }
}
