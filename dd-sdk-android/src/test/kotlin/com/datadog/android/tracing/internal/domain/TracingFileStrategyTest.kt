/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.DeferredWriter
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.threading.LazyHandlerThread
import com.datadog.android.log.internal.domain.FilePersistenceStrategyTest
import com.datadog.android.utils.extension.assertMatches
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.SpanForgeryFactory
import com.datadog.tools.unit.invokeMethod
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import io.opentracing.Tracer
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class TracingFileStrategyTest :
    FilePersistenceStrategyTest<DDSpan>(
        TracingFileStrategy.TRACES_FOLDER,
        modelClass = DDSpan::class.java
    ) {

    // region LogStrategyTest

    override fun getStrategy(): PersistenceStrategy<DDSpan> {
        return TracingFileStrategy(
            context = mockContext,
            recentDelayMs = RECENT_DELAY_MS,
            maxBatchSize = MAX_BATCH_SIZE,
            maxLogPerBatch = MAX_MESSAGES_PER_BATCH,
            maxDiskSpace = MAX_DISK_SPACE
        )
    }

    override fun setUp(writer: Writer<DDSpan>, reader: Reader) {
        // add fake data into the old data directory
        val oldDir = File(tempDir, TracingFileStrategy.DATA_FOLDER_ROOT)
        oldDir.mkdirs()
        val file1 = File(oldDir, "file1")
        val file2 = File(oldDir, "file2")
        file1.createNewFile()
        file2.createNewFile()
        assertThat(oldDir).exists()
        (testedWriter as DeferredWriter<DDSpan>).deferredHandler = mockDeferredHandler
        (testedWriter as DeferredWriter<DDSpan>).invokeMethod(
            "consumeQueue",
            methodEnclosingClass = LazyHandlerThread::class.java
        ) // consume all the queued messages
    }

    @Test
    fun `migrates the data from v0 to v1`() {
    }

    // endregion

    // region utils

    override fun waitForNextBatch() {
        Thread.sleep(RECENT_DELAY_MS * 2)
    }

    override fun minimalCopy(of: DDSpan): DDSpan {
        return of
    }

    override fun lightModel(forge: Forge): DDSpan {
        return forge.getForgery()
    }

    override fun bigModel(forge: Forge): DDSpan {

        val operationName = forge.anAlphabeticalString(size = 65536)
        val resourceName = forge.anAlphabeticalString(size = 65536)
        val serviceName = forge.anAlphabeticalString(size = 65536)
        val spanType = forge.anAlphabeticalString(size = 65536)
        val isWithErrorFlag = forge.aBool()
        val tags = forge.aMap(size = 256) {
            forge.anAlphabeticalString(size = 64) to forge.anAlphabeticalString(
                size = 128
            )
        }
        val spanBuilder = SpanForgeryFactory.TEST_TRACER
            .buildSpan(operationName)
            .withSpanType(spanType)
            .withResourceName(resourceName)
            .withServiceName(serviceName)

        if (isWithErrorFlag) {
            spanBuilder.withErrorFlag()
        }
        (spanBuilder as Tracer.SpanBuilder).apply {
            tags.forEach {
                this.withTag(it.key, it.value)
            }
        }
        return spanBuilder.start()
    }

    override fun assertHasMatches(jsonObject: JsonObject, models: List<DDSpan>) {
        val serviceName = (jsonObject[SpanSerializer.SERVICE_NAME_KEY] as JsonPrimitive).asString

        val roughMatches = models.filter {
            serviceName == it.serviceName
        }

        assertThat(roughMatches).isNotEmpty()
    }

    override fun assertMatches(jsonObject: JsonObject, model: DDSpan) {
        jsonObject.assertMatches(model)
    }

    // endregion

    companion object {
        private const val RECENT_DELAY_MS = 150L
        private const val MAX_DISK_SPACE = 16 * 32 * 1024L
    }
}
