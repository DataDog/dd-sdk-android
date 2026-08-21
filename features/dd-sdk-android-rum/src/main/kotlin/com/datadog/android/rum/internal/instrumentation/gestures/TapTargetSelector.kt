/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.View
import android.view.ViewGroup

internal class TapTargetSelector {

    fun shouldSelectCandidate(currentHostView: View?, candidateHostView: View): Boolean {
        return currentHostView == null || !currentHostView.isPreferredOver(candidateHostView)
    }

    private fun View.isPreferredOver(candidateView: View): Boolean {
        val currentPath = pathFromRoot()
        val candidatePath = candidateView.pathFromRoot()
        val commonPathSize = currentPath.commonPathSizeWith(candidatePath)
        val viewsAreDisconnected = commonPathSize == 0
        val currentIsSameOrAncestor = commonPathSize == currentPath.size
        val candidateIsAncestor = commonPathSize == candidatePath.size

        return when {
            viewsAreDisconnected -> false
            // Within the same branch, prefer the deepest clickable view. An ancestor's Z does not
            // place it in front of its own descendants.
            currentIsSameOrAncestor -> false
            candidateIsAncestor -> true
            else -> currentPath.isBranchDrawnOnTopOf(candidatePath, commonPathSize)
        }
    }

    private fun List<View>.commonPathSizeWith(otherPath: List<View>): Int {
        val maximumCommonPathSize = minOf(size, otherPath.size)
        var commonPathSize = 0
        while (
            commonPathSize < maximumCommonPathSize &&
            this[commonPathSize] === otherPath[commonPathSize]
        ) {
            commonPathSize++
        }
        return commonPathSize
    }

    private fun List<View>.isBranchDrawnOnTopOf(
        candidatePath: List<View>,
        commonPathSize: Int
    ): Boolean {
        val divergingBranchIndex = commonPathSize
        val commonParent = this[divergingBranchIndex - 1] as? ViewGroup ?: return false
        val currentBranch = this[divergingBranchIndex]
        val candidateBranch = candidatePath[divergingBranchIndex]

        // Target depth and Z within each branch are irrelevant after the paths diverge. Android
        // draws the two sibling branches as units, so their ordering decides which target is on top.
        return currentBranch.isDrawnOnTopOf(candidateBranch, commonParent)
    }

    private fun View.isDrawnOnTopOf(candidateView: View, parent: ViewGroup): Boolean {
        return when {
            z > candidateView.z -> true
            z < candidateView.z -> false
            // Both comparisons are also false for NaN, so fall back to child index order.
            else -> parent.isLaterChild(this, candidateView)
        }
    }

    private fun ViewGroup.isLaterChild(child: View, candidateChild: View): Boolean {
        val childIndex = childIndexOf(child)
        val candidateChildIndex = childIndexOf(candidateChild)
        return childIndex != null && candidateChildIndex != null && childIndex > candidateChildIndex
    }

    // indexOfChild is overridable, so exceptions from a custom ViewGroup are contained here.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun ViewGroup.childIndexOf(child: View): Int? {
        return try {
            val childIndex = indexOfChild(child)
            if (childIndex >= 0) childIndex else null
        } catch (_: Exception) {
            null
        }
    }

    private fun View.pathFromRoot(): List<View> {
        val path = mutableListOf<View>()
        var current: View? = this
        while (current != null) {
            path += current
            current = current.parent as? View
        }
        // path is backed by mutableListOf, so reverse cannot encounter an immutable implementation.
        @Suppress("UnsafeThirdPartyFunctionCall")
        path.reverse()
        return path
    }
}
