/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.log.Logger
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.concurrent.ExecutorService

internal class SessionReplayRecordPersistenceStrategy(
    contextProvider: ContextProvider,
    executorService: ExecutorService,
    internalLogger: Logger,
    storage: Storage
) : BatchFilePersistenceStrategy<String>(
    contextProvider,
    executorService,
    SessionReplayRecordSerializer(),
    internalLogger,
    storage
)
