/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.metrics

internal sealed class RemovalReason {

    internal fun includeInMetrics(): Boolean {
        return this !is Flushed
    }
    internal class IntakeCode(private val responseCode: Int) : RemovalReason() {
        override fun toString(): String {
            return "intake-code-$responseCode"
        }
    }

    internal object Invalid : RemovalReason() {
        override fun toString(): String {
            return "invalid"
        }
    }

    internal object Purged : RemovalReason() {
        override fun toString(): String {
            return "purged"
        }
    }

    internal object Obsolete : RemovalReason() {
        override fun toString(): String {
            return "obsolete"
        }
    }

    internal object Flushed : RemovalReason() {
        override fun toString(): String {
            return "flushed"
        }
    }
}
