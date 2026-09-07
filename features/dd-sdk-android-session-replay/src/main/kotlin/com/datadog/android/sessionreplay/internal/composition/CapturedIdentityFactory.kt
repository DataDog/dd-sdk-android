/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

@Suppress("TooManyFunctions") // Each function creates one supported capture identity type.
internal interface CapturedIdentityFactory {
    val scope: RumViewIdentityScope

    fun screenRoot(): CapturedIdentity

    fun window(windowId: String): CapturedIdentity

    fun view(window: CapturedIdentity, viewId: String): CapturedIdentity

    fun composeHost(window: CapturedIdentity, hostId: String): CapturedIdentity

    fun composeNode(host: CapturedIdentity, nodeId: String): CapturedIdentity

    fun layer(owner: CapturedIdentity, layerId: String): CapturedIdentity

    fun shapeWireframe(owner: CapturedIdentity): CapturedIdentity

    fun textWireframe(owner: CapturedIdentity): CapturedIdentity

    fun imageWireframe(owner: CapturedIdentity): CapturedIdentity

    fun placeholderWireframe(owner: CapturedIdentity): CapturedIdentity

    fun webViewWireframe(owner: CapturedIdentity, slotId: Long): CapturedIdentity
}
