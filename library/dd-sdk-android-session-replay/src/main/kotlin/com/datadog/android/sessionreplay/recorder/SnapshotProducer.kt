/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.content.res.Resources.Theme
import android.view.View
import android.view.ViewGroup
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.datadog.android.sessionreplay.recorder.mapper.GenericWireframeMapper
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.ThemeUtils
import com.datadog.android.sessionreplay.utils.copy
import com.datadog.android.sessionreplay.utils.shapeStyle
import java.util.LinkedList

internal class SnapshotProducer(
    private val wireframeMapper: GenericWireframeMapper,
    private val viewUtils: ViewUtils = ViewUtils(),
    private val stringUtils: StringUtils = StringUtils,
    private val themeUtils: ThemeUtils = ThemeUtils
) {

    fun produce(theme: Theme, rootView: View, pixelsDensity: Float): Node? {
        val snapshot = convertViewToNode(rootView, pixelsDensity, LinkedList()) ?: return null
        // we call this at the end because we are waiting to see if the ViewMapper was able to
        // resolve the root background from any drawable
        return resolveRootBackgroundFromTheme(theme, rootView, snapshot)
    }

    @Suppress("ComplexMethod", "ReturnCount")
    private fun convertViewToNode(
        view: View,
        pixelsDensity: Float,
        parents: LinkedList<MobileSegment.Wireframe>
    ): Node? {
        if (viewUtils.checkIfNotVisible(view)) {
            return null
        }

        if (viewUtils.checkIfSystemNoise(view)) {
            return null
        }

        if (viewUtils.checkIsToolbar(view)) {
            // skip adding the children and just take a screenshot of the toolbar.
            // It is too complex to de - structure this in multiple wireframes
            // and we cannot actually get all the details here.
            return Node(
                wireframe = wireframeMapper.imageMapper.map(view, pixelsDensity)
            )
        }

        val childNodes = LinkedList<Node>()
        val wireframe = wireframeMapper.map(view, pixelsDensity)
        if (view is ViewGroup && view.childCount > 0) {
            val parentsCopy = LinkedList(parents).apply { add(wireframe) }
            for (i in 0 until view.childCount) {
                val viewChild = view.getChildAt(i) ?: continue
                convertViewToNode(viewChild, pixelsDensity, parentsCopy)?.let {
                    childNodes.add(it)
                }
            }
        }

        return Node(
            children = childNodes,
            wireframe = wireframe,
            parents = parents
        )
    }

    private fun resolveRootBackgroundFromTheme(theme: Theme, rootView: View, snapshot: Node): Node {
        val rootShapeStyle = snapshot.wireframe.shapeStyle()
        // we add a shapeStyle based on the Theme color in case the
        // root wireframe does not have a ShapeStyle
        if (rootShapeStyle == null) {
            themeUtils.resolveThemeColor(theme)?.let {
                val colorAndAlphaAsHexa = stringUtils.formatColorAndAlphaAsHexa(
                    it,
                    BaseWireframeMapper.OPAQUE_ALPHA_VALUE
                )
                val shapeStyle = MobileSegment.ShapeStyle(
                    backgroundColor = colorAndAlphaAsHexa,
                    opacity = rootView.alpha
                )
                return snapshot.copy(wireframe = snapshot.wireframe.copy(shapeStyle = shapeStyle))
            }
        }
        return snapshot
    }
}
