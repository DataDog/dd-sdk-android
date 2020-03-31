/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.DeferredWriter
import com.datadog.android.core.internal.domain.FilePersistenceStrategyTest
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.utils.copy
import com.datadog.android.utils.extension.getString
import com.datadog.android.utils.extension.hexToBigInteger
import com.datadog.android.utils.extension.toHexString
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.SpanForgeryFactory
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.tools.unit.invokeMethod
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider
    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Forgery
    lateinit var fakeUserInfo: UserInfo
    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    // region LogStrategyTest

    @BeforeEach
    override fun `set up`(forge: Forge) {
        // add fake data into the old data directory
        val oldDir = File(tempDir, TracingFileStrategy.DATA_FOLDER_ROOT)
        oldDir.mkdirs()
        val file1 = File(oldDir, "file1")
        val file2 = File(oldDir, "file2")
        file1.createNewFile()
        file2.createNewFile()
        assertThat(oldDir).exists()
        super.`set up`(forge)
    }

    override fun getStrategy(): PersistenceStrategy<DDSpan> {
        return TracingFileStrategy(
            context = mockContext,
            timeProvider = mockTimeProvider,
            networkInfoProvider = mockNetworkInfoProvider,
            userInfoProvider = mockUserInfoProvider,
            recentDelayMs = RECENT_DELAY_MS,
            maxBatchSize = MAX_BATCH_SIZE,
            maxLogPerBatch = MAX_MESSAGES_PER_BATCH,
            maxDiskSpace = MAX_DISK_SPACE,
            dataPersistenceExecutorService = mockExecutorService
        )
    }

    override fun setUp(writer: Writer<DDSpan>, reader: Reader) {
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        (testedWriter as DeferredWriter<DDSpan>).invokeMethod(
            "tryToConsumeQueue"
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
            span.setBaggageItem(it.key, it.value)
        }

        return span as DDSpan
    }

    override fun assertHasMatches(jsonObject: JsonObject, models: List<DDSpan>) {
        val serviceName = jsonObject.getString(SpanSerializer.TAG_SERVICE_NAME)
        val resourceName = jsonObject.getString(SpanSerializer.TAG_RESOURCE)
        val traceId = jsonObject.getString(SpanSerializer.TAG_TRACE_ID).hexToBigInteger()
        val spanId = jsonObject.getString(SpanSerializer.TAG_SPAN_ID).hexToBigInteger()
        val parentId = jsonObject.getString(SpanSerializer.TAG_PARENT_ID).hexToBigInteger()

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
            .hasField(SpanSerializer.TAG_START_TIMESTAMP, model.startTime)
            .hasField(SpanSerializer.TAG_DURATION, model.durationNano)
            .hasField(SpanSerializer.TAG_SERVICE_NAME, model.serviceName)
            .hasField(SpanSerializer.TAG_TRACE_ID, model.traceId.toHexString())
            .hasField(SpanSerializer.TAG_SPAN_ID, model.spanId.toHexString())
            .hasField(SpanSerializer.TAG_PARENT_ID, model.parentId.toHexString())
            .hasField(SpanSerializer.TAG_RESOURCE, model.resourceName)
            .hasField(SpanSerializer.TAG_OPERATION_NAME, model.operationName)
            .hasField(SpanSerializer.TAG_META, model.meta)
            .hasField(SpanSerializer.TAG_METRICS, model.metrics)
    }

    // endregion

    companion object {
        private const val RECENT_DELAY_MS = 150L
        private const val MAX_DISK_SPACE = 16 * 32 * 1024L

        private const val HEX_RAD = 16
    }
}
