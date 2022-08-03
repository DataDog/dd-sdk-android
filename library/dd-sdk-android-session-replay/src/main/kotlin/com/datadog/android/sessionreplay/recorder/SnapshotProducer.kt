/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import androidx.appcompat.widget.ActionBarContextView
import androidx.appcompat.widget.Toolbar
import com.datadog.android.sessionreplay.recorder.mapper.GenericWireframeMapper
import com.datadog.android.sessionreplay.recorder.mapper.ViewScreenshotWireframeMapper
import java.util.LinkedList

internal class SnapshotProducer(
    private val viewScreenshotWireframeMapper: ViewScreenshotWireframeMapper =
        ViewScreenshotWireframeMapper(),
    private val genericWireframeMapper: GenericWireframeMapper =
        GenericWireframeMapper(imageMapper = viewScreenshotWireframeMapper)
) {

    private val systemViewIds by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOf(android.R.id.navigationBarBackground, android.R.id.statusBarBackground)
        } else {
            emptySet()
        }
    }

    fun produce(rootView: View, pixelsDensity: Float): Node? {
        return convertViewToNode(rootView, pixelsDensity)
    }

    @Suppress("ComplexMethod")
    private fun convertViewToNode(view: View, pixelsDensity: Float): Node? {
        if (view.isNotVisible()) {
            return null
        }

        if (view.isSystemNoise()) {
            return null
        }

        if (view.isToolbar()) {
            // skip adding the children and just take a screenshot of the toolbar.
            // It is too complex to de - structure this in multiple wireframes
            // and we cannot actually get all the details here.
            return Node(
                wireframes = listOf(viewScreenshotWireframeMapper.map(view, pixelsDensity))
            )
        }

        val childNodes = LinkedList<Node>()
        if (view is ViewGroup && view.childCount > 0) {
            for (i in 0 until view.childCount) {
                val viewChild = view.getChildAt(i) ?: continue
                convertViewToNode(viewChild, pixelsDensity)?.let {
                    childNodes.add(it)
                }
            }
        }

        return convertViewToNode(view, childNodes, pixelsDensity)
    }

    private fun convertViewToNode(
        view: View,
        childNodes: LinkedList<Node>,
        pixelsDensity: Float
    ) = Node(
        children = childNodes,
        wireframes = listOf(genericWireframeMapper.map(view, pixelsDensity))
    )

    private fun View.isToolbar(): Boolean {
        return (
            Toolbar::class.java.isAssignableFrom(this::class.java) &&
                this.id == androidx.appcompat.R.id.action_bar
            ) ||
            (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                    android.widget.Toolbar::class.java
                        .isAssignableFrom(this::class.java)
                )
    }

    private fun View.isNotVisible(): Boolean {
        return !this.isShown || this.width <= 0 || this.height <= 0
    }

    private fun View.isSystemNoise(): Boolean {
        return id in systemViewIds ||
            this is ViewStub ||
            this is ActionBarContextView
    }
}
