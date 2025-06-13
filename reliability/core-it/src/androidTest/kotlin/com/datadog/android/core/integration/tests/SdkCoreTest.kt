/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.context.AccountInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.feature.stub.StubStorageBackedFeature
import com.datadog.android.core.configuration.Configuration
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
class SdkCoreTest : MockServerTest() {

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

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeAccountId: String

    @StringForgery(regex = "[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+")
    lateinit var fakeAccountName: String

    private var fakeAccountExtraInfo: Map<String, Any?> = emptyMap()

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
        fakeAccountExtraInfo = forge.exhaustiveAttributes(excludedKeys = setOf("id", "name"))
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

    // region set AccountInfo

    @Test
    fun must_addAccountInformationIntoEvents_when_setAccountInformation() {
        // When
        testedSdkCore?.setAccountInfo(
            fakeAccountId,
            fakeAccountName,
            fakeAccountExtraInfo
        )

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(fakeAccountExtraInfo)

        testedSdkCore?.clearAccountInfo()
        val countDownLatch2 = CountDownLatch(1)
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch2.countDown()
        }
        countDownLatch2.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo).isNull()
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
        repeat(forge.anInt(1, fakeMutableProperties.size / 2)) {
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
        repeat(forge.anInt(1, fakeExtraProperties.size / 2)) {
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

    // region add Account Properties

    @Test
    fun must_addAccountExtraInfo_when_addAccountInfo() {
        // Given
        testedSdkCore?.setAccountInfo(
            fakeAccountId,
            fakeAccountName,
            fakeAccountExtraInfo
        )
        val expectedAccountExtraProperties = forge.exhaustiveAttributes()

        // When
        testedSdkCore?.addAccountExtraInfo(expectedAccountExtraProperties)

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(fakeAccountExtraInfo + expectedAccountExtraProperties)
    }

    @Test
    fun must_useImmutableProperties_when_setAccountProperties_attributesValuesModified() {
        // Given
        val fakeMutableProperties = forge.exhaustiveAttributes()
        val expectedMutableProperties = fakeMutableProperties.toMap()
        testedSdkCore?.setAccountInfo(fakeAccountId, fakeAccountName, fakeMutableProperties)

        // When
        fakeMutableProperties.keys.forEach { key ->
            fakeMutableProperties[key] = forge.anAlphaNumericalString()
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(expectedMutableProperties)
    }

    @Test
    fun must_useImmutableProperties_when_setAccountProperties_attributesValuesRemoved() {
        // Given
        val fakeMutableProperties = forge.exhaustiveAttributes()
        val expectedMutableProperties = fakeMutableProperties.toMap()
        testedSdkCore?.setAccountInfo(fakeAccountId, fakeAccountName, fakeMutableProperties)

        // When
        repeat(forge.anInt(1, fakeMutableProperties.size / 2)) {
            fakeMutableProperties.remove(fakeMutableProperties.keys.random())
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(expectedMutableProperties)
    }

    @Test
    fun must_useImmutableProperties_when_setAccountProperties_addExtraAttributes() {
        // Given
        val fakeMutableProperties = forge.exhaustiveAttributes()
        val expectedMutableProperties = fakeMutableProperties.toMap()
        testedSdkCore?.setAccountInfo(fakeAccountId, fakeAccountName, fakeMutableProperties)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeMutableProperties[forge.anAlphabeticalString()] = forge.anAlphabeticalString()
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(expectedMutableProperties)
    }

    @Test
    fun must_useImmutableProperties_when_addAccountExtraInfo_attributesValuesModified() {
        // Given
        val fakeExtraProperties = forge.exhaustiveAttributes()
        val expectedExtraProperties = fakeExtraProperties.toMap()
        testedSdkCore?.setAccountInfo(fakeAccountId, fakeAccountName, fakeAccountExtraInfo)
        testedSdkCore?.addAccountExtraInfo(fakeExtraProperties)

        // When
        fakeExtraProperties.keys.forEach { key ->
            fakeExtraProperties[key] = forge.anAlphabeticalString()
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(fakeAccountExtraInfo + expectedExtraProperties)
    }

    @Test
    fun must_useImmutableProperties_when_addAccountExtraInfo_attributesValuesRemoved() {
        // Given
        val fakeExtraProperties = forge.exhaustiveAttributes()
        val expectedExtraProperties = fakeExtraProperties.toMap()
        testedSdkCore?.setAccountInfo(fakeAccountId, fakeAccountName, fakeAccountExtraInfo)
        testedSdkCore?.addAccountExtraInfo(fakeExtraProperties)

        // When
        repeat(forge.anInt(1, fakeExtraProperties.size / 2)) {
            fakeExtraProperties.remove(fakeExtraProperties.keys.random())
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(fakeAccountExtraInfo + expectedExtraProperties)
    }

    @Test
    fun must_useImmutableProperties_when_addAccountExtraInfo_addExtraAttributes() {
        // Given
        val fakeExtraProperties = forge.exhaustiveAttributes()
        val expectedExtraProperties = fakeExtraProperties.toMap()
        testedSdkCore?.setAccountInfo(fakeAccountId, fakeAccountName, fakeAccountExtraInfo)
        testedSdkCore?.addAccountExtraInfo(fakeExtraProperties)

        // When
        repeat(forge.anInt(1, 10)) {
            fakeExtraProperties[forge.anAlphabeticalString()] = forge.anAlphabeticalString()
        }

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(fakeAccountExtraInfo + expectedExtraProperties)
    }

    @Test
    fun must_resetAccountInfo_when_setAccountInfoCalledSecondTime() {
        // Given
        testedSdkCore?.setAccountInfo(fakeAccountId, fakeAccountName, fakeAccountExtraInfo)
        val expectedAccountExtraProperties = forge.exhaustiveAttributes()
        testedSdkCore?.addAccountExtraInfo(expectedAccountExtraProperties)
        val fakeAccountId2 = forge.anAlphabeticalString()
        val fakeAccountName2 = forge.anAlphabeticalString()
        val fakeAccountExtraInfo2 = forge.exhaustiveAttributes()

        // When
        testedSdkCore?.setAccountInfo(fakeAccountId2, fakeAccountName2, fakeAccountExtraInfo2)

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId2)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName2)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(fakeAccountExtraInfo2)
    }

    @Test
    fun must_resetAccountInfo_when_setAccountInfoCalled_afterAddAccountExtraInfo() {
        // Given
        val expectedAccountExtraProperties = forge.exhaustiveAttributes()
        testedSdkCore?.addAccountExtraInfo(expectedAccountExtraProperties)

        // When
        testedSdkCore?.setAccountInfo(fakeAccountId, fakeAccountName, fakeAccountExtraInfo)

        // Then
        val countDownLatch = CountDownLatch(1)
        var readAccountInfo: AccountInfo? = null
        featureSdkCore?.getFeature(stubFeature.name)?.withWriteContext { datadogContext, _ ->
            readAccountInfo = datadogContext.accountInfo
            countDownLatch.countDown()
        }
        countDownLatch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)
        assertThat(readAccountInfo?.id).isEqualTo(fakeAccountId)
        assertThat(readAccountInfo?.name).isEqualTo(fakeAccountName)
        assertThat(readAccountInfo?.extraInfo)
            .containsExactlyInAnyOrderEntriesOf(fakeAccountExtraInfo)
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
