/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import java.io.File

internal class DataStoreFileHelper {
    internal fun getDataStoreDirectory(
        sdkInstanceId: String,
        storageDir: File,
        featureName: String,
        folderName: String
    ): File = File(
        storageDir,
        "$sdkInstanceId/$featureName/$folderName"
    )

    internal fun getDataStoreFile(
        dataStoreDirectory: File,
        dataStoreFileName: String
    ) = File(dataStoreDirectory, dataStoreFileName)
}
