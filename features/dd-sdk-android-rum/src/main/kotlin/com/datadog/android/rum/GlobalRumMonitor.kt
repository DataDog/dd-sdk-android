/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRumMonitor.get
import com.datadog.android.rum.GlobalRumMonitor.registerIfAbsent
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import java.util.concurrent.Callable

/**
 * A global [RumMonitor] holder, ensuring that all RUM events are registered
 * on the same instance.
 *
 * The [registerIfAbsent] method should only be called once during the application
 * initialization phase. Any following calls will be no-op. If the [registerIfAbsent]
 * method is never called, a default no-op implementation is used.
 *
 * You can then retrieve the active [RumMonitor] using the [get] method.
 */
object GlobalRumMonitor {

    private val registeredMonitors: MutableMap<SdkCore, RumMonitor> = mutableMapOf()

    /**
     * Identify whether a [RumMonitor] has previously been registered for the given SDK instance.
     *
     * This check is useful in scenarios where more than one component may be responsible
     * for registering a monitor.
     *
     * @param sdkCore the [SdkCore] instance to check against. If not provided, default instance
     * will be checked.
     * @return whether a monitor has been registered
     * @see [registerIfAbsent]
     */
    @JvmOverloads
    @JvmStatic
    fun isRegistered(sdkCore: SdkCore = Datadog.getInstance()): Boolean {
        return synchronized(registeredMonitors) {
            registeredMonitors.containsKey(sdkCore)
        }
    }

    /**
     * Register a [RumMonitor] with an [SdkCore] to back the behaviour of the [get] function.
     *
     * Registration is a one-time operation. Once a monitor has been registered, all attempts at re-registering
     * will return `false`.
     *
     * Every application intending to use the global monitor is responsible for registering it once
     * during its initialization.
     *
     * @param monitor the monitor to use as global monitor.
     * @param sdkCore the instance to register the given monitor with. If not provided, default
     * instance will be used.
     * @return `true` if the provided monitor was registered as a result of this call, `false` otherwise.
     */
    @JvmOverloads
    @JvmStatic
    fun registerIfAbsent(monitor: RumMonitor, sdkCore: SdkCore = Datadog.getInstance()): Boolean {
        return registerIfAbsent(sdkCore) { monitor }
    }

    /**
     * Register a [RumMonitor] to back the behaviour of the [get].
     *
     * The monitor is provided through a [Callable] that will only be called if the global monitor is absent.
     * Registration is a one-time operation. Once a monitor has been registered, all attempts at re-registering
     * will return `false`.
     *
     * Every application intending to use the global monitor is responsible for registering it once
     * during its initialization.
     *
     * @param sdkCore the instance to register the given monitor with. If not provided, default
     * instance will be used.
     * @param provider Provider for the monitor to use as global monitor.
     * @return `true` if the provided monitor was registered as a result of this call, `false` otherwise.
     */
    @JvmOverloads
    @JvmStatic
    fun registerIfAbsent(
        sdkCore: SdkCore = Datadog.getInstance(),
        provider: Callable<RumMonitor>
    ): Boolean {
        return synchronized(registeredMonitors) {
            if (registeredMonitors.containsKey(sdkCore)) {
                (sdkCore as FeatureSdkCore).internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    "A RumMonitor has already been registered for this SDK instance"
                )
                false
            } else {
                @Suppress("UnsafeThirdPartyFunctionCall") // User provided callable, let it throw
                val monitor = provider.call()
                registeredMonitors[sdkCore] = monitor
                true
            }
        }
    }

    /**
     * Returns the constant [RumMonitor] instance.
     *
     * Until a monitor is explicitly configured with [registerIfAbsent],
     * a no-op implementation is returned.
     *
     * @return The monitor associated with the instance given instance, or a no-op monitor. If SDK
     * instance is not provided, default instance will be used.
     * @see [registerIfAbsent]
     */
    @JvmOverloads
    @JvmStatic
    fun get(sdkCore: SdkCore = Datadog.getInstance()): RumMonitor {
        return synchronized(registeredMonitors) {
            registeredMonitors[sdkCore] ?: NoOpRumMonitor()
        }
    }

    // region Internal

    internal fun clear() {
        synchronized(registeredMonitors) {
            registeredMonitors.clear()
        }
    }

    // This method is mainly for test purposes.
    @Suppress("unused")
    @JvmStatic
    private fun reset() {
        clear()
    }

    // endregion
}
