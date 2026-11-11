/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.sessionreplay.SessionReplayHeatmapScreenRootProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class HeatmapScreenRootResolverTest {

    @StringForgery
    lateinit var fakePackage: String

    @StringForgery
    lateinit var fakeUrl: String

    @StringForgery
    lateinit var fakeScreenKey: String

    private lateinit var testedResolver: HeatmapIdentifierResolver
    private lateinit var mockProvider: SessionReplayHeatmapScreenRootProvider
    private lateinit var mockRoot: View
    private lateinit var mockTarget: View

    @BeforeEach
    fun setUp() {
        mockProvider = mock()
        testedResolver =
            HeatmapIdentifierResolver(
                fakePackage,
                mock<HeatmapIdentifierRegistry>(),
                mock<InternalLogger>(),
                mockProvider
            )
        mockRoot = mock()
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn(fakeScreenKey)
        mockTarget = mock {
            whenever(it.id).thenReturn(View.NO_ID)
            whenever(it.isClickable).thenReturn(true)
            whenever(it.visibility).thenReturn(View.VISIBLE)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M reset path at screen root W resolveIdentity()`(fakeClickable: Boolean) {
        // Given
        val context = testedResolver.beginTraversal(fakeUrl)
        whenever(mockRoot.isClickable).thenReturn(fakeClickable)
        whenever(mockRoot.visibility).thenReturn(View.VISIBLE)

        // When
        val result = context.resolveIdentity(mockRoot, listOf("window-ancestor"), 8)

        // Then
        assertThat(result.viewPath).containsExactly("screen-root:$fakeScreenKey")
        val expectedIdentifier = if (fakeClickable) {
            HeatmapIdentifier.create(listOf("screen-root:$fakeScreenKey"), fakeUrl, fakePackage)
        } else {
            null
        }
        assertThat(result.identifier).isEqualTo(expectedIdentifier)
    }

    @Test
    fun `M preserve permanent identity after ancestor movement W resolveIdentity()`(
        @StringForgery fakeAncestor: String,
        @StringForgery fakeOtherAncestor: String
    ) {
        // Given
        val before = testedResolver.beginTraversal(fakeUrl)
        val oldRoot = before.resolveIdentity(mockRoot, listOf(fakeAncestor), 0)
        val oldTarget = before.resolveIdentity(mockTarget, oldRoot.viewPath, 0)
        before.publish()
        val after = testedResolver.beginTraversal(fakeUrl)

        // When
        val newRoot = after.resolveIdentity(mockRoot, listOf(fakeOtherAncestor), 3)
        val newTarget = after.resolveIdentity(mockTarget, newRoot.viewPath, 0)

        // Then
        assertThat(newTarget.identifier).isNotNull().isEqualTo(oldTarget.identifier)
        assertThat(newTarget.viewPath).containsExactly(
            "screen-root:$fakeScreenKey",
            "cls:${mockTarget.javaClass.name}#0"
        )
    }

    @Test
    fun `M distinguish retained roots under same current URL W resolveIdentity()`(@StringForgery fakeOtherKey: String) {
        // Given
        val context = testedResolver.beginTraversal(fakeUrl)
        val firstRoot = context.resolveIdentity(mockRoot, emptyList(), 0)
        val firstTarget = context.resolveIdentity(mockTarget, firstRoot.viewPath, 0)
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn("other:$fakeOtherKey")

        // When
        val otherRoot = context.resolveIdentity(mockRoot, emptyList(), 1)
        val otherTarget = context.resolveIdentity(mockTarget, otherRoot.viewPath, 0)

        // Then
        assertThat(otherTarget.identifier).isNotNull().isNotEqualTo(firstTarget.identifier)
    }

    @Test
    fun `M preserve indices below screen root W resolveIdentity()`() {
        // Given
        val context = testedResolver.beginTraversal(fakeUrl)
        val root = context.resolveIdentity(mockRoot, emptyList(), 0)

        // When
        val first = context.resolveIdentity(mockTarget, root.viewPath, 0)
        val second = context.resolveIdentity(mockTarget, root.viewPath, 1)

        // Then
        assertThat(first.identifier).isNotNull().isNotEqualTo(second.identifier)
        assertThat(second.viewPath.last()).endsWith("#1")
    }

    @Test
    fun `M prefer nearest nested root W resolveIdentity()`(@StringForgery fakeAncestor: String) {
        // Given
        val context = testedResolver.beginTraversal(fakeUrl)
        val outer = context.resolveIdentity(mockRoot, listOf(fakeAncestor), 4)
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn("nested:$fakeScreenKey")

        // When
        val inner = context.resolveIdentity(mockRoot, outer.viewPath, 2)
        val target = context.resolveIdentity(mockTarget, inner.viewPath, 0)

        // Then
        assertThat(target.viewPath).containsExactly(
            "screen-root:nested:$fakeScreenKey",
            "cls:${mockTarget.javaClass.name}#0"
        )
    }

    @Test
    fun `M invalidate cached path when provider key removed W resolveIdentity()`(@StringForgery fakeAncestor: String) {
        // Given
        val before = testedResolver.beginTraversal(fakeUrl)
        val root = before.resolveIdentity(mockRoot, listOf(fakeAncestor), 2)
        val anchored = before.resolveIdentity(mockTarget, root.viewPath, 0)
        before.publish()
        whenever(mockProvider.getHeatmapScreenRootKey(mockRoot)).thenReturn(null)
        whenever(mockRoot.id).thenReturn(View.NO_ID)
        val after = testedResolver.beginTraversal(fakeUrl)

        // When
        val restoredRoot = after.resolveIdentity(mockRoot, listOf(fakeAncestor), 2)
        val restored = after.resolveIdentity(mockTarget, restoredRoot.viewPath, 0)

        // Then
        val expectedPath =
            listOf(fakeAncestor, "cls:${mockRoot.javaClass.name}#2", "cls:${mockTarget.javaClass.name}#0")
        assertThat(restored.viewPath).isEqualTo(expectedPath)
        assertThat(restored.identifier).isEqualTo(HeatmapIdentifier.create(expectedPath, fakeUrl, fakePackage))
            .isNotEqualTo(anchored.identifier)
    }
}
