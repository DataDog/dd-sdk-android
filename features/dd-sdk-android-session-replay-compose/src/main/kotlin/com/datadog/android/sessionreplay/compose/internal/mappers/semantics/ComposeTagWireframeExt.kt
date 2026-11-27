/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import com.datadog.android.sessionreplay.model.MobileSegment

private const val COMPOSE_PERMANENT_ID_PREFIX = "compose:"

/**
 * Builds the `permanentId` for a composable tagged with [tag].
 *
 * The prefix namespaces Compose tags away from the structural heatmap identifiers that the
 * View-based recorder mints, so the two can never collide.
 */
internal fun composePermanentIdFor(tag: String): String = "$COMPOSE_PERMANENT_ID_PREFIX$tag"

/**
 * Returns a copy of this wireframe carrying [permanentId].
 *
 * Duplicated from the session-replay module's `copyWithPermanentId`, which is `internal` to that
 * Gradle module and therefore not visible here.
 */
internal fun MobileSegment.Wireframe.copyWithComposePermanentId(
    permanentId: String
): MobileSegment.Wireframe {
    return when (this) {
        is MobileSegment.Wireframe.ShapeWireframe -> this.copy(permanentId = permanentId)
        is MobileSegment.Wireframe.TextWireframe -> this.copy(permanentId = permanentId)
        is MobileSegment.Wireframe.ImageWireframe -> this.copy(permanentId = permanentId)
        is MobileSegment.Wireframe.PlaceholderWireframe -> this.copy(permanentId = permanentId)
        is MobileSegment.Wireframe.WebviewWireframe -> this.copy(permanentId = permanentId)
        is MobileSegment.Wireframe.EmbeddedContentWireframe -> this.copy(permanentId = permanentId)
    }
}
