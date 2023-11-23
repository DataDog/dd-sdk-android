/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.api.InternalLogger

@Suppress("UnsafeThirdPartyFunctionCall")
internal class StubInternalLogger : InternalLogger {
    override fun log(
        level: InternalLogger.Level,
        target: InternalLogger.Target,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        println("${level.name.first()} [${target.name.first()}]: ${messageBuilder()}")
        additionalProperties?.log()
        throwable?.printStackTrace()
    }

    override fun log(
        level: InternalLogger.Level,
        targets: List<InternalLogger.Target>,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        println("${level.name.first()} [${targets.joinToString { it.name.first().toString() }}]: ${messageBuilder()}")
        additionalProperties?.log()
        throwable?.printStackTrace()
    }

    override fun logMetric(
        messageBuilder: () -> String,
        additionalProperties: Map<String, Any?>
    ) {
        println("M [T]: ${messageBuilder()}")
        additionalProperties.log()
    }

    private fun <K, T> Map<K, T>.log() {
        forEach {
            println("    ${it.key}: ${it.value}")
        }
    }
}
