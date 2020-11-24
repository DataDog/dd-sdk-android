/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.assertj

import com.datadog.android.core.internal.data.file.FileOrchestrator
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.core.internal.domain.FilePersistenceStrategy
import com.datadog.android.core.internal.domain.batching.DefaultConsentAwareDataWriter
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class PersistenceStrategyAssert<T : Any>(actual: FilePersistenceStrategy<T>) :
    AbstractObjectAssert<PersistenceStrategyAssert<T>, FilePersistenceStrategy<T>>(
        actual,
        PersistenceStrategyAssert::class.java
    ) {

    fun hasIntermediateStorageFolder(folderPath: String): PersistenceStrategyAssert<T> {
        val absolutePath = actual.intermediateFileOrchestrator.rootDirectory.absolutePath
        assertThat(absolutePath)
            .overridingErrorMessage(
                "Expected strategy to have intermediate folder " +
                    "$folderPath but was $absolutePath"
            )
            .isEqualTo(folderPath)
        return this
    }

    fun hasAuthorizedStorageFolder(folderPath: String): PersistenceStrategyAssert<T> {
        val absolutePath = actual.authorizedFileOrchestrator.rootDirectory.absolutePath
        assertThat(absolutePath)
            .overridingErrorMessage(
                "Expected strategy to have authorized folder " +
                    "$folderPath but was $absolutePath"
            )
            .isEqualTo(folderPath)
        return this
    }

    fun usesConsentAwareAsyncWriter(): PersistenceStrategyAssert<T> {
        assertThat(actual.getWriter())
            .isInstanceOf(DefaultConsentAwareDataWriter::class.java)
        return this
    }

    fun usesImmediateWriter(): PersistenceStrategyAssert<T> {
        assertThat(actual.getWriter())
            .isInstanceOf(ImmediateFileWriter::class.java)
        return this
    }

    fun hasConfig(config: FilePersistenceConfig): PersistenceStrategyAssert<T> {
        assertThat(actual.intermediateFileOrchestrator.filePersistenceConfig)
            .isEqualToComparingFieldByField(config)
        assertThat(actual.authorizedFileOrchestrator.filePersistenceConfig)
            .isEqualToComparingFieldByField(config)
        return this
    }

    fun uploadsFrom(folderPath: String): PersistenceStrategyAssert<T> {
        val absolutePath =
            (actual.fileReader.fileOrchestrator as FileOrchestrator).rootDirectory.absolutePath
        assertThat(absolutePath)
            .overridingErrorMessage(
                "Expected strategy to upload from " +
                    "$folderPath but was uploading from $absolutePath"
            )
            .isEqualTo(folderPath)
        return this
    }

    companion object {
        internal fun <T : Any> assertThat(actual: FilePersistenceStrategy<T>):
            PersistenceStrategyAssert<T> =
                PersistenceStrategyAssert(actual)
    }
}
