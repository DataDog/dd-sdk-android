/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.view.View
import android.view.ViewGroup
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.GenericWireframeMapper
import java.util.LinkedList

internal class SnapshotProducer(
    private val wireframeMapper: GenericWireframeMapper,
    private val viewUtils: ViewUtils = ViewUtils()
) {

    fun produce(rootView: View, pixelsDensity: Float): Node? {
        return convertViewToNode(rootView, pixelsDensity, LinkedList())
    }

    @Suppress("ComplexMethod")
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
}
