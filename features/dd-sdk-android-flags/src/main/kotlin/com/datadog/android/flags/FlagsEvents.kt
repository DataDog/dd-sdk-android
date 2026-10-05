/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.internal.FlagsEventSource

/**
 * Event registrations for an SDK-created [FlagsClient].
 * Facades share the client's retained event and do not own its lifecycle.
 *
 * @param client a client returned by [FlagsClient.Builder.build] or [FlagsClient.get].
 * @throws IllegalArgumentException if [client] is a custom implementation without SDK event support.
 */
class FlagsEvents(client: FlagsClient) {
    private val source = client as? FlagsEventSource
        ?: throw IllegalArgumentException("FlagsEvents requires a client created by the Datadog SDK.")

    /**
     * Invokes [listener] once with the keys from the first installed cached or downloaded flags,
     * including an empty configuration. Missing or invalid cache does not trigger this callback.
     * Each registration receives the retained first result, even after subsequent flag updates.
     * If the first result is available, delivery is immediate on the calling thread; otherwise it runs
     * on a dedicated background thread. Callback exceptions are logged and isolated.
     * Pending callbacks are retained until delivery or cancellation. Unsubscribe the returned subscription
     * when the owner is destroyed to release captured references if flags never become available.
     * Unsubscription is thread-safe and idempotent. A callback already claimed
     * for delivery may still run; cancellation does not interrupt or wait for it.
     * Immediate replay may occur before this method returns; cancellation cannot undo delivery.
     * This notification does not imply readiness. Dispatch UI work to the appropriate thread.
     */
    fun onFirstFlags(listener: FlagsClientEventListener): FlagsSubscription = source.onFirstFlags(listener)
}
