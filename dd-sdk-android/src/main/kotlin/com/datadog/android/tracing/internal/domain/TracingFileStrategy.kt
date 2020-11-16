/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain

import android.content.Context
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.core.internal.domain.FilePersistenceStrategy
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfoProvider
import datadog.opentracing.DDSpan
import java.io.File
import java.util.concurrent.ExecutorService

internal class TracingFileStrategy(
    context: Context,
    filePersistenceConfig: FilePersistenceConfig =
        FilePersistenceConfig(recentDelayMs = MAX_DELAY_BETWEEN_SPANS_MS),
    timeProvider: TimeProvider,
    networkInfoProvider: NetworkInfoProvider,
    userInfoProvider: UserInfoProvider,
    envName: String = "",
    dataPersistenceExecutorService: ExecutorService,
    trackingConsentProvider: ConsentProvider
) : FilePersistenceStrategy<DDSpan>(
    File(context.filesDir, INTERMEDIATE_DATA_FOLDER),
    File(context.filesDir, AUTHORIZED_FOLDER),
    SpanSerializer(timeProvider, networkInfoProvider, userInfoProvider, envName),
    dataPersistenceExecutorService,
    filePersistenceConfig,
    PayloadDecoration.NEW_LINE_DECORATION,
    trackingConsentProvider
) {
    companion object {
        internal const val VERSION = 1
        internal const val ROOT = "dd-tracing"
        internal const val INTERMEDIATE_DATA_FOLDER =
            "$ROOT-pending-v$VERSION"
        internal const val AUTHORIZED_FOLDER = "$ROOT-v$VERSION"
        internal const val MAX_DELAY_BETWEEN_SPANS_MS = 5000L
    }
}
