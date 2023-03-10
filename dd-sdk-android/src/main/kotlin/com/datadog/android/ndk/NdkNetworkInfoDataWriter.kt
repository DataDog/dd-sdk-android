/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import com.datadog.android.core.internal.net.info.NetworkInfoSerializer
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.core.internal.persistence.file.advanced.ConsentAwareFileMigrator
import com.datadog.android.core.internal.persistence.file.advanced.ConsentAwareFileOrchestrator
import com.datadog.android.core.internal.persistence.file.single.SingleFileOrchestrator
import com.datadog.android.core.internal.persistence.file.single.SingleItemDataWriter
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.NetworkInfo
import java.io.File
import java.util.concurrent.ExecutorService

internal class NdkNetworkInfoDataWriter(
    storageDir: File,
    consentProvider: ConsentProvider,
    executorService: ExecutorService,
    fileWriter: FileWriter,
    fileMover: FileMover,
    internalLogger: InternalLogger,
    filePersistenceConfig: FilePersistenceConfig
) : SingleItemDataWriter<NetworkInfo>(
    ConsentAwareFileOrchestrator(
        consentProvider = consentProvider,
        pendingOrchestrator = SingleFileOrchestrator(
            DatadogNdkCrashHandler.getPendingNetworkInfoFile(storageDir)
        ),
        grantedOrchestrator = SingleFileOrchestrator(
            DatadogNdkCrashHandler.getGrantedNetworkInfoFile(storageDir)
        ),
        dataMigrator = ConsentAwareFileMigrator(
            fileMover,
            executorService,
            internalLogger
        )
    ),
    NetworkInfoSerializer(),
    fileWriter,
    internalLogger,
    filePersistenceConfig
)
