/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

/**
 * Shared per Flags feature; stopping the feature invalidates its clients' proofs.
 * RUM is not part of the identity: each transcript still binds `dd-application-id` per response.
 */
internal class ResponseSigningContext {
    class Snapshot(val active: Boolean)
    val lock = Any()
    private var identity = Snapshot(true)

    fun snapshot(): Snapshot = synchronized(lock) { identity }

    fun start() = synchronized(lock) { identity = Snapshot(true) }

    fun stop() = synchronized(lock) { identity = Snapshot(false) }
}
