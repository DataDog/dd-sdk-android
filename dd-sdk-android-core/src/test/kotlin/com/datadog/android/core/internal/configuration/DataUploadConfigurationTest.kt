/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.configuration

import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
internal class DataUploadConfigurationTest {

    @Forgery
    lateinit var fakeUploadFrequency: UploadFrequency

    lateinit var testedConfiguration: DataUploadConfiguration

    @BeforeEach
    fun `set up`(forge: Forge) {
        testedConfiguration = DataUploadConfiguration(
            fakeUploadFrequency,
            forge.anInt()
        )
    }

    @Test
    fun `M correctly compute the min delay`() {
        assertThat(testedConfiguration.minDelayMs)
            .isEqualTo(fakeUploadFrequency.baseStepMs * DataUploadConfiguration.MIN_DELAY_FACTOR)
    }

    @Test
    fun `M correctly compute the max delay`() {
        assertThat(testedConfiguration.maxDelayMs)
            .isEqualTo(fakeUploadFrequency.baseStepMs * DataUploadConfiguration.MAX_DELAY_FACTOR)
    }

    @Test
    fun `M correctly compute the default delay`() {
        assertThat(testedConfiguration.defaultDelayMs)
            .isEqualTo(
                fakeUploadFrequency.baseStepMs *
                    DataUploadConfiguration.DEFAULT_DELAY_FACTOR
            )
    }
}
