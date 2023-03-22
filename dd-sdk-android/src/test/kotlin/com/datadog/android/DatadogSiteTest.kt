/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogSiteTest {

    @Test
    fun `𝕄 return intake endpoint 𝕎 intakeEndpoint {US1}`() {
        assertThat(DatadogSite.US1.intakeEndpoint).isEqualTo("https://browser-intake-datadoghq.com")
    }

    @Test
    fun `𝕄 return intake endpoint 𝕎 intakeEndpoint {US3}`() {
        assertThat(DatadogSite.US3.intakeEndpoint).isEqualTo("https://browser-intake-us3-datadoghq.com")
    }

    @Test
    fun `𝕄 return intake endpoint 𝕎 intakeEndpoint {US5}`() {
        assertThat(DatadogSite.US5.intakeEndpoint).isEqualTo("https://browser-intake-us5-datadoghq.com")
    }

    @Test
    fun `𝕄 return intake endpoint 𝕎 intakeEndpoint {US1-FED}`() {
        assertThat(DatadogSite.US1_FED.intakeEndpoint).isEqualTo("https://browser-intake-ddog-gov.com")
    }

    @Test
    fun `𝕄 return intake endpoint 𝕎 intakeEndpoint {EU1}`() {
        assertThat(DatadogSite.EU1.intakeEndpoint).isEqualTo("https://browser-intake-datadoghq.eu")
    }

    @Test
    fun `𝕄 return intake endpoint 𝕎 intakeEndpoint {AP1}`() {
        assertThat(DatadogSite.AP1.intakeEndpoint).isEqualTo("https://browser-intake-ap1-datadoghq.com")
    }

    @Test
    fun `𝕄 return intake endpoint 𝕎 intakeEndpoint {STAGING}`() {
        assertThat(DatadogSite.STAGING.intakeEndpoint).isEqualTo("https://browser-intake-datad0g.com")
    }
}
