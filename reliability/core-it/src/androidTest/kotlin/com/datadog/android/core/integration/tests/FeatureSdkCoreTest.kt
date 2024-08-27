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
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.stub.StubContextUpdateReceiver
import com.datadog.android.api.feature.stub.StubFeatureEventReceiver
import com.datadog.android.api.feature.stub.StubStorageBackedFeature
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.integration.tests.forge.factories.ConfigurationCoreForgeryFactory
import com.datadog.android.core.integration.tests.utils.removeRandomEntries
import com.datadog.android.core.thread.FlushableExecutorService
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.forge.useToolsFactories
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit4.ForgeRule
import fr.xgouchet.elmyr.jvm.useJvmFactories
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Provides the tests for the InternalSdkCore API not related with the writing operations.
 */
@RunWith(AndroidJUnit4::class)
class FeatureSdkCoreTest : MockServerTest() {

    @get:Rule
    var forge = ForgeRule().useJvmFactories().useToolsFactories().withFactory(ConfigurationCoreForgeryFactory())

    private lateinit var stubFeature: Feature

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeFeatureName: String

    private lateinit var fakeTrackingConsent: TrackingConsent

    private lateinit var testedFeatureSdkCore: FeatureSdkCore

    @Forgery
    lateinit var fakeConfiguration: Configuration

