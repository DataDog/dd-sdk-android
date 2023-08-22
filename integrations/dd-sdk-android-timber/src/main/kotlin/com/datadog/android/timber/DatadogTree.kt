/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.timber

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.log.Logger
import timber.log.Timber

/**
 * An implementation of a [Timber.Tree], forwarding all logs to the provided [Logger].
 *
 * @param logger the logger to use with Timber.
 */
class DatadogTree(
    private val logger: Logger
) : Timber.Tree() {

    /**
     * Creates a [Timber.Tree] with a default [Logger] having a minimum log priority
     * for Datadog logs set to specified value.
     *
     * See [Logger.Builder.setRemoteLogThreshold] for details.
     *
     * @param minLogPriority Minimum log threshold (priority) to be sent to the Datadog servers.
     * @param sdkCore SDK instance to bind to. If not provided, default instance will be used.
     */
    @Suppress("unused")
    @JvmOverloads
    constructor(minLogPriority: Int, sdkCore: SdkCore = Datadog.getInstance()) :
        this(
            Logger.Builder(sdkCore)
                .setRemoteLogThreshold(minLogPriority)
                .build()
        )

    init {
        logger.addTag("android:timber")
    }

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        logger.log(priority, message, t)
    }
}
