/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.ViewGroup
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * A basic abstract [WireframeMapper] to extend when mapping [ViewGroup] implementations.
 *
 * This class extends both [BaseAsyncBackgroundWireframeMapper] and [TraverseAllChildrenMapper],
 * ensuring that all children of the [ViewGroup] will be mapped by the relevant mappers.
 *
 *  @param T the type of the [ViewGroup] to map
 *  @param viewIdentifierResolver the [ViewIdentifierResolver] (to resolve a view or children stable id)
 *  @param colorStringFormatter the [ColorStringFormatter] to transform Color into HTML hex strings
 *  @param viewBoundsResolver the [ViewBoundsResolver] to get a view boundaries in density independent units
 *  @param drawableToColorMapper the [DrawableToColorMapper] to convert a background drawable into a solid color
 */
open class BaseViewGroupMapper<T : ViewGroup>(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseAsyncBackgroundWireframeMapper<T>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
),
    TraverseAllChildrenMapper<T>