    @Before
    fun setUp() {
        stubFeature = StubStorageBackedFeature(
            forge,
            fakeFeatureName,
            getMockServerWrapper().getServerUrl()
        )
        fakeTrackingConsent = forge.aValueFrom(TrackingConsent::class.java)
        testedFeatureSdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            fakeConfiguration,
            fakeTrackingConsent
        ) as FeatureSdkCore
    }

    @After
    fun tearDown() {
        Datadog.stopInstance()
    }

    // region register/get Feature

    @Test
    fun mustReturnAllTheRegisteredFeatures_when_getFeature_newFeaturesRegistered() {
        // Given
        val registeredFeatures = forge.aList(forge.anInt(1, 10)) {
            StubStorageBackedFeature(
                forge,
                forge.anAlphabeticalString(),
                getMockServerWrapper().getServerUrl()
            )
        }.onEach { testedFeatureSdkCore.registerFeature(it) }

        // When
        val retrievedFeatures = registeredFeatures.mapNotNull { testedFeatureSdkCore.getFeature(it.name)?.unwrap() }

        // Then
        assertThat(retrievedFeatures).containsAll(registeredFeatures)
    }

    @Test
    fun mustReturnTheCrashReportFeature_when_getFeature_crashReportEnabled() {
        // Given
        val fakeConfigCrashReportsEnabled = fakeConfiguration.copy(crashReportsEnabled = true)

        // When
        // stop the current instance
        Datadog.stopInstance()
        val internalSdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            fakeConfigCrashReportsEnabled,
            fakeTrackingConsent
        ) as InternalSdkCore

        // When
        val crashReportsFeature = internalSdkCore.getFeature(CRASH_REPORTS_TASK_NAME)?.unwrap<Feature>()

        // Then
        checkNotNull(crashReportsFeature)
    }

    @Test
    fun mustReturnEmptyFeatures_when_getFeature_crashReportNotEnabled() {
        // Given
        val fakeConfigCrashReportsNotEnabled = fakeConfiguration.copy(crashReportsEnabled = false)

        // When
        // stop the current instance
        Datadog.stopInstance()
        val internalSdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            fakeConfigCrashReportsNotEnabled,
            fakeTrackingConsent
        ) as InternalSdkCore

        // When
        val crashReportsFeature = internalSdkCore.getFeature(CRASH_REPORTS_TASK_NAME)?.unwrap<Feature>()

        // Then
        assertThat(crashReportsFeature).isNull()
    }

    @Test
    fun isThreadSafe_when_registerMultipleFeatures_fromDifferentThreads() {
        // Given
        val registeredFeatures = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            StubStorageBackedFeature(
                forge,
                forge.anAlphabeticalString(),
                getMockServerWrapper().getServerUrl()
            )
        }
        val countDownLatch = CountDownLatch(registeredFeatures.size)
        registeredFeatures.forEach { feature ->
            val thread = Thread {
                testedFeatureSdkCore.registerFeature(feature)
                countDownLatch.countDown()
            }
            thread.start()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)

        // When
        val retrievedFeatures = registeredFeatures.mapNotNull { testedFeatureSdkCore.getFeature(it.name)?.unwrap() }

        // Then
        assertThat(retrievedFeatures).containsAll(registeredFeatures)
    }

    @Test
    fun isThreadSafe_when_registerSameFeature_fromDifferentThreads() {
        // Given
        val countDownLatch = CountDownLatch(10)
        repeat(10) {
            val thread = Thread {
                testedFeatureSdkCore.registerFeature(stubFeature)
                countDownLatch.countDown()
            }
            thread.start()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)

        // When
        val registeredFeature = testedFeatureSdkCore.getFeature(stubFeature.name)?.unwrap<Feature>()

        // Then
        assertThat(registeredFeature).isSameAs(stubFeature)
    }

    // endregion

    // region Feature Context

    @Test
    fun must_returnEmptyContext_when_getFeatureContext_featureRegistered() {
        // Given
        testedFeatureSdkCore.registerFeature(stubFeature)

        // When
        val context = testedFeatureSdkCore.getFeatureContext(stubFeature.name)

        // Then
        assertThat(context).isEmpty()
    }

    @Test
    fun must_returnEmptyContext_when_getFeatureContext_featureNotRegistered() {
        // When
        val context = testedFeatureSdkCore.getFeatureContext(forge.anAlphabeticalString())

        // Then
        assertThat(context).isEmpty()
    }

    @Test
    fun mustReturnTheUpdatedFeatureContext_when_getFeatureContext_featureContextWasSet() {
        // Given
        testedFeatureSdkCore.registerFeature(stubFeature)
        val fakeKeyValues = forge.aMap { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        testedFeatureSdkCore.updateFeatureContext(fakeFeatureName) {
            fakeKeyValues.forEach { (key, value) ->
                it[key] = value
            }
        }

        // When
        val context = testedFeatureSdkCore.getFeatureContext(stubFeature.name)

        // Then
        assertThat(context).containsExactlyInAnyOrderEntriesOf(fakeKeyValues)
    }

    @Test
    fun mustUseAtomicOperations_when_updateFeatureContext_addNewValues() {
        // Given
        testedFeatureSdkCore.registerFeature(stubFeature)
        val fakeKeyValues1 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val fakeKeyValues2 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val expectedKeyValues = fakeKeyValues1 + fakeKeyValues2
        val countDownLatch = CountDownLatch(2)
        testedFeatureSdkCore.setContextUpdateReceiver(fakeFeatureName) { _, _ -> countDownLatch.countDown() }
        Thread {
            testedFeatureSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues1.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        Thread {
            testedFeatureSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues2.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)

        // When
        val context = testedFeatureSdkCore.getFeatureContext(stubFeature.name)

        // Then
        assertThat(context).containsExactlyInAnyOrderEntriesOf(expectedKeyValues)
    }

    @Test
    fun mustUseAtomicOperations_when_updateFeatureContext_modifyValues() {
        // Given
        testedFeatureSdkCore.registerFeature(stubFeature)
        val threadStartedCountDownLatch = CountDownLatch(2)
        val fakeKeyValues1 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val fakeKeyValues2 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val fakeModifiedValues = fakeKeyValues2.mapValues { (_, _) -> forge.anAlphabeticalString() }
        val expectedKeyValues = fakeKeyValues1 + fakeModifiedValues
        val countDownLatch = CountDownLatch(3)
        testedFeatureSdkCore.setContextUpdateReceiver(fakeFeatureName) { _, _ -> countDownLatch.countDown() }
        Thread {
            threadStartedCountDownLatch.countDown()
            testedFeatureSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues1.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        Thread {
            threadStartedCountDownLatch.countDown()
            testedFeatureSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues2.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        threadStartedCountDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        Thread {
            testedFeatureSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues2.forEach { (key, _) ->
                    it[key] = fakeModifiedValues[key]
                }
            }
        }.start()
        // we need to wait 3 times the SHORT_WAIT_MS because the last update is done after the first two
        countDownLatch.await(SHORT_WAIT_MS * 3, TimeUnit.MILLISECONDS)

        // When
        val context = testedFeatureSdkCore.getFeatureContext(stubFeature.name)

        // Then
        assertThat(context).containsExactlyInAnyOrderEntriesOf(expectedKeyValues)
    }

    @Test
    fun mustUseAtomicOperations_when_updateFeatureContext_removeValues() {
        // Given
        testedFeatureSdkCore.registerFeature(stubFeature)
        val threadStartedCountDownLatch = CountDownLatch(2)
        val fakeKeyValues1 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val fakeKeyValues2 = forge.aMap(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val expectedKeyValues = (fakeKeyValues1 + fakeKeyValues2).toMutableMap()
        val droppedKeyValues = expectedKeyValues.removeRandomEntries(forge)
        val countDownLatch = CountDownLatch(3)
        testedFeatureSdkCore.setContextUpdateReceiver(fakeFeatureName) { _, _ -> countDownLatch.countDown() }
        Thread {
            threadStartedCountDownLatch.countDown()
            testedFeatureSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues1.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        Thread {
            threadStartedCountDownLatch.countDown()
            testedFeatureSdkCore.updateFeatureContext(fakeFeatureName) {
                fakeKeyValues2.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        threadStartedCountDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        Thread {
            threadStartedCountDownLatch.countDown()
            testedFeatureSdkCore.updateFeatureContext(fakeFeatureName) {
                droppedKeyValues.forEach { (key, _) ->
                    it.remove(key)
                }
            }
        }.start()
        // we need to wait 3 times the SHORT_WAIT_MS because the last update is done after the first two
        countDownLatch.await(SHORT_WAIT_MS * 3, TimeUnit.MILLISECONDS)

        // When
        val context = testedFeatureSdkCore.getFeatureContext(stubFeature.name)

        // Then
        assertThat(context).containsExactlyInAnyOrderEntriesOf(expectedKeyValues)
    }

    // endregion

    // region Context Update Receiver

    @Test
    fun mustReceiveContextUpdate_when_contextUpdateReceiverRegistered() {
        // Given
        val stubContextUpdateReceiver = StubContextUpdateReceiver()
        val otherFeature = StubStorageBackedFeature(
            forge,
            forge.anAlphabeticalString(),
            getMockServerWrapper().getServerUrl()
        )
        val fakeKeyValues = forge.aMap { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        testedFeatureSdkCore.registerFeature(stubFeature)
        testedFeatureSdkCore.registerFeature(otherFeature)
        testedFeatureSdkCore.setContextUpdateReceiver(otherFeature.name, stubContextUpdateReceiver)

        // When
        testedFeatureSdkCore.updateFeatureContext(stubFeature.name) {
            fakeKeyValues.forEach { (key, value) ->
                it[key] = value
            }
        }

        // Then
        assertThat(stubContextUpdateReceiver.getReceivedEvents()).hasSize(1)
        val receivedEvent = stubContextUpdateReceiver.getReceivedEvents().first()
        assertThat(receivedEvent.featureName).isEqualTo(stubFeature.name)
        assertThat(receivedEvent.eventData).containsExactlyInAnyOrderEntriesOf(fakeKeyValues)
    }

    @Test
    fun mustReceiveContextUpdate_when_contextUpdateReceiverRegistered_updateCalledFromDifferentThreads() {
        // Given
        val otherFeature = StubStorageBackedFeature(
            forge,
            forge.anAlphabeticalString(),
            getMockServerWrapper().getServerUrl()
        )
        val fakeKeyValues1 = forge.aMap(size = 2) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        val fakeKeyValues2 = forge.aMap(size = 2) { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        testedFeatureSdkCore.registerFeature(stubFeature)
        testedFeatureSdkCore.registerFeature(otherFeature)
        val countDownLatch = CountDownLatch(2)
        val stubContextUpdateReceiver = StubContextUpdateReceiver(countDownLatch::countDown)
        testedFeatureSdkCore.setContextUpdateReceiver(otherFeature.name, stubContextUpdateReceiver)

        // When
        Thread {
            testedFeatureSdkCore.updateFeatureContext(stubFeature.name) {
                fakeKeyValues1.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()
        Thread {
            testedFeatureSdkCore.updateFeatureContext(stubFeature.name) {
                fakeKeyValues2.forEach { (key, value) ->
                    it[key] = value
                }
            }
        }.start()

        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)

        // Then
        val receivedEvents = stubContextUpdateReceiver.getReceivedEvents()
        assertThat(receivedEvents).hasSize(2)
        val contextUpdates = receivedEvents.filter { it.featureName == stubFeature.name }.map { it.eventData }
        val firstGroupUpdates = contextUpdates.filter { it.entries.containsAll(fakeKeyValues1.entries) }
        val secondGroupUpdates = contextUpdates.filter { it.entries.containsAll(fakeKeyValues2.entries) }
        // each group must not be empty meaning that no matter the order of the updates, both are received
        assertThat(firstGroupUpdates).isNotEmpty
        assertThat(secondGroupUpdates).isNotEmpty
    }

    @Test
    fun mustNotReceiveContextUpdate_when_contextUpdateReceiverRegistered_contextUpdateOnSameFeature() {
        // Given
        val stubContextUpdateReceiver = StubContextUpdateReceiver()
        val fakeKeyValues = forge.aMap { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        testedFeatureSdkCore.registerFeature(stubFeature)
        testedFeatureSdkCore.setContextUpdateReceiver(stubFeature.name, stubContextUpdateReceiver)

        // When
        testedFeatureSdkCore.updateFeatureContext(stubFeature.name) {
            fakeKeyValues.forEach { (key, value) ->
                it[key] = value
            }
        }

        // Then
        assertThat(stubContextUpdateReceiver.getReceivedEvents()).isEmpty()
    }

    @Test
    fun mustReceiveNoContextUpdate_when_contextUpdateReceiverNotRegistered() {
        // Given
        val stubContextUpdateReceiver = StubContextUpdateReceiver()
        val otherFeature = StubStorageBackedFeature(
            forge,
            forge.anAlphabeticalString(),
            getMockServerWrapper().getServerUrl()
        )
        val fakeKeyValues = forge.aMap { forge.anAlphabeticalString() to forge.anAlphabeticalString() }
        testedFeatureSdkCore.registerFeature(stubFeature)
        testedFeatureSdkCore.registerFeature(otherFeature)
        testedFeatureSdkCore.setContextUpdateReceiver(otherFeature.name, stubContextUpdateReceiver)

        // When
        testedFeatureSdkCore.removeContextUpdateReceiver(otherFeature.name, stubContextUpdateReceiver)
        testedFeatureSdkCore.updateFeatureContext(stubFeature.name) {
            fakeKeyValues.forEach { (key, value) ->
                it[key] = value
            }
        }

        // Then
        assertThat(stubContextUpdateReceiver.getReceivedEvents()).isEmpty()
    }

    // endregion

    // region set Event Receiver

    @Test
    fun mustReceiveEvent_when_eventReceiverRegistered() {
        // Given
        val stubFeatureEventReceiver = StubFeatureEventReceiver()
        val fakeEvent = forge.anAlphabeticalString()
        testedFeatureSdkCore.registerFeature(stubFeature)
        testedFeatureSdkCore.setEventReceiver(stubFeature.name, stubFeatureEventReceiver)
        val stubRegisteredFeature = testedFeatureSdkCore.getFeature(stubFeature.name)

        // When
        assertThat(stubRegisteredFeature).isNotNull
        stubRegisteredFeature?.sendEvent(fakeEvent)

        // Then
        assertThat(stubFeatureEventReceiver.getReceivedEvents()).containsOnly(fakeEvent)
    }

    @Test
    fun mustNotReceiveEvents_when_eventReceiverUnregistered() {
        // Given
        val stubFeatureEventReceiver = StubFeatureEventReceiver()
        val fakeEvent1 = forge.anAlphabeticalString()
        val fakeEvent2 = forge.anAlphabeticalString()
        testedFeatureSdkCore.registerFeature(stubFeature)
        testedFeatureSdkCore.setEventReceiver(stubFeature.name, stubFeatureEventReceiver)
        val stubRegisteredFeature = testedFeatureSdkCore.getFeature(stubFeature.name)
        checkNotNull(stubRegisteredFeature)
        stubRegisteredFeature.sendEvent(fakeEvent1)

        // When
        testedFeatureSdkCore.removeEventReceiver(stubFeature.name)
        stubRegisteredFeature.sendEvent(fakeEvent2)

        // Then
        assertThat(stubFeatureEventReceiver.getReceivedEvents()).hasSize(1)
        assertThat(stubFeatureEventReceiver.getReceivedEvents()).containsOnly(fakeEvent1)
    }

    @Test
    fun mustNotReceiveEvents_when_eventReceiverRegistered_eventSentForADifferentFeature() {
        // Given
        val stubFeatureEventReceiver = StubFeatureEventReceiver()
        val otherFeature = StubStorageBackedFeature(
            forge,
            forge.anAlphabeticalString(),
            getMockServerWrapper().getServerUrl()
        )
        val fakeEvent = forge.anAlphabeticalString()
        testedFeatureSdkCore.registerFeature(stubFeature)
        testedFeatureSdkCore.registerFeature(otherFeature)
        testedFeatureSdkCore.setEventReceiver(stubFeature.name, stubFeatureEventReceiver)
        val otherRegisteredFeature = testedFeatureSdkCore.getFeature(otherFeature.name)

        // When
        assertThat(otherRegisteredFeature).isNotNull
        otherRegisteredFeature?.sendEvent(fakeEvent)

        // Then
        assertThat(stubFeatureEventReceiver.getReceivedEvents()).isEmpty()
    }

    @Test
    fun mustReceiveEvents_when_eventReceiverRegistered_sendEventCalledFromDifferentThreads() {
        // Given
        val fakeEvent1 = forge.anAlphabeticalString()
        val fakeEvent2 = forge.anAlphabeticalString()
        val countDownLatch = CountDownLatch(2)
        val stubFeatureEventReceiver = StubFeatureEventReceiver(countDownLatch::countDown)
        testedFeatureSdkCore.registerFeature(stubFeature)
        testedFeatureSdkCore.setEventReceiver(stubFeature.name, stubFeatureEventReceiver)
        val stubRegisteredFeature = testedFeatureSdkCore.getFeature(stubFeature.name)

        // When
        checkNotNull(stubRegisteredFeature)
        Thread {
            stubRegisteredFeature.sendEvent(fakeEvent1)
        }.start()
        Thread {
            stubRegisteredFeature.sendEvent(fakeEvent2)
        }.start()

        // Then
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(stubFeatureEventReceiver.getReceivedEvents()).containsExactlyInAnyOrder(fakeEvent1, fakeEvent2)
    }

    // endregion

    // region SingleThreadExecutor Service

    @Test
    fun mustCreateASingleThreadExecutorService_when_createSingleThreadExecutorService() {
        // Given
        val fakeExecutorContext = forge.anAlphabeticalString()
        val executorService = testedFeatureSdkCore.createSingleThreadExecutorService(fakeExecutorContext)

        // When
        assertThat(executorService).isInstanceOf(FlushableExecutorService::class.java)

        // Then
        assertThat(executorService.isShutdown).isFalse()
        assertThat(executorService.isTerminated).isFalse()

        // Tear down
        executorService.shutdown()
    }

    // endregion

    // region ScheduledExecutor Service

    @Test
    fun mustCreateAScheduledExecutorService_when_createScheduledExecutorService() {
        // Given
        val fakeExecutorContext = forge.anAlphabeticalString()
        val executorService = testedFeatureSdkCore.createScheduledExecutorService(fakeExecutorContext)

        // When
        assertThat(executorService).isInstanceOf(ScheduledExecutorService::class.java)

        // Then
        assertThat(executorService.isShutdown).isFalse()
        assertThat(executorService.isTerminated).isFalse()

        // Tear down
        executorService.shutdown()
    }

    // endregion

    companion object {
        private const val CRASH_REPORTS_TASK_NAME = "crash"
    }
}
