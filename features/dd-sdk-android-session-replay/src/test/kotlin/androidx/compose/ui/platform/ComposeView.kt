/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package androidx.compose.ui.platform

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test-only stand-in living under the exact package and class name
 * `AndroidWindowTraversal.isComposeHost()` matches by name (`androidx.compose.ui.platform.ComposeView`).
 * A real dependency on Compose isn't available from this module, and a Mockito mock/spy of [View]
 * always reports a different (subclassed) runtime class, so it can never satisfy that check - only a
 * real, un-proxied instance with this exact name can. `View` isn't in this module's `unMock { keep(...) }`
 * allowlist, so its own methods (getWidth()/isShown()/etc.) are stubbed to their default values by the
 * plain Android jar regardless of what's overridden here; callers relying on this fixture pass a mocked
 * `ViewUtilsInternal` rather than the real one to avoid depending on that unusable View state.
 */
internal class ComposeView(context: Context, density: Float) : View(context) {
    private val fakeResources: Resources = mock<Resources>().also {
        whenever(it.displayMetrics).thenReturn(DisplayMetrics().apply { this.density = density })
    }

    override fun getTag(key: Int): Any? = null
    override fun getResources(): Resources = fakeResources
}
