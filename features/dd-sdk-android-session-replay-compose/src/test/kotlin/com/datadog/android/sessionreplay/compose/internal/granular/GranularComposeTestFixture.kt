/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.internal.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal abstract class GranularComposeTestFixture {
    protected val mockSemanticsUtils: SemanticsUtils = mock()
    protected val mockReflectionUtils: ReflectionUtils = mock()
    protected val mockLogger: InternalLogger = mock()
    protected val mockHost: View = mock()
    protected val stubIdentityFactory: CompositionIdentityFactory = mock()
    protected val fakeHostIdentity: CapturedIdentity = mock()
    protected val testedGate = GranularComposeCompatibilityGate()
    protected val testedDecomposer = GranularComposeDecomposer(
        semanticsUtils = mockSemanticsUtils,
        reflectionUtils = mockReflectionUtils,
        compatibilityGate = testedGate
    )
    protected lateinit var forge: Forge

    @BeforeEach
    fun setUp(forge: Forge) {
        this.forge = forge
        whenever(stubIdentityFactory.composeNode(any(), any())).thenAnswer { mock<CapturedIdentity>() }
        whenever(stubIdentityFactory.shapeWireframe(any())).thenAnswer { mock<CapturedIdentity>() }
        whenever(stubIdentityFactory.textWireframe(any())).thenAnswer { mock<CapturedIdentity>() }
        whenever(stubIdentityFactory.placeholderWireframe(any())).thenAnswer { mock<CapturedIdentity>() }
    }

    protected fun node(
        children: List<SemanticsNode> = emptyList(),
        bounds: GlobalBounds = GlobalBounds(10, 20, 30, 40),
        input: Boolean = false
    ): SemanticsNode {
        val config = SemanticsConfiguration()
        if (input) config[SemanticsProperties.EditableText] = AnnotatedString(forge.anAlphabeticalString())
        val stubLayout: LayoutInfo = mock {
            on { isAttached } doReturn true
            on { isPlaced } doReturn true
            on { getModifierInfo() } doReturn emptyList()
        }
        val fakeId = forge.anInt()
        val node: SemanticsNode = mock {
            on { id } doReturn fakeId
            on { this.children } doReturn children
            on { layoutInfo } doReturn stubLayout
            on { this.config } doReturn config
            on { boundsInRoot } doReturn Rect(10f, 20f, 40f, 60f)
        }
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(bounds)
        whenever(mockSemanticsUtils.resolveBackgroundColor(node)).thenReturn(null)
        return node
    }

    protected fun scene(vararg children: SemanticsNode): SemanticsNode {
        val root = node(children.toList())
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockHost)).thenReturn(root)
        return root
    }

    protected fun request(privacy: TextAndInputPrivacy = TextAndInputPrivacy.MASK_ALL) =
        CompositionHostDecomposeRequest(
            identityFactory = stubIdentityFactory,
            hostIdentity = fakeHostIdentity,
            screenDensity = 2f,
            hostBounds = CapturedBounds(0, 0, 1000, 1000),
            textAndInputPrivacy = privacy,
            internalLogger = mockLogger,
            shouldContinue = { true },
            nativeViewHandoff = { _, _ -> false }
        )

    protected fun text(node: SemanticsNode, text: String): TextLayoutInfo {
        val layout = TextLayoutInfo(text, 0u, forge.aLong(min = 1L, max = 64L), null)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any())).thenReturn(layout)
        return layout
    }
}
