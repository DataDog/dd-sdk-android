/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.core.internal.persistence.file.advanced.ConsentAwareFileMigrator
import com.datadog.android.core.internal.persistence.file.advanced.ConsentAwareFileOrchestrator
import com.datadog.android.core.internal.persistence.file.single.SingleFileOrchestrator
import com.datadog.android.core.internal.persistence.file.single.SingleItemDataWriter
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.user.UserInfoSerializer
import java.io.File
import java.util.concurrent.ExecutorService

internal class NdkUserInfoDataWriter(
    storageDir: File,
    consentProvider: ConsentProvider,
    executorService: ExecutorService,
    fileWriter: FileWriter<ByteArray>,
    fileMover: FileMover,
    internalLogger: InternalLogger,
    filePersistenceConfig: FilePersistenceConfig
) : SingleItemDataWriter<UserInfo>(
    ConsentAwareFileOrchestrator(
        consentProvider = consentProvider,
        pendingOrchestrator = SingleFileOrchestrator(
            DatadogNdkCrashHandler.getPendingUserInfoFile(storageDir),
            internalLogger
        ),
        grantedOrchestrator = SingleFileOrchestrator(
            DatadogNdkCrashHandler.getGrantedUserInfoFile(storageDir),
            internalLogger
        ),
        dataMigrator = ConsentAwareFileMigrator(
            fileMover,
            internalLogger
        ),
        executorService,
        internalLogger
    ),
    UserInfoSerializer(),
    fileWriter,
    internalLogger,
    filePersistenceConfig
)
