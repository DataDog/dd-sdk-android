/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.model.MobileSegment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class PendingViewMappingTest {
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
    private val fakeContext = CapturedMappingContext(
        fakeFactory,
        fakeFactory.view(fakeFactory.window("window"), "owner"),
        1f
    )

    @Test
    fun `M wait for mapper return W jobFinished() { synchronous resource callback }`() {
        // Given
        val testedMapping = PendingViewMapping(true)
        val fakeNotifications = AtomicInteger()
        val fakeImage = MobileSegment.Wireframe.ImageWireframe(1L, 0L, 0L, 10L, 10L)
        testedMapping.whenReady { fakeNotifications.incrementAndGet() }
        testedMapping.jobStarted()

        // When / Then
        testedMapping.jobFinished()
        assertThat(fakeNotifications.get()).isZero()
        fakeImage.resourceId = "image"
        testedMapping.mapped(listOf(fakeImage))
        testedMapping.jobFinished()
        assertThat(fakeNotifications.get()).isEqualTo(1)
        val result = testedMapping.map(fakeContext) as CapturedViewMapperResult.Wireframes
        assertThat((result.wireframes.single() as CapturedWireframe.Pixel).resource)
            .isEqualTo(PixelResource.Resolved("image"))
    }

    @Test
    fun `M include appended output W map() { mapper completes on another thread after returning }`() {
        // Given
        val testedMapping = PendingViewMapping(true)
        val fakeWireframes = mutableListOf<MobileSegment.Wireframe>()
        val fakeNotifications = AtomicInteger()
        testedMapping.jobStarted()
        testedMapping.mapped(fakeWireframes)
        testedMapping.whenReady { fakeNotifications.incrementAndGet() }
        assertThat(testedMapping.map(fakeContext)).isEqualTo(CapturedViewMapperResult.None)
        val fakeCallback = Thread {
            fakeWireframes += MobileSegment.Wireframe.ImageWireframe(
                1L,
                0L,
                0L,
                10L,
                10L,
                resourceId = "async-image"
            )
            testedMapping.jobFinished()
        }

        // When
        fakeCallback.start()
        fakeCallback.join(5_000)

        // Then
        assertThat(fakeCallback.isAlive).isFalse()
        assertThat(fakeNotifications.get()).isEqualTo(1)
        val result = testedMapping.map(fakeContext) as CapturedViewMapperResult.Wireframes
        assertThat((result.wireframes.single() as CapturedWireframe.Pixel).resource)
            .isEqualTo(PixelResource.Resolved("async-image"))
    }

    @Test
    fun `M notify once W jobFinished() { concurrent resource callbacks }`() {
        // Given
        val testedMapping = PendingViewMapping(true)
        val fakeNotifications = AtomicInteger()
        val fakeStart = CountDownLatch(1)
        val fakeFinished = CountDownLatch(2)
        testedMapping.jobStarted()
        testedMapping.jobStarted()
        testedMapping.mapped(emptyList())
        testedMapping.whenReady { fakeNotifications.incrementAndGet() }
        val fakeThreads = List(2) {
            Thread {
                try {
                    if (fakeStart.await(5, TimeUnit.SECONDS)) testedMapping.jobFinished()
                } finally {
                    fakeFinished.countDown()
                }
            }
        }

        // When
        fakeThreads.forEach { it.start() }
        fakeStart.countDown()
        assertThat(fakeFinished.await(5, TimeUnit.SECONDS)).isTrue()
        fakeThreads.forEach { it.join() }
        testedMapping.jobFinished()

        // Then
        assertThat(fakeNotifications.get()).isEqualTo(1)
        testedMapping.whenReady { fakeNotifications.incrementAndGet() }
        assertThat(fakeNotifications.get()).isEqualTo(2)
    }

    @Test
    fun `M discard partial output W failed() { resource callbacks still pending }`() {
        // Given
        val testedMapping = PendingViewMapping(true)
        val fakeNotifications = AtomicInteger()
        testedMapping.jobStarted()
        testedMapping.mapped(listOf(MobileSegment.Wireframe.ImageWireframe(1L, 0L, 0L, 10L, 10L)))
        testedMapping.whenReady { fakeNotifications.incrementAndGet() }

        // When
        testedMapping.failed()
        testedMapping.jobFinished()
        testedMapping.jobStarted()
        testedMapping.jobFinished()

        // Then
        assertThat(fakeNotifications.get()).isEqualTo(1)
        val result = testedMapping.map(fakeContext) as CapturedViewMapperResult.Wireframes
        assertThat(result.wireframes).isEmpty()
    }
}
