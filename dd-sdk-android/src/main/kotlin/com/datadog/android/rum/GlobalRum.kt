/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.rum.GlobalRum.get
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.monitor.NoOpRumMonitor
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A global [RumMonitor] holder, ensuring that all RUM events are registered
 * on the same instance .
 *
 * The [registerIfAbsent] method should only be called once during the application
 * initialization phase. Any following calls will be no-op. If the [registerIfAbsent]
 * method is never called, a default no-op implementation is used.
 *
 * You can then retrieve the active [RumMonitor] using the [get] method.
 */
object GlobalRum {

    internal val isRegistered = AtomicBoolean(false)
    internal var monitor: RumMonitor = NoOpRumMonitor()

    private var activeContext: AtomicReference<RumContext> = AtomicReference(RumContext())

    /**
     * Identify whether a [RumMonitor] has previously been registered.
     *
     * This check is useful in scenarios where more than one component may be responsible
     * for registering a monitor.
     *
     * @return whether a monitor has been registered
     * @see [registerIfAbsent]
     */
    @JvmStatic
    fun isRegistered(): Boolean {
        return isRegistered.get()
    }

    /**
     * Register a [RumMonitor] to back the behaviour of the [get].
     *
     * Registration is a one-time operation. Once a monitor has been registered, all attempts at re-registering
     * will return `false`.
     *
     * Every application intending to use the global monitor is responsible for registering it once
     * during its initialization.
     *
     * @param monitor the monitor to use as global monitor.
     * @return `true` if the provided monitor was registered as a result of this call, `false` otherwise.
     */
    @JvmStatic
    fun registerIfAbsent(monitor: RumMonitor): Boolean {
        return registerIfAbsent(Callable { monitor })
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
     * @param provider Provider for the monitor to use as global monitor.
     * @return `true` if the provided monitor was registered as a result of this call, `false` otherwise.
     */
    @JvmStatic
    fun registerIfAbsent(provider: Callable<RumMonitor>): Boolean {
        if (isRegistered.get()) {
            devLogger.w("RumMonitor has already been registered")
            return false
        } else {
            if (isRegistered.compareAndSet(false, true)) {
                monitor = provider.call()
                return true
            } else {
                return false
            }
        }
    }

    /**
     * Returns the constant [RumMonitor] instance.
     *
     * Until a monitor is explicitly configured with [registerIfAbsent],
     * a no-op implementation is returned.
     *
     * @return The global monitor instance.
     * @see [registerIfAbsent]
     */
    @JvmStatic
    fun get(): RumMonitor {
        return monitor
    }

    // region Internal

    internal fun updateApplicationId(applicationId: UUID) {
        updateRumContext { context ->
            context.copy(applicationId = applicationId)
        }
    }

    internal fun updateViewId(viewId: UUID?) {
        updateRumContext { context ->
            context.copy(viewId = viewId)
        }
    }

    // Used only for internal tests
    internal fun updateContext(rumContext: RumContext) {
        updateRumContext {
            rumContext.copy()
        }
    }

    internal fun getRumContext(): RumContext {
        return activeContext.get()
    }

    private fun updateRumContext(update: (RumContext) -> RumContext) {
        var oldContext: RumContext
        var newContext: RumContext
        do {
            oldContext = activeContext.get()
            newContext = update(oldContext)
        } while (!activeContext.compareAndSet(oldContext, newContext))
    }

    // endregion
}
