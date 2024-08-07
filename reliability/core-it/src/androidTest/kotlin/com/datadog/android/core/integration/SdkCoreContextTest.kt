/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.feature.stub.StubStorageBackedFeature
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.integration.tests.MockServerTest
import com.datadog.android.core.integration.tests.forge.factories.ConfigurationCoreForgeryFactory
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.forge.exhaustiveAttributes
import com.datadog.tools.unit.forge.useToolsFactories
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
import java.util.concurrent.TimeUnit

/**
 * Provides the tests for the SDK Core API responsible for modifying the User information, data collection consent,
 * etc.
 */
@RunWith(AndroidJUnit4::class)
class SdkCoreContextTest : MockServerTest() {

    @get:Rule
    var forge = ForgeRule().useJvmFactories().useToolsFactories().withFactory(ConfigurationCoreForgeryFactory())

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeUserId: String

    @StringForgery(regex = "[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+")
    lateinit var fakeUserName: String

    @StringForgery(regex = "[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}")
    lateinit var fakeUserEmail: String
    private var fakeUserAdditionalProperties: Map<String, Any?> = emptyMap()
    private lateinit var stubFeature: StorageBackedFeature

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeFeatureName: String

    private lateinit var fakeTrackingConsent: TrackingConsent

    private var featureSdkCore: FeatureSdkCore? = null
    private var testedSdkCore: SdkCore? = null

