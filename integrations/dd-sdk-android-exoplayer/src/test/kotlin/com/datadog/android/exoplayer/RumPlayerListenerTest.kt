/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.exoplayer

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.exoplayer.utils.forge.ExoPlayerForgeryConfigurator
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible


@Suppress("DEPRECATION")
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ExoPlayerForgeryConfigurator::class)
class RumPlayerListenerTest {

    lateinit var testedListener: Player.Listener

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeMediaItem: MediaItem

    @BeforeEach
    fun `set up`(forge: Forge) {
        GlobalRumMonitor::class.declaredFunctions.first { it.name == "registerIfAbsent" }.apply {
            isAccessible = true
            call(GlobalRumMonitor::class.objectInstance, mockRumMonitor, mockSdkCore)
        }
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedListener = RumPlayerListener(mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    @Test
    fun `𝕄 send play action 𝕎 onMediaItemTransition() + onPlaybackStateChanged(ENDED)`(
        @IntForgery(0, 4) fakeReason: Int,
        @LongForgery(100L, 250L) duration: Long
    ) {
        // When
        testedListener.onMediaItemTransition(fakeMediaItem, fakeReason)
        Thread.sleep(duration)
        testedListener.onPlaybackStateChanged(Player.STATE_ENDED)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockRumMonitor).addAction(
                eq(RumActionType.MEDIA),
                eq(fakeMediaItem.mediaId),
                capture()
            )
            assertMapContainsMediaItem(lastValue, fakeMediaItem)
            assertThat(lastValue["media.ended"]).isEqualTo(true)
        }
    }

    private fun assertMapContainsMediaItem(lastValue: Map<String, Any?>, fakeMediaItem: MediaItem) {
        assertThat(lastValue["media.id"]).isEqualTo(fakeMediaItem.mediaId)
        assertThat(lastValue["media.url"]).isEqualTo(fakeMediaItem.mediaId)
        assertThat(lastValue["media.type"]).isEqualTo("video")
    }
}