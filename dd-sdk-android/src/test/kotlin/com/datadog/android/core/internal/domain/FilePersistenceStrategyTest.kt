/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import android.content.Context
import android.os.Build
import com.datadog.android.Datadog
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.utils.asJsonArray
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.invokeMethod
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.concurrent.ExecutorService
import kotlin.math.min
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings()
internal abstract class FilePersistenceStrategyTest<T : Any>(
    val dataFolderName: String,
    val maxMessagesPerPath: Int = MAX_MESSAGES_PER_BATCH,
    val modelClass: Class<T>
) {

    lateinit var testedWriter: Writer<T>
    lateinit var testedReader: Reader

    @TempDir
    lateinit var tempDir: File

    lateinit var mockContext: Context

    @Mock
    lateinit var mockExecutorService: ExecutorService
    // region Setup

    @BeforeEach
    open fun `set up`(forge: Forge) {
        mockContext = mockContext()
        whenever(mockContext.filesDir) doReturn tempDir
        whenever(mockExecutorService.submit(any())) doAnswer {
            (it.arguments[0] as Runnable).run()
            null
        }
        Datadog.initialize(mockContext, forge.anHexadecimalString())
        val persistingStrategy = getStrategy()

        testedWriter = persistingStrategy.getWriter()
        testedReader = persistingStrategy.getReader()

        setUp(testedWriter, testedReader)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
    }

    abstract fun getStrategy(): PersistenceStrategy<T>

    abstract fun setUp(writer: Writer<T>, reader: Reader)

    abstract fun waitForNextBatch()

    // endregion

    // region Writer Tests

    @Test
    fun `writes full message as json`(forge: Forge) {
        val fakeModel = forge.getForgery(modelClass)
        testedWriter.write(fakeModel)
        waitForNextBatch()
        val batch = testedReader.readNextBatch()!!
        val model = batch.asJsonArray[0].asJsonObject
        assertMatches(model, fakeModel)
    }

    @Test
    fun `writes minimal model as json`(forge: Forge) {
        val fakeModel = forge.getForgery(modelClass)
        val minimalModel = minimalCopy(fakeModel)
        testedWriter.write(minimalModel)
        waitForNextBatch()
        val batch = testedReader.readNextBatch()!!
        val log = batch.asJsonArray[0].asJsonObject
        assertMatches(log, minimalModel)
    }

    @Test
    fun `writes batch of models`(forge: Forge) {
        val fakeModels = forge.aList { forge.getForgery(modelClass) }
        val sentModels = mutableListOf<T>()
        val logCount = min(maxMessagesPerPath, fakeModels.size)
        for (i in 0 until logCount) {
            val model = fakeModels[i]
            testedWriter.write(model)
            sentModels.add(model)
        }
        waitForNextBatch()
        val batch = testedReader.readNextBatch()!!

        val batchCount = min(maxMessagesPerPath, batch.asJsonArray.size())
        for (i in 0 until batchCount) {
            val jsonObject = batch.asJsonArray[i].asJsonObject
            assertMatches(jsonObject, sentModels[i])
        }
    }

    @Test
    fun `writes in new batch if delay passed`(forge: Forge) {
        val fakeModel = forge.getForgery(modelClass)
        val nextModel = forge.getForgery(modelClass)
        testedWriter.write(fakeModel)
        waitForNextBatch()

        testedWriter.write(nextModel)
        val batch = testedReader.readNextBatch()!!
        val jsonObject = batch.asJsonArray[0].asJsonObject
        assertMatches(jsonObject, fakeModel)
    }

    @Test
    fun `writes batch of models from mutliple threads`(forge: Forge) {
        val fakeModels = forge.aList { forge.getForgery(modelClass) }
        val runnables = fakeModels.map {
            Runnable { testedWriter.write(it) }
        }
        runnables.forEach {
            Thread(it).start()
        }

        waitForNextBatch()
        waitForNextBatch()
        val batch = testedReader.readNextBatch()!!
        batch.asJsonArray.forEachIndexed { i, jsonElement ->
            val jsonObject = jsonElement.asJsonObject
            assertHasMatches(jsonObject, fakeModels)
        }
    }

    @Test
    fun `don't write model if size is too big`(forge: Forge) {
        val bigModel = bigModel(forge)
        testedWriter.write(bigModel)
        waitForNextBatch()
        val batch = testedReader.readNextBatch()

        assertThat(batch)
            .isNull()
    }

    @Test
    fun `limit the number of models per batch`(forge: Forge) {
        val models = forge.aList(maxMessagesPerPath * 3) {
            lightModel(forge)
        }

        models.forEach { testedWriter.write(it) }
        waitForNextBatch()
        val batch = testedReader.readNextBatch()!!
        testedReader.dropBatch(batch.id)
        waitForNextBatch()
        val batch2 = testedReader.readNextBatch()!!

        assertThat(batch.asJsonArray.size())
            .isEqualTo(maxMessagesPerPath)
        assertThat(batch2.asJsonArray.size())
            .isEqualTo(maxMessagesPerPath)
        batch.asJsonArray.forEachIndexed { i, model ->
            val jsonObject = model.asJsonObject
            assertMatches(jsonObject, models[i])
        }
        batch2.asJsonArray.forEachIndexed { i, model ->
            val jsonObject = model.asJsonObject
            assertMatches(jsonObject, models[i + maxMessagesPerPath])
        }
    }

    @Test
    fun `read returns null when first batch is already sent`(forge: Forge) {
        val fakeModel = forge.getForgery(modelClass)
        testedWriter.write(fakeModel)
        waitForNextBatch()
        val batch = testedReader.readNextBatch()
        checkNotNull(batch)

        testedReader.dropBatch(batch.id)
        val batch2 = testedReader.readNextBatch()

        assertThat(batch2)
            .isNull()
    }

    @Test
    fun `read returns null when first batch is too recent`(forge: Forge) {
        val fakeModel = forge.getForgery(modelClass)
        testedWriter.write(fakeModel)
        val batch = testedReader.readNextBatch()
        assertThat(batch)
            .isNull()
    }

    @Test
    fun `read returns null when nothing was written`() {

        val batch = testedReader.readNextBatch()
        assertThat(batch)
            .isNull()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `read returns null when drop all was called`(
        forge: Forge
    ) {
        val firstModels = forge.aList { forge.getForgery(modelClass) }
        val secondModels = forge.aList { forge.getForgery(modelClass) }
        firstModels.forEach { testedWriter.write(it) }
        waitForNextBatch()
        secondModels.forEach { testedWriter.write(it) }

        testedReader.dropAllBatches()
        val batch = testedReader.readNextBatch()

        assertThat(batch)
            .isNull()
    }

    @Test
    fun `fails gracefully if sent batch with unknown id`(
        forge: Forge
    ) {
        val batchId = forge.aNumericalString()

        testedReader.dropBatch(batchId)
    }

    @Test
    fun `read returns null when 1st batch is already sent but file still present`(
        forge: Forge
    ) {
        val fakeModel = forge.getForgery(modelClass)
        testedWriter.write(fakeModel)
        waitForNextBatch()
        val batch = testedReader.readNextBatch()
        checkNotNull(batch)

        testedReader.dropBatch(batch.id)
        val logsDir = File(tempDir, dataFolderName)
        val file = File(logsDir, batch.id)
        file.writeText("I'm still there !")
        val batch2 = testedReader.readNextBatch()

        assertThat(batch2)
            .isNull()
    }

    @Test
    fun `it will create and share one single writer instance`() {
        // given
        val persistenceStrategy = getStrategy()
        val currentWriter = persistenceStrategy.getWriter()

        // then
        assertThat(persistenceStrategy.getWriter()).isSameAs(currentWriter)
    }

    // endregion

    // region Abstract

    abstract fun minimalCopy(of: T): T
    abstract fun lightModel(forge: Forge): T
    abstract fun bigModel(forge: Forge): T

    abstract fun assertHasMatches(
        jsonObject: JsonObject,
        models: List<T>
    )

    abstract fun assertMatches(
        jsonObject: JsonObject,
        model: T
    )

    // endregion

    companion object {

        const val MAX_BATCH_SIZE: Long = 128 * 1024
        const val MAX_MESSAGES_PER_BATCH: Int = 32
    }
}
