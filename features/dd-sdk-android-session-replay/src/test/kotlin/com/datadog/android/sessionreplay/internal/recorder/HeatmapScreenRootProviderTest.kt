/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.sessionreplay.SessionReplayHeatmapScreenRootProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class HeatmapScreenRootProviderTest {
    @StringForgery
    lateinit var fakePackage: String

    @StringForgery
    lateinit var fakeUrl: String

    @StringForgery
    lateinit var fakeScreenKey: String

    private lateinit var testedResolver: HeatmapIdentifierResolver
    private lateinit var mockProvider: SessionReplayHeatmapScreenRootProvider
    private lateinit var mockRegistry: HeatmapIdentifierRegistry
    private lateinit var mockInternalLogger: InternalLogger
    private lateinit var mockRoot: View

    @BeforeEach
    fun setUp() {
        mockProvider = mock()
        mockRegistry = mock()
        mockInternalLogger = mock()
        mockRoot = mock {
            on { id }.thenReturn(View.NO_ID)
            on { isClickable }.thenReturn(true)
            on { visibility }.thenReturn(View.VISIBLE)
        }
        testedResolver = HeatmapIdentifierResolver(
            fakePackage,
            mockRegistry,
            mockInternalLogger,
            mockProvider
        )
    }

    @Test
    fun `M reset identity path from integration W resolveIdentity()`() {
        // Given
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn(fakeScreenKey)
        val before = testedResolver.beginTraversal(fakeUrl)
        val oldRoot = before.resolveIdentity(mockRoot, listOf("old-ancestor"), 0)
        before.publish()
        val after = testedResolver.beginTraversal(fakeUrl)

        // When
        val result = after.resolveIdentity(mockRoot, listOf("new-ancestor"), 8)

        // Then
        assertThat(result.viewPath).containsExactly("screen-root:$fakeScreenKey")
        assertThat(result.identifier).isNotNull().isEqualTo(oldRoot.identifier)
    }

    @Test
    fun `M preserve native hierarchy W resolveIdentity() {no provider}`() {
        // Given
        testedResolver = HeatmapIdentifierResolver(fakePackage, mockRegistry, mockInternalLogger)
        val context = testedResolver.beginTraversal(fakeUrl)

        // When
        val result = context.resolveIdentity(mockRoot, listOf("ancestor"), 2)

        // Then
        assertThat(result.viewPath).containsExactly("ancestor", "cls:${mockRoot.javaClass.name}#2")
        assertThat(result.isPathValid).isTrue()
        verifyNoInteractions(mockProvider, mockInternalLogger)
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun `M retain ordinary hierarchy W resolveIdentity() {no integration boundary}`(fakeKey: String?) {
        // Given
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn(fakeKey)
        val context = testedResolver.beginTraversal(fakeUrl)

        // When
        val result = context.resolveIdentity(mockRoot, listOf("ancestor"), 2)

        // Then
        assertThat(result.viewPath).containsExactly("ancestor", "cls:${mockRoot.javaClass.name}#2")
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun `M retain valid root path W resolveIdentity() {no integration boundary}`(fakeKey: String?) {
        // Given
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn(fakeKey)
        val context = testedResolver.beginTraversal(fakeUrl)

        // When
        val result = context.resolveIdentity(mockRoot, emptyList(), 0)

        // Then
        assertThat(result.viewPath).containsExactly("cls:${mockRoot.javaClass.name}#0")
        assertThat(result.isPathValid).isTrue()
        assertThat(result.identifier).isNotNull()
    }

    @Test
    fun `M invalidate cache after integration key changes W resolveIdentity()`(@StringForgery fakeOtherKey: String) {
        // Given
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn(fakeScreenKey)
        val before = testedResolver.beginTraversal(fakeUrl)
        val oldRoot = before.resolveIdentity(mockRoot, emptyList(), 0)
        before.publish()
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn("other:$fakeOtherKey")
        val after = testedResolver.beginTraversal(fakeUrl)

        // When
        val result = after.resolveIdentity(mockRoot, emptyList(), 0)

        // Then
        assertThat(result.viewPath).containsExactly("screen-root:other:$fakeOtherKey")
        assertThat(result.identifier).isNotNull().isNotEqualTo(oldRoot.identifier)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M isolate provider failure and remove stale identifiers W resolveIdentity() and publish()`(
        fakeIsError: Boolean,
        @StringForgery fakeMessage: String
    ) {
        // Given
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn(fakeScreenKey)
        val before = testedResolver.beginTraversal(fakeUrl)
        before.resolveIdentity(mockRoot, emptyList(), 0)
        before.publish()
        val fakeFailure = if (fakeIsError) AssertionError(fakeMessage) else IllegalStateException(fakeMessage)
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenThrow(fakeFailure)
        val context = testedResolver.beginTraversal(fakeUrl)

        // When
        val result = assertDoesNotThrow<HeatmapIdentifierResolver.HeatmapIdentity> {
            context.resolveIdentity(mockRoot, emptyList(), 0)
        }
        context.publish()

        // Then
        assertThat(result.isPathValid).isFalse()
        assertThat(result.viewPath).isEmpty()
        assertThat(result.identifier).isNull()
        assertThat(context.entries).isEmpty()
        verify(mockRegistry).setHeatmapIdentifiers(emptyMap(), fakeUrl)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Failed to resolve heatmap screen root. Heatmap identifiers are omitted for the affected subtree.",
            throwable = fakeFailure,
            onlyOnce = true
        )
    }

    @Test
    fun `M recover after a provider failure W resolveIdentity() and publish()`(@StringForgery fakeMessage: String) {
        // Given
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn(fakeScreenKey)
        val before = testedResolver.beginTraversal(fakeUrl)
        val original = before.resolveIdentity(mockRoot, emptyList(), 0)
        before.publish()
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot))
            .thenThrow(IllegalStateException(fakeMessage))
            .thenReturn(fakeScreenKey)

        // When
        val failureContext = testedResolver.beginTraversal(fakeUrl)
        failureContext.resolveIdentity(mockRoot, emptyList(), 0)
        failureContext.publish()
        val recoveryContext = testedResolver.beginTraversal(fakeUrl)
        val recovered = recoveryContext.resolveIdentity(mockRoot, emptyList(), 0)
        recoveryContext.publish()

        // Then
        assertThat(recovered.isPathValid).isTrue()
        assertThat(recovered.identifier).isNotNull().isEqualTo(original.identifier)
        assertThat(recoveryContext.hasProviderFailure).isFalse()
    }
}
