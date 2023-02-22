/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import com.datadog.android.sessionreplay.internal.recorder.mapper.GenericWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.LinkedList

internal class SnapshotProducer(
    private val wireframeMapper: GenericWireframeMapper,
    private val viewUtils: ViewUtils = ViewUtils()
) {

    fun produce(
        rootView: View,
        systemInformation: SystemInformation
    ): Node? {
        return convertViewToNode(rootView, systemInformation, LinkedList())
    }

    @Suppress("ComplexMethod", "ReturnCount")
    private fun convertViewToNode(
        view: View,
        systemInformation: SystemInformation,
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
                wireframes = wireframeMapper.imageMapper.map(view, systemInformation)
            )
        }

        val childNodes = LinkedList<Node>()
        val wireframes = wireframeMapper.map(view, systemInformation)
        if (view is ViewGroup && view.childCount > 0) {
            val parentsCopy = LinkedList(parents).apply { addAll(wireframes) }
            for (i in 0 until view.childCount) {
                val viewChild = view.getChildAt(i) ?: continue
                convertViewToNode(viewChild, systemInformation, parentsCopy)?.let {
                    childNodes.add(it)
                }
            }
        }

        return Node(
            children = childNodes,
            wireframes = wireframes,
            parents = parents
        )
    }
}