    @Before
    fun setUp() {
        stubFeature = StubStorageBackedFeature(
            forge,
            fakeFeatureName,
            getMockServerWrapper().getServerUrl()
        )
        fakeTrackingConsent = forge.aValueFrom(TrackingConsent::class.java)
        fakeUserAdditionalProperties = forge.exhaustiveAttributes(excludedKeys = setOf("id", "name", "email"))
        val configuration: Configuration = forge.getForgery()
        testedSdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            configuration,
            fakeTrackingConsent
        )
        featureSdkCore = testedSdkCore as? FeatureSdkCore
        featureSdkCore?.registerFeature(stubFeature)
    }

    @After
    fun tearDown() {
        Datadog.stopInstance()
    }

    // region set UserInfo

    @Test
    fun must_addUserInformationIntoEvents_when_setUserInformation() {
        // When
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeUserAdditionalProperties)

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(fakeUserAdditionalProperties)
    }

    // endregion

    // region add User Properties

    @Test
    fun must_addUserExtraProperties_when_addUserProperties() {
        // Given
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeUserAdditionalProperties)
        val expectedUserExtraProperties = forge.exhaustiveAttributes()

        // When
        testedSdkCore?.addUserProperties(expectedUserExtraProperties)

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(fakeUserAdditionalProperties + expectedUserExtraProperties)
    }

    @Test
    fun must_useImmutableProperties_when_setUserProperties_attributesValuesModified() {
        // Given
        val fakeMutableProperties = forge.exhaustiveAttributes()
        val expectedMutableProperties = fakeMutableProperties.toMap()
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeMutableProperties)

        // When
        fakeMutableProperties.keys.forEach { key ->
            fakeMutableProperties[key] = forge.anAlphaNumericalString()
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(expectedMutableProperties)
    }

    @Test
    fun must_useImmutableProperties_when_setUserProperties_attributesValuesRemoved() {
        // Given
        val fakeMutableProperties = forge.exhaustiveAttributes()
        val expectedMutableProperties = fakeMutableProperties.toMap()
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeMutableProperties)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeMutableProperties.remove(fakeMutableProperties.keys.random())
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(expectedMutableProperties)
    }

    @Test
    fun must_useImmutableProperties_when_setUserProperties_addExtraAttributes() {
        // Given
        val fakeMutableProperties = forge.exhaustiveAttributes()
        val expectedMutableProperties = fakeMutableProperties.toMap()
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeMutableProperties)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeMutableProperties[forge.anAlphabeticalString()] = forge.anAlphabeticalString()
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(expectedMutableProperties)
    }

    @Test
    fun must_useImmutableProperties_when_addUserProperties_attributesValuesModified() {
        // Given
        val fakeExtraProperties = forge.exhaustiveAttributes()
        val expectedExtraProperties = fakeExtraProperties.toMap()
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeUserAdditionalProperties)
        testedSdkCore?.addUserProperties(fakeExtraProperties)

        // When
        fakeExtraProperties.keys.forEach { key ->
            fakeExtraProperties[key] = forge.anAlphabeticalString()
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(fakeUserAdditionalProperties + expectedExtraProperties)
    }

    @Test
    fun must_useImmutableProperties_when_addUserProperties_attributesValuesRemoved() {
        // Given
        val fakeExtraProperties = forge.exhaustiveAttributes()
        val expectedExtraProperties = fakeExtraProperties.toMap()
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeUserAdditionalProperties)
        testedSdkCore?.addUserProperties(fakeExtraProperties)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeExtraProperties.remove(fakeExtraProperties.keys.random())
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(fakeUserAdditionalProperties + expectedExtraProperties)
    }

    @Test
    fun must_useImmutableProperties_when_addUserProperties_addExtraAttributes() {
        // Given
        val fakeExtraProperties = forge.exhaustiveAttributes()
        val expectedExtraProperties = fakeExtraProperties.toMap()
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeUserAdditionalProperties)
        testedSdkCore?.addUserProperties(fakeExtraProperties)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeExtraProperties[forge.anAlphabeticalString()] = forge.anAlphabeticalString()
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(fakeUserAdditionalProperties + expectedExtraProperties)
    }

    @Test
    fun must_resetUserProperties_when_setUserPropertiesCalledSecondTime() {
        // Given
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeUserAdditionalProperties)
        val expectedUserExtraProperties = forge.exhaustiveAttributes()
        testedSdkCore?.addUserProperties(expectedUserExtraProperties)
        val fakeUserId2 = forge.anAlphabeticalString()
        val fakeUserName2 = forge.anAlphabeticalString()
        val fakeUserEmail2 = forge.anAlphabeticalString()
        val fakeUserAdditionalProperties2 = forge.exhaustiveAttributes()

        // When
        testedSdkCore?.setUserInfo(fakeUserId2, fakeUserName2, fakeUserEmail2, fakeUserAdditionalProperties2)

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId2)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName2)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail2)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(fakeUserAdditionalProperties2)
    }

    @Test
    fun must_resetUserProperties_when_setUserPropertiesCalled_afterAddUserProperties() {
        // Given
        val expectedUserExtraProperties = forge.exhaustiveAttributes()
        testedSdkCore?.addUserProperties(expectedUserExtraProperties)

        // When
        testedSdkCore?.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail, fakeUserAdditionalProperties)

        // Then
        val countDownLatch = CountDownLatch(1)
        var readUserInfo: UserInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readUserInfo = datadogContext.userInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readUserInfo?.id).isEqualTo(fakeUserId)
        assertThat(readUserInfo?.name).isEqualTo(fakeUserName)
        assertThat(readUserInfo?.email).isEqualTo(fakeUserEmail)
        assertThat(readUserInfo?.additionalProperties)
            .containsExactlyInAnyOrderEntriesOf(fakeUserAdditionalProperties)
    }

    // endregion

    // region set Tracking Consent

    @Test
    fun must_useTheInitializationTrackingConsent_when_initialize() {
        val countDownLatch = CountDownLatch(1)
        var trackingConsent: TrackingConsent? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            trackingConsent = datadogContext.trackingConsent
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(trackingConsent).isEqualTo(fakeTrackingConsent)
    }

    @Test
    fun must_updateTrackingConsent_when_setTrackingConsent() {
        // Given
        val expectedTrackingConsent = forge.aValueFrom(TrackingConsent::class.java)

        // When
        testedSdkCore?.setTrackingConsent(expectedTrackingConsent)

        // Then
        val countDownLatch = CountDownLatch(1)
        var trackingConsent: TrackingConsent? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            trackingConsent = datadogContext.trackingConsent
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(trackingConsent).isEqualTo(expectedTrackingConsent)
    }

    // endregion
}
