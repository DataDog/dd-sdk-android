/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.android.Datadog
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.integration.tests.forge.factories.ConfigurationCoreForgeryFactory
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.forge.useToolsFactories
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit4.ForgeRule
import fr.xgouchet.elmyr.jvm.useJvmFactories
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InternalProxyTest {

    private lateinit var testedInternalSdkCore: InternalSdkCore

    @Forgery
    lateinit var fakeConfiguration: Configuration

    @get:Rule
    var forge = ForgeRule()
        .useJvmFactories()
        .useToolsFactories()
        .withFactory(ConfigurationCoreForgeryFactory())

    @Before
    fun setUp() {
        testedInternalSdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            fakeConfiguration,
            forge.aValueFrom(TrackingConsent::class.java)
        ) as InternalSdkCore
    }

    @After
    fun tearDown() {
        Datadog.stopInstance()
    }

    // region set version

    @Test
    fun mustSetAppVersion_when_setCustomAppVersion() {
        // Given
        val fakeAppVersion = forge.anAlphabeticalString()
        val internalProxy = Datadog._internalProxy()

        // When
        internalProxy.setCustomAppVersion(fakeAppVersion)

        // Then
        val context = testedInternalSdkCore.getDatadogContext()
        checkNotNull(context)
        assertThat(context.version).isEqualTo(fakeAppVersion)
    }

    // endregion
}
