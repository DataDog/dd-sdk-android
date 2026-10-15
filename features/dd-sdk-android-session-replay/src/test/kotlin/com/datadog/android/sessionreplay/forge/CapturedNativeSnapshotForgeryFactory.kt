/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.internal.composition.CapturedForeground
import com.datadog.android.sessionreplay.internal.composition.CapturedNativeSnapshot
import com.datadog.android.sessionreplay.internal.composition.CapturedNativeView
import com.datadog.android.sessionreplay.internal.composition.CapturedNativeWindow
import com.datadog.android.sessionreplay.internal.composition.CapturedWindow
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedBackgroundColor
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class CapturedNativeSnapshotForgeryFactory : ForgeryFactory<CapturedNativeSnapshot> {
    override fun getForgery(forge: Forge): CapturedNativeSnapshot {
        val window = forge.getForgery<CapturedNativeWindow>()
        return CapturedNativeSnapshot(
            forge.aLong(min = 1L),
            window.views.first().identity.scope,
            listOf(window)
        )
    }
}

internal class CapturedNativeWindowForgeryFactory : ForgeryFactory<CapturedNativeWindow> {
    override fun getForgery(forge: Forge): CapturedNativeWindow {
        val root = forge.getForgery<CapturedNativeView>()
        val factory = DefaultCapturedIdentityFactory(root.identity.scope)
        val children = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            forge.getForgery<CapturedNativeView>().copy(
                identity = factory.view(root.identity, forge.anAlphabeticalString()),
                parentIndex = 0,
                kind = CapturedLayerKind.NATIVE_VIEW
            )
        }
        return CapturedNativeWindow(forge.aFloat(min = 0.75f, max = 4f), listOf(root) + children)
    }
}

internal class CapturedNativeViewForgeryFactory : ForgeryFactory<CapturedNativeView> {
    override fun getForgery(forge: Forge): CapturedNativeView {
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(forge.anAlphabeticalString()))
        return CapturedNativeView(
            identity = factory.window(forge.anAlphabeticalString()),
            parentIndex = -1,
            bounds = CapturedBounds(
                forge.aLong(min = 0L, max = 100L),
                forge.aLong(min = 0L, max = 100L),
                forge.aLong(min = 1L, max = 1000L),
                forge.aLong(min = 1L, max = 1000L)
            ),
            alpha = forge.aDouble(min = 0.0, max = 1.0),
            viewData = CapturedViewData.Mapped(CapturedViewMapperResult.None),
            clipToBounds = forge.aBool(),
            explicitClip = null,
            childPaddingClip = null
        )
    }
}

internal class CapturedWindowForgeryFactory : ForgeryFactory<CapturedWindow> {
    override fun getForgery(forge: Forge): CapturedWindow {
        val view = forge.getForgery<CapturedNativeView>()
        val root = CapturedLayer(view.identity, CapturedLayerKind.WINDOW_ROOT, view.bounds, emptyList<CapturedChild>())
        return CapturedWindow(root, listOf(root), emptyList())
    }
}

internal class CapturedBackgroundColorForgeryFactory : ForgeryFactory<CapturedBackgroundColor> {
    override fun getForgery(forge: Forge): CapturedBackgroundColor {
        val color = forge.anInt()
        return CapturedBackgroundColor(color, DefaultColorStringFormatter, color ushr 24 == 0xff)
    }
}

internal class CapturedMappingContextForgeryFactory : ForgeryFactory<CapturedMappingContext> {
    override fun getForgery(forge: Forge): CapturedMappingContext {
        val view = forge.getForgery<CapturedNativeView>()
        return CapturedMappingContext(
            DefaultCapturedIdentityFactory(view.identity.scope),
            view.identity,
            forge.aFloat(min = 0.75f, max = 4f),
            view.bounds
        )
    }
}

internal class CapturedForegroundForgeryFactory : ForgeryFactory<CapturedForeground> {
    override fun getForgery(forge: Forge): CapturedForeground {
        val view = forge.getForgery<CapturedNativeView>()
        val factory = DefaultCapturedIdentityFactory(view.identity.scope)
        val identity = factory.layer(view.identity, "foreground")
        return CapturedForeground(
            identity,
            CapturedWireframe.Shape(
                factory.shapeWireframe(identity),
                view.bounds
            )
        )
    }
}
