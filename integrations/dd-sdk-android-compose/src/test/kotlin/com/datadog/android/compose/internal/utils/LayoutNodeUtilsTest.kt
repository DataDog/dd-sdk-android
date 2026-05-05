/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.compose.internal.utils

import androidx.compose.foundation.ClickableElement
import androidx.compose.foundation.CombinedClickableElement
import androidx.compose.foundation.ScrollingLayoutElement
import androidx.compose.foundation.gestures.ScrollableElement
import androidx.compose.foundation.selection.SelectableElement
import androidx.compose.foundation.selection.ToggleableElement
import androidx.compose.foundation.selection.TriStateToggleableElement
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNodeLayoutDelegate
import androidx.compose.ui.node.NodeCoordinator
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.compose.DatadogSemanticsPropertyKey
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.stream.Stream

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LayoutNodeUtilsTest {

    private val testedLayoutNodeUtils = spy(LayoutNodeUtils())

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        val registry: Any = Datadog::class.java.getStaticValue("registry")
        val instances: MutableMap<String, SdkCore> = registry.getFieldValue("instances")
        instances += DEFAULT_INSTANCE_NAME to mockSdkCore
    }

    @AfterEach
    fun `tear down`() {
        Datadog.stopInstance(DEFAULT_INSTANCE_NAME)
    }

    // region Legacy Compose (SemanticsModifier)

    @Test
    fun `M return correct target node W call resolveLayoutNode {legacy compose}`(
        forge: Forge
    ) {
        // Given
        val fakeTagName = forge.aString()
        val isClickable = forge.aBool()
        val isScrollable = forge.aBool()
        val mockNode = mockLegacyLayoutNode(
            fakeTagName,
            isClickable,
            isScrollable
        )

        // When
        val result = testedLayoutNodeUtils.resolveLayoutNode(mockNode)

        // Then
        assertThat(result).isEqualTo(
            LayoutNodeUtils.TargetNode(
                tag = fakeTagName,
                isScrollable = isScrollable,
                isClickable = isClickable
            )
        )
    }

    // endregion

    // region Clickable Modifier Elements

    @ParameterizedTest
    @MethodSource("clickableModifierElements")
    fun `M return clickable target node W resolveLayoutNode {clickable modifier element}`(
        testCase: ModifierTestCase,
        @StringForgery fakeTagName: String
    ) {
        // Given
        val mockNode = mockLayoutNodeWithModifiers(fakeTagName, testCase.modifier)

        // When
        val result = testedLayoutNodeUtils.resolveLayoutNode(mockNode)

        // Then
        checkNotNull(result)
        assertThat(result.tag).isEqualTo(fakeTagName)
        assertThat(result.isClickable).isTrue()
        assertThat(result.isScrollable).isFalse()
    }

    // endregion

    // region Scrollable Modifier Elements

    @ParameterizedTest
    @MethodSource("scrollableModifierElements")
    fun `M return scrollable target node W resolveLayoutNode {scrollable modifier element}`(
        testCase: ModifierTestCase,
        @StringForgery fakeTagName: String
    ) {
        // Given
        val mockNode = mockLayoutNodeWithModifiers(fakeTagName, testCase.modifier)

        // When
        val result = testedLayoutNodeUtils.resolveLayoutNode(mockNode)

        // Then
        checkNotNull(result)
        assertThat(result.tag).isEqualTo(fakeTagName)
        assertThat(result.isClickable).isFalse()
        assertThat(result.isScrollable).isTrue()
    }

    // endregion

    // region Error Handling

    @Test
    fun `M log WARN to telemetry W resolveLayoutNode() {getModifierInfo throws}`() {
        // Given
        val exception = RuntimeException("test exception")
        val mockNode = mock<LayoutNode>()
        whenever(mockNode.getModifierInfo()).thenThrow(exception)

        // When
        val result = testedLayoutNodeUtils.resolveLayoutNode(mockNode)

        // Then
        assertThat(result).isNull()
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY)),
                capture(),
                same(exception),
                eq(true),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo("LayoutNodeUtils execution failure in resolveLayoutNode.")
        }
    }

    @Test
    fun `M log WARN to telemetry W getLayoutNodeBoundsInWindow() {layoutDelegate access throws}`() {
        // Given
        // mock<LayoutNode>() returns null for unstubbed layoutDelegate, causing NPE when chained
        val mockNode = mock<LayoutNode>()

        // When
        val result = testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode)

        // Then
        assertThat(result).isNull()
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY)),
                capture(),
                isA(NullPointerException::class.java),
                eq(true),
                eq(null)
            )
            assertThat(firstValue())
                .isEqualTo("LayoutNodeUtils execution failure in getLayoutNodeBoundsInWindow.")
        }
    }

    // endregion

    // region Reflection Fallback State Machine

    @Test
    fun `M keep skipping reflection path W getLayoutNodeBoundsInWindow() {repeated, internal succeeds}`() {
        // Given
        val mockNode = mockLayoutNodeWithValidInternalPath()

        // When
        repeat(5) { testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode) }

        // Then
        verify(testedLayoutNodeUtils, times(5)).getLayoutNodeBoundsInWindowInternal(mockNode)
        verify(testedLayoutNodeUtils, never()).getLayoutNodeBoundsInWindowReflection(any<LayoutNode>())
    }

    @Test
    fun `M use reflection path only W getLayoutNodeBoundsInWindow() {reflection already activated}`() {
        // Given
        testedLayoutNodeUtils.methodResolver().state = LayoutNodeUtils.MethodResolver.State.MANGLING_FAILED
        val mockNode = mock<LayoutNode>()

        // When
        repeat(3) { testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode) }

        // Then
        verify(testedLayoutNodeUtils, times(3)).getLayoutNodeBoundsInWindowReflection(mockNode)
        verify(testedLayoutNodeUtils, never()).getLayoutNodeBoundsInWindowInternal(any<LayoutNode>())
    }

    @Test
    fun `M activate reflection without giving up W getBoundsInWindow() {internal fails, reflection ok}`() {
        // Given
        // mock<LayoutNode>() returns null for layoutDelegate, so internal path fails with NPE
        // swallowed by runSafe; reflection path then runs against the mock's JVM class
        // (inherits LayoutNode accessors), so method lookup succeeds and state stays MANGLING_FAILED.
        val mockNode = mock<LayoutNode>()

        // When
        testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode)

        // Then
        val resolver = testedLayoutNodeUtils.methodResolver()
        assertThat(resolver.state).isEqualTo(LayoutNodeUtils.MethodResolver.State.MANGLING_FAILED)
        assertThat(resolver.classPrefixMethodsCache)
            .containsKey(mockNode.javaClass)
        assertThat(resolver.classPrefixMethodsCache[mockNode.javaClass])
            .doesNotContainValue(null)
    }

    @Test
    fun `M not grow suffix cache W getLayoutNodeBoundsInWindow() {repeated reflection calls}`() {
        // Given
        // Force reflection path so all three levels (LayoutNode, LayoutNodeLayoutDelegate,
        // NodeCoordinator) are traversed and cached — the maximum cache size for this chain.
        testedLayoutNodeUtils.methodResolver().state = LayoutNodeUtils.MethodResolver.State.MANGLING_FAILED
        val mockNode = mockLayoutNodeWithValidInternalPath()
        testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode)
        val cache = testedLayoutNodeUtils.methodResolver().classPrefixMethodsCache
        val sizeAfterFirstCall = cache.size

        // When
        repeat(10) { testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode) }

        // Then
        assertThat(sizeAfterFirstCall).isEqualTo(EXPECTED_REFLECTION_CHAIN_LENGTH)
        assertThat(cache).hasSize(sizeAfterFirstCall)
    }

    @Test
    fun `M transition to REFLECTION_FAILED W findMethod() {no method found}`(
        @StringForgery fakePrefix: String
    ) {
        // Given
        val methodResolver = LayoutNodeUtils.MethodResolver()

        // When
        val result = methodResolver.findMethod(Any::class.java, fakePrefix)

        // Then
        assertThat(result).isNull()
        assertThat(methodResolver.state).isEqualTo(LayoutNodeUtils.MethodResolver.State.REFLECTION_FAILED)
    }

    @Test
    fun `M use internal path only W getLayoutNodeBoundsInWindow() {reflection gave up}`() {
        // Given
        testedLayoutNodeUtils.methodResolver().state = LayoutNodeUtils.MethodResolver.State.REFLECTION_FAILED
        val mockNode = mock<LayoutNode>()

        // When
        repeat(3) { testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode) }

        // Then
        verify(testedLayoutNodeUtils, times(3)).getLayoutNodeBoundsInWindowInternal(mockNode)
        verify(testedLayoutNodeUtils, never()).getLayoutNodeBoundsInWindowReflection(any<LayoutNode>())
    }

    @Test
    fun `M stay on internal W getLayoutNodeBoundsInWindow() {first call resolved via internal, repeated}`() {
        // Given
        // First call resolves through the internal path — state stays UNKNOWN, cache stays empty.
        val mockNode = mockLayoutNodeWithValidInternalPath()
        testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode)

        // When
        repeat(10) { testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode) }

        // Then
        // Subsequent calls never fall back to reflection.
        verify(testedLayoutNodeUtils, times(11)).getLayoutNodeBoundsInWindowInternal(mockNode)
        verify(testedLayoutNodeUtils, never()).getLayoutNodeBoundsInWindowReflection(any<LayoutNode>())
        val resolver = testedLayoutNodeUtils.methodResolver()
        assertThat(resolver.state).isEqualTo(LayoutNodeUtils.MethodResolver.State.UNKNOWN)
        assertThat(resolver.classPrefixMethodsCache).isEmpty()
    }

    @Test
    fun `M stay on reflection and hit cache W getBoundsInWindow() {first call resolved via reflection, repeated}`() {
        // Given
        // First call: internal fails (mock<LayoutNode> returns null layoutDelegate → NPE swallowed
        // by runSafe), so getLayoutNodeBoundsInWindow falls back to reflection. Reflection
        // resolves the mangled accessors via the mock's JVM class and caches them.
        // State transitions to MANGLING_FAILED.
        val mockNode = mock<LayoutNode>()
        testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode)
        val cacheAfterFirstCall = testedLayoutNodeUtils.methodResolver().classPrefixMethodsCache.toMap()

        // When
        repeat(10) { testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode) }

        // Then
        // State=MANGLING_FAILED now routes straight to reflection — internal is not retried.
        // Reflection wrapper keeps being invoked, but findMethod short-circuits on the cached
        // entries, so the cache never grows.
        verify(testedLayoutNodeUtils, times(1)).getLayoutNodeBoundsInWindowInternal(mockNode)
        verify(testedLayoutNodeUtils, times(11)).getLayoutNodeBoundsInWindowReflection(mockNode)
        assertThat(testedLayoutNodeUtils.methodResolver().classPrefixMethodsCache)
            .isEqualTo(cacheAfterFirstCall)
    }

    @Test
    fun `M not downgrade state W MethodResolver#state {REFLECTION_FAILED cannot be lowered}`() {
        // Given
        val methodResolver = LayoutNodeUtils.MethodResolver()
        methodResolver.state = LayoutNodeUtils.MethodResolver.State.REFLECTION_FAILED

        // When
        methodResolver.state = LayoutNodeUtils.MethodResolver.State.UNKNOWN
        methodResolver.state = LayoutNodeUtils.MethodResolver.State.MANGLING_FAILED

        // Then
        assertThat(methodResolver.state)
            .isEqualTo(LayoutNodeUtils.MethodResolver.State.REFLECTION_FAILED)
    }

    @Test
    fun `M not downgrade state W MethodResolver#state {MANGLING_FAILED cannot revert to UNKNOWN}`() {
        // Given
        val methodResolver = LayoutNodeUtils.MethodResolver()
        methodResolver.state = LayoutNodeUtils.MethodResolver.State.MANGLING_FAILED

        // When
        methodResolver.state = LayoutNodeUtils.MethodResolver.State.UNKNOWN

        // Then
        assertThat(methodResolver.state)
            .isEqualTo(LayoutNodeUtils.MethodResolver.State.MANGLING_FAILED)
    }

    @Test
    fun `M keep state UNKNOWN W getLayoutNodeBoundsInWindow() {internal path succeeds}`() {
        // Given
        val mockNode = mockLayoutNodeWithValidInternalPath()

        // When
        testedLayoutNodeUtils.getLayoutNodeBoundsInWindow(mockNode)

        // Then
        assertThat(testedLayoutNodeUtils.methodResolver().state)
            .isEqualTo(LayoutNodeUtils.MethodResolver.State.UNKNOWN)
    }

    @Test
    fun `M prefer plain name W findMethod() {plain and mangled variants coexist}`() {
        // Given
        // SuffixFixture declares foo(), foo$ui_release() and foo$ui() — all three suffixes
        // in SUPPORTED_MANGLING_SUFFIXES. Plain is first in the list, so it must win.
        val methodResolver = LayoutNodeUtils.MethodResolver()

        // When
        val result = methodResolver.findMethod(SuffixFixture::class.java, "foo")

        // Then
        assertThat(result?.name).isEqualTo("foo")
    }

    @Test
    fun `M prefer ui_release over ui W findMethod() {no plain variant}`() {
        // Given
        // SuffixFixture declares bar$ui_release() and bar$ui() but no plain bar().
        // $ui_release precedes $ui in SUPPORTED_MANGLING_SUFFIXES, so it must win.
        val methodResolver = LayoutNodeUtils.MethodResolver()

        // When
        val result = methodResolver.findMethod(SuffixFixture::class.java, "bar")

        // Then
        assertThat(result?.name).isEqualTo("bar\$ui_release")
    }

    @Test
    fun `M resolve ui suffix W findMethod() {only ui variant exists}`() {
        // Given
        // SuffixFixture declares only bazz$ui() — the last fallback in SUPPORTED_MANGLING_SUFFIXES.
        val methodResolver = LayoutNodeUtils.MethodResolver()

        // When
        val result = methodResolver.findMethod(SuffixFixture::class.java, "bazz")

        // Then
        assertThat(result?.name).isEqualTo("bazz\$ui")
    }

    @Test
    fun `M return cached value W findMethod() {prefix already resolved}`() {
        // Given
        // Realistic context: findMethod is always called from the reflection flow which has
        // already transitioned state to MANGLING_FAILED. Seed the cache with a pre-resolved
        // method for a prefix ("bogus") that would NOT resolve via reflection — if findMethod
        // ignored the cache and re-searched, it would return null (and downgrade to
        // REFLECTION_FAILED).
        val methodResolver = LayoutNodeUtils.MethodResolver()
        methodResolver.state = LayoutNodeUtils.MethodResolver.State.MANGLING_FAILED
        val knownMethod = SuffixFixture::class.java.getMethod("foo")
        methodResolver.classPrefixMethodsCache[SuffixFixture::class.java] =
            mutableMapOf("bogus" to knownMethod)

        // When
        val result = methodResolver.findMethod(SuffixFixture::class.java, "bogus")

        // Then
        assertThat(result).isSameAs(knownMethod)
        assertThat(methodResolver.state)
            .isEqualTo(LayoutNodeUtils.MethodResolver.State.MANGLING_FAILED)
    }

    @Test
    fun `M return cached null and flag REFLECTION_FAILED W findMethod() {prefix previously unresolved}`() {
        // Given
        // A null cached value means "we already tried and failed" — must not re-run searchManglings,
        // even when the method actually exists on the class. State still transitions to
        // REFLECTION_FAILED because findMethod's post-condition fires on any null result.
        val methodResolver = LayoutNodeUtils.MethodResolver()
        methodResolver.state = LayoutNodeUtils.MethodResolver.State.MANGLING_FAILED
        methodResolver.classPrefixMethodsCache[SuffixFixture::class.java] =
            mutableMapOf("foo" to null)

        // When
        val result = methodResolver.findMethod(SuffixFixture::class.java, "foo")

        // Then
        assertThat(result).isNull()
        assertThat(methodResolver.state)
            .isEqualTo(LayoutNodeUtils.MethodResolver.State.REFLECTION_FAILED)
    }

    // endregion

    // region Private

    private fun mockLayoutNodeWithModifiers(
        tagName: String,
        vararg modifiers: Modifier.Element
    ): LayoutNode {
        val mockLayoutCoordinates = mock<LayoutCoordinates>()
        val mockSemanticsConfiguration = mock<SemanticsConfiguration> {
            whenever(it.getOrNull(DatadogSemanticsPropertyKey)).thenReturn(tagName)
        }

        val mockSemanticsModifier = mock<SemanticsModifier> {
            whenever(it.semanticsConfiguration).thenReturn(mockSemanticsConfiguration)
        }

        val modifierInfoList = mutableListOf(
            ModifierInfo(mockSemanticsModifier, mockLayoutCoordinates)
        )
        modifiers.forEach { modifier ->
            modifierInfoList.add(ModifierInfo(modifier, mockLayoutCoordinates))
        }

        return mock<LayoutNode> {
            whenever(it.getModifierInfo()) doReturn modifierInfoList
        }
    }

    private fun LayoutNodeUtils.methodResolver(): LayoutNodeUtils.MethodResolver =
        getFieldValue("methodResolver")

    private fun mockLayoutNodeWithValidInternalPath(): LayoutNode {
        // Stub the full internal chain so that
        // node.layoutDelegate.outerCoordinator.coordinates.boundsInWindow() returns a Rect.
        // With parentLayoutCoordinates defaulting to null and localBoundingBoxOf returning
        // Rect.Zero, boundsInWindow early-returns Rect.Zero without further chain access.
        val mockLayoutCoordinates = mock<LayoutCoordinates> {
            whenever(it.localBoundingBoxOf(any(), any())) doReturn Rect.Zero
        }
        val mockOuterCoordinator = mock<NodeCoordinator> {
            whenever(it.coordinates) doReturn mockLayoutCoordinates
        }
        val mockLayoutDelegate = mock<LayoutNodeLayoutDelegate> {
            whenever(it.outerCoordinator) doReturn mockOuterCoordinator
        }
        return mock<LayoutNode> {
            whenever(it.layoutDelegate) doReturn mockLayoutDelegate
        }
    }

    private fun mockLegacyLayoutNode(
        tagName: String,
        isClickable: Boolean = false,
        isScrollable: Boolean = false
    ): LayoutNode {
        val mockLayoutCoordinates = mock<LayoutCoordinates>()
        val mockSemanticsConfiguration = mock<SemanticsConfiguration> {
            whenever(it.contains(SemanticsActions.OnClick)).thenReturn(isClickable)
            whenever(it.contains(SemanticsActions.ScrollBy)).thenReturn(isScrollable)
            whenever(it.getOrNull(DatadogSemanticsPropertyKey)).thenReturn(tagName)
        }

        val mockSemanticsModifier = mock<SemanticsModifier> {
            whenever(it.semanticsConfiguration).thenReturn(mockSemanticsConfiguration)
        }

        val node = mock<LayoutNode> {
            whenever(it.getModifierInfo()) doReturn listOf(
                ModifierInfo(mockSemanticsModifier, mockLayoutCoordinates)
            )
        }
        return node
    }

    // endregion

    data class ModifierTestCase(
        val name: String,
        val modifier: Modifier.Element
    ) {
        override fun toString(): String = name
    }

    companion object {

        /**
         * LayoutNodeUtils sends telemetry with default SDK core instance, so in the test we must
         * register the mocked SDK core with default name from [SdkCoreRegistry.DEFAULT_INSTANCE_NAME].
         */
        private const val DEFAULT_INSTANCE_NAME = "_dd.sdk_core.default"

        // LayoutNode -> LayoutNodeLayoutDelegate -> NodeCoordinator
        private const val EXPECTED_REFLECTION_CHAIN_LENGTH = 3

        @JvmStatic
        fun clickableModifierElements(): Stream<ModifierTestCase> = Stream.of(
            ModifierTestCase("TriStateToggleableElement", TriStateToggleableElement()),
            ModifierTestCase("ToggleableElement", ToggleableElement()),
            ModifierTestCase("ClickableElement", ClickableElement()),
            ModifierTestCase("CombinedClickableElement", CombinedClickableElement()),
            ModifierTestCase("SelectableElement", SelectableElement())
        )

        @JvmStatic
        fun scrollableModifierElements(): Stream<ModifierTestCase> = Stream.of(
            ModifierTestCase("ScrollingLayoutElement", ScrollingLayoutElement()),
            ModifierTestCase("ScrollableElement", ScrollableElement())
        )
    }

    // Method names mirror the JVM-mangled accessors emitted for Kotlin `internal` members
    // in the androidx.compose.ui module. Used to exercise SUPPORTED_MANGLING_SUFFIXES resolution.
    @Suppress("FunctionName", "unused")
    internal class SuffixFixture {
        fun foo() = Unit
        fun `foo$ui_release`() = Unit
        fun `foo$ui`() = Unit
        fun `bar$ui_release`() = Unit
        fun `bar$ui`() = Unit
        fun `bazz$ui`() = Unit
    }
}
