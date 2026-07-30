/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import java.lang.ref.WeakReference

internal class EmbeddedContentSlotRegistration(
    val slotId: String
)

internal object EmbeddedContentSlotRegistry {
    private val registrations = mutableListOf<WeakReference<EmbeddedContentSlotRegistration>>()

    @AnyThread
    fun hasMarkedSlots(): Boolean {
        return synchronized(registrations) {
            removeGarbageCollectedRegistrations()
            registrations.isNotEmpty()
        }
    }

    @AnyThread
    fun isSlotMarked(slotId: String): Boolean {
        return synchronized(registrations) {
            removeGarbageCollectedRegistrations()
            registrations.any { it.get()?.slotId == slotId }
        }
    }

    @UiThread
    fun notifySlotChanged(
        previousRegistration: EmbeddedContentSlotRegistration?,
        newRegistration: EmbeddedContentSlotRegistration?
    ) {
        synchronized(registrations) {
            registrations.removeAll {
                val registration = it.get()
                registration == null || registration === previousRegistration
            }
            if (newRegistration != null) {
                @Suppress("UnsafeThirdPartyFunctionCall") // WeakReference construction has no documented exception.
                val weakRegistration = WeakReference(newRegistration)
                registrations += weakRegistration
            }
        }
    }

    private fun removeGarbageCollectedRegistrations() {
        registrations.removeAll { it.get() == null }
    }
}
