/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.core.SdkReference
import java.util.WeakHashMap

/**
 * A Registry for all [SdkCore] instances, allowing customers to retrieve the one
 * they want from anywhere in the code.
 */
internal class SdkCoreRegistry(
    private val internalLogger: InternalLogger
) {

    private val instances = mutableMapOf<String, SdkCore>()

    // Clients own their references; the registry must not keep discarded clients alive.
    private val references: MutableMap<SdkReference, String> = WeakHashMap()

    // region SdkCoreRegistry

    /**
     * Register an instance of [SdkCore] with the given name.
     * Callers holding the registry lock must use [registerAndGetNotifications] instead and
     * invoke [notifyReferences] after releasing the lock.
     * @param name the name for the given instance
     * @param sdkCore the [SdkCore] instance
     */
    fun register(name: String?, sdkCore: SdkCore) {
        notifyReferences(registerAndGetNotifications(name, sdkCore))
    }

    /** Allows initialization to finish its registry transaction before invoking capture callbacks. */
    @Synchronized
    fun registerAndGetNotifications(name: String?, sdkCore: SdkCore): List<() -> Unit> {
        val key = name ?: DEFAULT_INSTANCE_NAME
        if (instances.containsKey(key)) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "An SdkCode with name $key has already been registered." }
            )
            return emptyList()
        }
        instances[key] = sdkCore
        return referencesFor(key).mapNotNull { it.capture(sdkCore) }
    }

    fun notifyReferences(notifications: List<() -> Unit>) {
        notifications.forEach { notifyReference(it) }
    }

    /**
     * Unregisters the instance for the given name.
     * @param name the name of the instance to unregister
     * @return the [SdkCore] instance if it exists, or null
     */
    @Synchronized
    fun unregister(name: String? = null): SdkCore? {
        val key = name ?: DEFAULT_INSTANCE_NAME
        referencesFor(key).forEach { it.release() }
        return instances.remove(key)
    }

    /**
     * Returns the instance for the given name.
     * @param name the name of the instance to get
     * @return the [SdkCore] instance if it exists, or null
     */
    @Synchronized
    fun getInstance(name: String? = null): SdkCore? {
        val key = name ?: DEFAULT_INSTANCE_NAME
        return instances[key]
    }

    /**
     * Clears all registered instances.
     */
    @Synchronized
    fun clear() {
        references.keys.toList().forEach { it.release() }
        references.clear()
        instances.clear()
    }

    fun registerReference(name: String?, reference: SdkReference) {
        val notification = synchronized(this) {
            val key = name ?: DEFAULT_INSTANCE_NAME
            references[reference] = key
            instances[key]?.let { reference.capture(it) }
        }
        notification?.let { notifyReference(it) }
    }

    private fun referencesFor(name: String): List<SdkReference> =
        references.filterValues { it == name }.keys.toList()

    private fun notifyReference(notification: () -> Unit) {
        try {
            notification()
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            // SdkReference accepts customer-provided callbacks.
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "SDK instance capture callback failed." },
                e
            )
        }
    }

    // endregion

    companion object {
        const val DEFAULT_INSTANCE_NAME = "_dd.sdk_core.default"
    }
}
