/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import com.datadog.android.core.persistence.datastore.DataStoreHandler
import java.io.File
import java.util.Locale

internal class DataStoreFileHelper {
    internal fun getDataStoreFile(
        featureName: String,
        storageDir: File,
        internalLogger: InternalLogger,
        key: String
    ): File {
        val dataStoreDirectory = createDataStoreDirectoryIfNecessary(
            featureName = featureName,
            storageDir = storageDir,
            internalLogger = internalLogger
        )

        return File(dataStoreDirectory, key)
    }

    private fun createDataStoreDirectoryIfNecessary(
        featureName: String,
        storageDir: File,
        internalLogger: InternalLogger
    ): File {
        val folderName = DATASTORE_FOLDER_NAME.format(
            Locale.US,
            DataStoreHandler.CURRENT_DATASTORE_VERSION
        )

        val dataStoreDirectory = File(
            storageDir,
            "$featureName/$folderName"
        )

        if (!dataStoreDirectory.existsSafe(internalLogger)) {
            dataStoreDirectory.mkdirsSafe(internalLogger)
        }

        return dataStoreDirectory
    }

    internal companion object {
        internal const val DATASTORE_FOLDER_NAME = "datastore_v%s"
    }
}
