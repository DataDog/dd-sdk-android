/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.DeferredWriter
import com.datadog.android.core.internal.domain.FilePersistenceStrategyTest
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.utils.copy
import com.datadog.android.utils.extension.getString
import com.datadog.android.utils.extension.hexToBigInteger
import com.datadog.android.utils.extension.toHexString
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.SpanForgeryFactory
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.tools.unit.invokeMethod
import com.google.gson.JsonObject
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class TracingFileStrategyTest :
    FilePersistenceStrategyTest<DDSpan>(
        TracingFileStrategy.TRACES_FOLDER,
        modelClass = DDSpan::class.java
    ) {

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    // region LogStrategyTest

    override fun getStrategy(): PersistenceStrategy<DDSpan> {
        return TracingFileStrategy(
            context = mockContext,
            timeProvider = mockTimeProvider,
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
            "consumeQueue"
        ) // consume all the queued messages
    }
    // endregion

    // region utils

    override fun waitForNextBatch() {
        Thread.sleep(RECENT_DELAY_MS * 2)
    }

    override fun minimalCopy(of: DDSpan): DDSpan {
        return of.copy()
    }

    override fun lightModel(forge: Forge): DDSpan {
        return forge.getForgery()
    }

    override fun bigModel(forge: Forge): DDSpan {

        val maxBytesInSize = 256 * 1024
        val maxBytesInSizeForKeyValye = maxBytesInSize / 3
        val operationName = forge.anAlphabeticalString(size = maxBytesInSizeForKeyValye)
        val resourceName = forge.anAlphabeticalString(size = maxBytesInSizeForKeyValye)
        val serviceName = forge.anAlphabeticalString(size = maxBytesInSizeForKeyValye)
        val spanType = forge.anAlphabeticalString(size = maxBytesInSizeForKeyValye)
        val isWithErrorFlag = forge.aBool()
        val meta = forge.aMap(size = 256) {
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
        val span = spanBuilder.start()
        meta.forEach {
            span.context().setBaggageItem(it.key, it.value)
        }

        return span
    }

    override fun assertHasMatches(jsonObject: JsonObject, models: List<DDSpan>) {
        val serviceName = jsonObject.getString(SpanSerializer.SERVICE_NAME_KEY)
        val resourceName = jsonObject.getString(SpanSerializer.RESOURCE_KEY)
        val traceId = jsonObject.getString(SpanSerializer.TRACE_ID_KEY).hexToBigInteger()
        val spanId = jsonObject.getString(SpanSerializer.SPAN_ID_KEY).hexToBigInteger()
        val parentId = jsonObject.getString(SpanSerializer.PARENT_ID_KEY).hexToBigInteger()

        val roughMatches = models.filter {
            serviceName == it.serviceName &&
                traceId == it.traceId &&
                parentId == it.parentId &&
                spanId == it.spanId &&
                resourceName == it.resourceName
        }

        assertThat(roughMatches).isNotEmpty()
    }

    override fun assertMatches(jsonObject: JsonObject, model: DDSpan) {
        assertThat(jsonObject)
            .hasField(SpanSerializer.START_TIMESTAMP_KEY, model.startTime)
            .hasField(SpanSerializer.DURATION_KEY, model.durationNano)
            .hasField(SpanSerializer.SERVICE_NAME_KEY, model.serviceName)
            .hasField(SpanSerializer.TRACE_ID_KEY, model.traceId.toHexString())
            .hasField(SpanSerializer.SPAN_ID_KEY, model.spanId.toHexString())
            .hasField(SpanSerializer.PARENT_ID_KEY, model.parentId.toHexString())
            .hasField(SpanSerializer.RESOURCE_KEY, model.resourceName)
            .hasField(SpanSerializer.OPERATION_NAME_KEY, model.operationName)
            .hasField(SpanSerializer.META_KEY, model.meta)
            .hasField(SpanSerializer.METRICS_KEY, model.metrics)
    }

    // endregion

    companion object {
        private const val RECENT_DELAY_MS = 150L
        private const val MAX_DISK_SPACE = 16 * 32 * 1024L

        private const val HEX_RAD = 16
    }
}
