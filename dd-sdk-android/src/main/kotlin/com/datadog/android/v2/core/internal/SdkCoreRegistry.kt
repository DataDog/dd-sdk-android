/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal

import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore

/**
 * A Registry for all [SdkCore] instances, allowing customers to retrieve the one
 * they want from anywhere in the code.
 */
internal class SdkCoreRegistry(
    private val internalLogger: InternalLogger
) {

    private val instances = mutableMapOf<String, SdkCore>()

    // region SdkCoreRegistry

    /**
     * Register an instance of [SdkCore] with the given name.
     * @param name the name for the given instance
     * @param sdkCore the [SdkCore] instance
     */
    fun register(name: String?, sdkCore: SdkCore) {
        val key = name ?: DEFAULT_INSTANCE_NAME
        if (instances.containsKey(key)) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                "An SdkCode with name $key has already been registered."
            )
        } else {
            instances[key] = sdkCore
        }
    }

    /**
     * Unregisters the instance for the given name.
     * @param name the name of the instance to unregister
     * @return the [SdkCore] instance if it exists, or null
     */
    fun unregister(name: String? = null): SdkCore? {
        val key = name ?: DEFAULT_INSTANCE_NAME
        return instances.remove(key)
    }

    /**
     * Returns the instance for the given name.
     * @param name the name of the instance to get
     * @return the [SdkCore] instance if it exists, or null
     */
    fun getInstance(name: String? = null): SdkCore? {
        val key = name ?: DEFAULT_INSTANCE_NAME
        return instances[key]
    }

    /**
     * Clears all registered instances.
     */
    fun clear() {
        instances.clear()
    }

    // endregion

    companion object {
        const val DEFAULT_INSTANCE_NAME = "_dd.sdk_core.default"
    }
}
