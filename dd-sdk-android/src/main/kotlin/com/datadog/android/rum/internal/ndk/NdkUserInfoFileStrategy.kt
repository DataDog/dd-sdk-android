/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.domain.FilePersistenceStrategy
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.internal.user.UserInfoSerializer
import java.io.File
import java.util.concurrent.ExecutorService

internal class NdkUserInfoFileStrategy(
    ndkTempDir: File,
    ndkDir: File,
    dataPersistenceExecutorService: ExecutorService,
    trackingConsentProvider: ConsentProvider
) : FilePersistenceStrategy<UserInfo>(
    ndkTempDir,
    ndkDir,
    UserInfoSerializer(),
    dataPersistenceExecutorService,
    trackingConsentProvider = trackingConsentProvider,
    fileWriterFactory = { fileOrchestrator, eventSerializer, _ ->
        NdkFileWriter(fileOrchestrator, eventSerializer)
    },
    intermediateFileOrchestrator = NdkFileOrchestrator(
        File(
            ndkTempDir,
            DatadogNdkCrashHandler.LAST_USER_INFORMATION_FILE_NAME
        )
    ),
    authorizedFileOrchestrator = NdkFileOrchestrator(
        File(
            ndkDir,
            DatadogNdkCrashHandler.LAST_USER_INFORMATION_FILE_NAME
        )
    )
)
