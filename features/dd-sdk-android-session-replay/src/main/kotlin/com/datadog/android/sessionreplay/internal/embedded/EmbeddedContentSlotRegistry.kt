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

    @AnyThread
    fun hasMarkedSlots(): Boolean {
        return synchronized(registrations) {
            removeInactiveRegistrations()
            registrations.isNotEmpty()
        }
    }

    @AnyThread
    fun isSlotMarked(slotId: String): Boolean {
        return synchronized(registrations) {
            removeInactiveRegistrations()
            registrations.any { it.get()?.slotId == slotId }
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
