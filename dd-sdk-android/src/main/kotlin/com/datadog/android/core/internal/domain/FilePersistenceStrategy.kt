/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.FileOrchestrator
import com.datadog.android.core.internal.data.file.FileReader
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import java.io.File

internal open class FilePersistenceStrategy<T : Any>(
    dataDirectory: File,
    serializer: Serializer<T>,
    filePersistenceConfig: FilePersistenceConfig = FilePersistenceConfig(),
    payloadDecoration: PayloadDecoration = PayloadDecoration.JSON_ARRAY_DECORATION
) : PersistenceStrategy<T> {

    private val fileOrchestrator = FileOrchestrator(
        dataDirectory,
        filePersistenceConfig
    )

    private val fileReader = FileReader(
        fileOrchestrator,
        dataDirectory,
        payloadDecoration.prefix,
        payloadDecoration.suffix
    )

    protected val fileWriter = ImmediateFileWriter(
        fileOrchestrator,
        serializer,
        payloadDecoration.separator
    )

    // region PersistenceStrategy

    override fun getWriter(): Writer<T> {
        return fileWriter
    }

    override fun getReader(): Reader {
        return fileReader
    }

    override fun clearAllData() {
        fileReader.dropAllBatches()
    }

    // endregion
}
