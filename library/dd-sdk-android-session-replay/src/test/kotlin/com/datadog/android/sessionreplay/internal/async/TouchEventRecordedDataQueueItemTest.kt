/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
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
@ForgeConfiguration(ForgeConfigurator::class)
internal class TouchEventRecordedDataQueueItemTest {

    @Forgery
    lateinit var fakeTouchEventRecordedDataQueueItem: TouchEventRecordedDataQueueItem

    lateinit var testedItem: TouchEventRecordedDataQueueItem

    @BeforeEach
    fun `set up`() {
        testedItem = fakeTouchEventRecordedDataQueueItem
    }

    @Test
    fun `M return false W isValid() { Touch Event with empty touchData }`() {
        // Given
        testedItem = TouchEventRecordedDataQueueItem(
            rumContextData = fakeTouchEventRecordedDataQueueItem.rumContextData,
            touchData = emptyList()
        )

        // Then
        assertThat(testedItem.isValid()).isFalse()
    }

    @Test
    fun `M return true W isValid() { Touch Event with interactions }`() {
        // Then
        assertThat(testedItem.isValid()).isTrue()
    }

    @Test
    fun `M return true W isReady() { Touch Event }`() {
        // Then
        assertThat(testedItem.isReady()).isTrue()
    }
}
