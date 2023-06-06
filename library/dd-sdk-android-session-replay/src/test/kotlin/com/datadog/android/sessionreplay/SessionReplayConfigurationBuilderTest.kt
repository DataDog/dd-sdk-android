/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.DatadogEndpoint
import com.datadog.android.DatadogSite
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(value = ForgeConfigurator::class)
internal class SessionReplayConfigurationBuilderTest {

    lateinit var testedBuilder: SessionReplayConfiguration.Builder

    @BeforeEach
    fun `set up`() {
        testedBuilder = SessionReplayConfiguration.Builder()
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.endpointUrl).isEqualTo(DatadogEndpoint.SESSION_REPLAY_US1)
        assertThat(config.privacy).isEqualTo(SessionReplayPrivacy.MASK_ALL)
        assertThat(config.extensionSupport).isInstanceOf(NoOpExtensionSupport::class.java)
    }

    @Test
    fun `𝕄 build config with custom site 𝕎 useSite() and build()`(
        @Forgery site: DatadogSite
    ) {
        // When
        val config = testedBuilder.useSite(site).build()

        // Then
        assertThat(config.endpointUrl).isEqualTo(site.sessionReplayEndpoint())
    }

    @Test
    fun `𝕄 build config with custom site 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") sessionReplayUrl: String
    ) {
        // When
        val config = testedBuilder.useCustomEndpoint(sessionReplayUrl).build()

        // Then
        assertThat(config.endpointUrl).isEqualTo(sessionReplayUrl)
    }

    @Test
    fun `𝕄 use the given privacy rule 𝕎 setSessionReplayPrivacy`(
        @Forgery fakePrivacy: SessionReplayPrivacy
    ) {
        // When
        val config = testedBuilder.setPrivacy(fakePrivacy).build()

        // Then
        assertThat(config.privacy).isEqualTo(fakePrivacy)
    }

    @Test
    fun `𝕄 use the given extension support 𝕎 addExtensionSupport`() {
        // Given
        val mockExtensionSupport: ExtensionSupport = mock()

        // When
        val config = testedBuilder.addExtensionSupport(mockExtensionSupport).build()

        // Then
        assertThat(config.extensionSupport).isEqualTo(mockExtensionSupport)
    }

    @Test
    fun `M use the given sample rate W sessionReplaySampleRate`(@FloatForgery fakeSampleRate: Float) {
        // When
        val config = testedBuilder.sessionReplaySampleRate(fakeSampleRate).build()

        // Then
        assertThat(config.samplingRate).isEqualTo(fakeSampleRate)
    }
}
