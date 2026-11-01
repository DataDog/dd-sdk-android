/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope

/**
 * Mints every [CapturedIdentity] this module needs; [CompositionIdentityFactory] carries the
 * subset also needed cross-module by the Compose decomposer.
 */
@Suppress("TooManyFunctions") // Each function creates one supported capture identity type.
internal interface CapturedIdentityFactory : CompositionIdentityFactory {
    val scope: RumViewIdentityScope

    fun screenRoot(): CapturedIdentity

    fun window(windowId: String): CapturedIdentity

    fun view(window: CapturedIdentity, viewId: String): CapturedIdentity

    fun layer(owner: CapturedIdentity, layerId: String): CapturedIdentity

    fun imageWireframe(owner: CapturedIdentity): CapturedIdentity

    fun webViewWireframe(owner: CapturedIdentity, slotId: Long): CapturedIdentity
}
