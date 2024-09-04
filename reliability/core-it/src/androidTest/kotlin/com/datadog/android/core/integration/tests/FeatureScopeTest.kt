/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.android.Datadog
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.stub.StubStorageBackedFeature
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.integration.tests.assertj.MockWebServerAssert
import com.datadog.android.core.integration.tests.forge.factories.ConfigurationCoreForgeryFactory
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.ConditionWatcher
import com.datadog.tools.unit.forge.useToolsFactories
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit4.ForgeRule
import fr.xgouchet.elmyr.jvm.useJvmFactories
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the feature scope.
 * This test class is used to validate the correct behavior of the feature scope.
 * **Note**: The send event API was already tested on the [FeatureSdkCoreTest].
 */
@RunWith(AndroidJUnit4::class)
class FeatureScopeTest : MockServerTest() {

    @get:Rule
    var forge = ForgeRule()
        .useJvmFactories()
        .useToolsFactories()
        .withFactory(ConfigurationCoreForgeryFactory())

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeFeatureName: String

    @Forgery
    lateinit var fakeConfiguration: Configuration
    private lateinit var fakeBatchData: List<RawBatchEvent>
    private lateinit var fakeBatchMetadata: ByteArray
    private lateinit var eventType: EventType
    private lateinit var trackingConsent: TrackingConsent
    private lateinit var stubFeature: Feature
    private lateinit var testedInternalSdkCore: InternalSdkCore

