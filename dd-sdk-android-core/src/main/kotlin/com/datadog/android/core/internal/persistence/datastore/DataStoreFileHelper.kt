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

internal class DataStoreFileHelper(
    private val internalLogger: InternalLogger
) {
    internal fun getDataStoreFile(
        featureName: String,
        storageDir: File,
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

    internal fun isKeyInvalid(key: String): Boolean {
        return key.contains("/")
    }

    internal fun logInvalidKeyException() {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { INVALID_DATASTORE_KEY_FORMAT_EXCEPTION }
        )
    }

    internal companion object {
        internal const val DATASTORE_FOLDER_NAME = "datastore_v%s"
        internal const val INVALID_DATASTORE_KEY_FORMAT_EXCEPTION =
            "Datastore key must not be a path!"
    }
}
