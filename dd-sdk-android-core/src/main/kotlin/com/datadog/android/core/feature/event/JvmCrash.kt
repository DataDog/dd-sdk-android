/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.feature.event

/**
 * JVM crash information to be propagated on the message bus to the features doing crash reporting.
 * Is not a part of official API.
 */
@Suppress("UndocumentedPublicClass", "UndocumentedPublicProperty")
sealed class JvmCrash {
    abstract val throwable: Throwable
    abstract val message: String
    abstract val threads: List<ThreadDump>

    data class Logs(
        val threadName: String,
        override val throwable: Throwable,
        val timestamp: Long,
        override val message: String,
        val loggerName: String,
        override val threads: List<ThreadDump>
    ) : JvmCrash()

    data class Rum(
        override val throwable: Throwable,
        override val message: String,
        override val threads: List<ThreadDump>
    ) : JvmCrash()
}