    @Before
    fun setUp() {
        fakeBatchData = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            val fakeEvent: JsonObject = forge.getForgery()
            val eventMetadata = forge.anAlphabeticalString()
            RawBatchEvent(
                fakeEvent.toString().toByteArray(),
                eventMetadata.toByteArray()
            )
        }
        fakeBatchMetadata = forge.anAlphabeticalString().toByteArray()
        eventType = forge.aValueFrom(EventType::class.java)
        stubFeature = StubStorageBackedFeature(
            forge,
            fakeFeatureName,
            getMockServerWrapper().getServerUrl()
        )
        fakeConfiguration = forge.getForgery()
    }

    @After
    fun tearDown() {
        cleanStorage()
        Datadog.stopInstance()
        cleanMockWebServer()
    }

    // region Consent Granted

    @Test
    fun mustReceiveTheEvents_whenFeatureWrite_trackingConsentGranted() {
        // Given
        trackingConsent = TrackingConsent.GRANTED
        testedInternalSdkCore = Datadog.initialize(
            context = ApplicationProvider.getApplicationContext(),
            configuration = fakeConfiguration,
            trackingConsent = trackingConsent
        ) as InternalSdkCore
        testedInternalSdkCore.registerFeature(stubFeature)
        val featureScope = testedInternalSdkCore.getFeature(fakeFeatureName)

        // When
        checkNotNull(featureScope)
        featureScope.withWriteContext { _, eventBatchWriter ->
            fakeBatchData.forEach { rawBatchEvent ->
                eventBatchWriter.write(
                    rawBatchEvent,
                    fakeBatchMetadata,
                    eventType
                )
            }
        }

        // Then
        ConditionWatcher {
            MockWebServerAssert.assertThat(getMockServerWrapper())
                .withConfiguration(fakeConfiguration)
                .withTrackingConsent(trackingConsent)
                .receivedData(fakeBatchData, fakeBatchMetadata)
            true
        }.doWait(LONG_WAIT_MS)
    }

    @Test
    fun mustReceiveTheEvents_whenFeatureWrite_trackingConsentPendingToGranted() {
        // Given
        trackingConsent = TrackingConsent.PENDING
        testedInternalSdkCore = Datadog.initialize(
            context = ApplicationProvider.getApplicationContext(),
            configuration = fakeConfiguration,
            trackingConsent = trackingConsent
        ) as InternalSdkCore
        testedInternalSdkCore.registerFeature(stubFeature)
        val featureScope = testedInternalSdkCore.getFeature(fakeFeatureName)
        checkNotNull(featureScope)
        featureScope.withWriteContext { _, eventBatchWriter ->
            fakeBatchData.forEach { rawBatchEvent ->
                eventBatchWriter.write(
                    rawBatchEvent,
                    fakeBatchMetadata,
                    eventType
                )
            }
        }

        // When
        Datadog.setTrackingConsent(TrackingConsent.GRANTED)

        // Then
        ConditionWatcher {
            MockWebServerAssert.assertThat(getMockServerWrapper())
                .withConfiguration(fakeConfiguration)
                .withTrackingConsent(trackingConsent)
                .receivedData(fakeBatchData, fakeBatchMetadata)
            true
        }.doWait(LONG_WAIT_MS)
    }

    @Test
    fun mustReceiveTheEvents_whenFeatureWrite_trackingConsentGranted_metadataIsNull() {
        // Given
        trackingConsent = TrackingConsent.GRANTED
        testedInternalSdkCore = Datadog.initialize(
            context = ApplicationProvider.getApplicationContext(),
            configuration = fakeConfiguration,
            trackingConsent = trackingConsent
        ) as InternalSdkCore
        testedInternalSdkCore.registerFeature(stubFeature)
        val featureScope = testedInternalSdkCore.getFeature(fakeFeatureName)

        // When
        checkNotNull(featureScope)
        featureScope.withWriteContext { _, eventBatchWriter ->
            fakeBatchData.forEach { rawBatchEvent ->
                eventBatchWriter.write(
                    rawBatchEvent,
                    null,
                    eventType
                )
            }
        }

        // Then
        ConditionWatcher {
            MockWebServerAssert.assertThat(getMockServerWrapper())
                .withConfiguration(fakeConfiguration)
                .withTrackingConsent(trackingConsent)
                .receivedData(fakeBatchData, null)
            true
        }.doWait(LONG_WAIT_MS)
    }

    // endregion

    // region Consent Pending

    @Test
    fun mustNotReceiveAnyEvent_whenFeatureWrite_trackingConsentPending() {
        // Given
        val trackingConsent = TrackingConsent.PENDING
        testedInternalSdkCore = Datadog.initialize(
            context = ApplicationProvider.getApplicationContext(),
            configuration = fakeConfiguration,
            trackingConsent = trackingConsent
        ) as InternalSdkCore
        testedInternalSdkCore.registerFeature(stubFeature)
        val featureScope = testedInternalSdkCore.getFeature(fakeFeatureName)

        // When
        checkNotNull(featureScope)
        featureScope.withWriteContext { _, eventBatchWriter ->
            fakeBatchData.forEach { rawBatchEvent ->
                eventBatchWriter.write(
                    rawBatchEvent,
                    fakeBatchMetadata,
                    eventType
                )
            }
        }

        // Then
        Thread.sleep(MEDIUM_WAIT_MS)
        MockWebServerAssert.assertThat(getMockServerWrapper())
            .withConfiguration(fakeConfiguration)
            .withTrackingConsent(trackingConsent)
            .didNotReceiveData(fakeBatchData, fakeBatchMetadata)
    }

    // endregion

    // region Consent Not Granted

    @Test
    fun mustNotReceiveAnyEvent_whenFeatureWrite_trackingConsentNotGranted() {
        // Given
        val trackingConsent = TrackingConsent.NOT_GRANTED
        testedInternalSdkCore = Datadog.initialize(
            context = ApplicationProvider.getApplicationContext(),
            configuration = fakeConfiguration,
            trackingConsent = trackingConsent
        ) as InternalSdkCore
        testedInternalSdkCore.registerFeature(stubFeature)
        val featureScope = testedInternalSdkCore.getFeature(fakeFeatureName)

        // When
        checkNotNull(featureScope)
        featureScope.withWriteContext { _, eventBatchWriter ->
            fakeBatchData.forEach { rawBatchEvent ->
                eventBatchWriter.write(
                    rawBatchEvent,
                    fakeBatchMetadata,
                    eventType
                )
            }
        }

        // Then
        Thread.sleep(MEDIUM_WAIT_MS)
        MockWebServerAssert.assertThat(getMockServerWrapper())
            .withConfiguration(fakeConfiguration)
            .withTrackingConsent(trackingConsent)
            .didNotReceiveData(fakeBatchData, fakeBatchMetadata)
    }

    @Test
    fun mustNotReceiveAnyEvent_whenFeatureWrite_trackingConsentPendingToNotGranted() {
        // Given
        val trackingConsent = TrackingConsent.PENDING
        testedInternalSdkCore = Datadog.initialize(
            context = ApplicationProvider.getApplicationContext(),
            configuration = fakeConfiguration,
            trackingConsent = trackingConsent
        ) as InternalSdkCore
        testedInternalSdkCore.registerFeature(stubFeature)
        val featureScope = testedInternalSdkCore.getFeature(fakeFeatureName)
        checkNotNull(featureScope)
        featureScope.withWriteContext { _, eventBatchWriter ->
            fakeBatchData.forEach { rawBatchEvent ->
                eventBatchWriter.write(
                    rawBatchEvent,
                    fakeBatchMetadata,
                    eventType
                )
            }
        }

        // When
        Datadog.setTrackingConsent(TrackingConsent.NOT_GRANTED)

        // Then
        Thread.sleep(MEDIUM_WAIT_MS)
        MockWebServerAssert.assertThat(getMockServerWrapper())
            .withConfiguration(fakeConfiguration)
            .withTrackingConsent(trackingConsent)
            .didNotReceiveData(fakeBatchData, fakeBatchMetadata)
    }

    // endregion

    // region Clear Data

    @Test
    fun mustNotReceiveAnyEvent_whenFeatureWrite_clearDataCalled() {
        // Given
        trackingConsent = TrackingConsent.PENDING
        testedInternalSdkCore = Datadog.initialize(
            context = ApplicationProvider.getApplicationContext(),
            configuration = fakeConfiguration,
            trackingConsent = trackingConsent
        ) as InternalSdkCore
        testedInternalSdkCore.registerFeature(stubFeature)
        val featureScope = testedInternalSdkCore.getFeature(fakeFeatureName)
        checkNotNull(featureScope)
        featureScope.withWriteContext { _, eventBatchWriter ->
            fakeBatchData.forEach { rawBatchEvent ->
                eventBatchWriter.write(
                    rawBatchEvent,
                    fakeBatchMetadata,
                    eventType
                )
            }
        }

        // When
        Datadog.clearAllData()
        Datadog.setTrackingConsent(TrackingConsent.GRANTED)

        // Then
        Thread.sleep(MEDIUM_WAIT_MS)
        MockWebServerAssert.assertThat(getMockServerWrapper())
            .withConfiguration(fakeConfiguration)
            .withTrackingConsent(trackingConsent)
            .didNotReceiveData(fakeBatchData, fakeBatchMetadata)
    }

    // endregion

    // region Stop Instance

    @Test
    fun mustNotReceiveAnyEvent_whenFeatureWrite_stopInstanceCalled() {
        // Given
        trackingConsent = TrackingConsent.PENDING
        testedInternalSdkCore = Datadog.initialize(
            context = ApplicationProvider.getApplicationContext(),
            configuration = fakeConfiguration,
            trackingConsent = trackingConsent
        ) as InternalSdkCore
        testedInternalSdkCore.registerFeature(stubFeature)
        val featureScope = testedInternalSdkCore.getFeature(fakeFeatureName)
        checkNotNull(featureScope)
        Datadog.stopInstance()

        // When
        featureScope.withWriteContext { _, eventBatchWriter ->
            fakeBatchData.forEach { rawBatchEvent ->
                eventBatchWriter.write(
                    rawBatchEvent,
                    fakeBatchMetadata,
                    eventType
                )
            }
        }

        // Then
        Thread.sleep(MEDIUM_WAIT_MS)
        MockWebServerAssert.assertThat(getMockServerWrapper())
            .withConfiguration(fakeConfiguration)
            .withTrackingConsent(trackingConsent)
            .didNotReceiveData(fakeBatchData, fakeBatchMetadata)
    }

    // endregion

    private fun cleanStorage() {
        val storageFolder = testedInternalSdkCore.rootStorageDir
        storageFolder?.deleteRecursively()
    }

    // endregion
}
