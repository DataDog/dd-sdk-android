/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsClientCreationTest {
    @Mock
    lateinit var mockDataStore: DataStoreHandler

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeContext: EvaluationContext

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @IntForgery
    var fakeVersion: Int = 0

    @LongForgery
    var fakeTimestamp: Long = 0

    private lateinit var testedClient: FlagsClient
    private lateinit var cacheCallback: DataStoreReadCallback<FlagsStateEntry>
    private val networkExecutor = TestExecutor(queued = true)
    private val callbackExecutor = TestExecutor(queued = false)
    private var delivered = false

    @BeforeEach
    fun setUp(forge: Forge) {
        val mockFeatureScope = mock<FeatureScope> {
            on { dataStore } doReturn mockDataStore
            on { withContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any()) } doAnswer { invocation ->
                invocation.getArgument<(DatadogContext) -> Unit>(1).invoke(fakeDatadogContext)
            }
        }
        val mockSdkCore = mock<FeatureSdkCore> {
            on { internalLogger } doReturn mockInternalLogger
            on { getFeature(Feature.FLAGS_FEATURE_NAME) } doReturn mockFeatureScope
            on { createOkHttpCallFactory(any()) } doReturn mock()
            on { createSingleThreadExecutorService(FlagsClient.FLAGS_NETWORK_EXECUTOR_NAME) } doReturn networkExecutor
            on { createSingleThreadExecutorService(FlagsClient.FLAGS_FIRST_FLAGS_EXECUTOR_NAME) } doReturn
                callbackExecutor
        }
        doAnswer {
            cacheCallback = it.getArgument(2)
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        val configuration = FlagsConfiguration.Builder().initializationTimeout(0).build()
        testedClient = FlagsClient.createInternal(
            configuration,
            mockSdkCore,
            FlagsFeature(mockSdkCore, configuration),
            null,
            forge.anAlphabeticalString()
        )
        testedClient.onFirstFlags { delivered = true }
    }

    @Test
    fun `M deliver cached first flags W completeCacheRead() {network work is pending}`() {
        // Given
        testedClient.setEvaluationContext(fakeContext)

        // When
        completeCacheRead()

        // Then
        assertThat(networkExecutor.pendingWork).hasSize(1)
        assertThat(delivered).isTrue()
    }

    @Test
    fun `M shut down callback executor W completeCacheRead()`() {
        // When
        completeCacheRead()

        // Then
        assertThat(callbackExecutor.isShutdown).isTrue()
    }

    private fun completeCacheRead() {
        cacheCallback.onSuccess(DataStoreContent(fakeVersion, FlagsStateEntry(fakeContext, emptyMap(), fakeTimestamp)))
    }

    private class TestExecutor(private val queued: Boolean) : AbstractExecutorService() {
        val pendingWork = ArrayDeque<Runnable>()
        private var stopped = false

        override fun execute(command: Runnable) {
            check(!stopped)
            if (queued) pendingWork.add(command) else command.run()
        }

        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> = pendingWork.toMutableList().also {
            pendingWork.clear()
            shutdown()
        }
        override fun isShutdown(): Boolean = stopped
        override fun isTerminated(): Boolean = stopped && pendingWork.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
    }
}
