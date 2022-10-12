/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.domain.event.WebViewLogEventSerializer
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.Storage
import com.google.gson.JsonObject
import java.util.concurrent.ExecutorService

internal class WebViewLogFilePersistenceStrategy(
    contextProvider: ContextProvider,
    executorService: ExecutorService,
    storage: Storage
) : BatchFilePersistenceStrategy<JsonObject>(
    contextProvider,
    executorService,
    WebViewLogEventSerializer(),
    sdkLogger,
    storage
)
