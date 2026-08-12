/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.tracking.ViewTarget
import java.util.IdentityHashMap

internal class TapTargetSelector(
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) {

    private var selectedHostedTarget: HostedTapTarget? = null
    private val drawingOrderCache = DrawingOrderCache()

    val selectedTarget: ViewTarget?
        get() = selectedHostedTarget?.target

    fun considerCandidate(target: ViewTarget, hostView: View) {
        val candidateTarget = HostedTapTarget(target, hostView)
        val currentTarget = selectedHostedTarget
        selectedHostedTarget = when {
            currentTarget == null -> candidateTarget
            currentTarget.hostView.isPreferredOver(candidateTarget.hostView) -> currentTarget
            else -> candidateTarget
        }
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
            // Both comparisons are also false for NaN, matching Android's child-order fallback.
            else -> parent.isChildDrawnAfter(this, candidateView)
        }
    }

    // Both indices are validated before accessing drawingRanks, so the array reads cannot fail.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun ViewGroup.isChildDrawnAfter(child: View, candidateChild: View): Boolean {
        val childIndex = childIndexOf(child)
        val candidateChildIndex = childIndexOf(candidateChild)
        return if (childIndex != null && candidateChildIndex != null) {
            val drawingRanks = drawingOrderCache.getOrCompute(this) { childDrawingRanks() }
            if (
                drawingRanks != null &&
                childIndex in drawingRanks.indices &&
                candidateChildIndex in drawingRanks.indices
            ) {
                drawingRanks[childIndex] > drawingRanks[candidateChildIndex]
            } else {
                childIndex > candidateChildIndex
            }
        } else {
            false
        }
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

    // childCount cannot be negative, and every index is checked before accessing drawingRanks.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun ViewGroup.childDrawingRanks(): IntArray? {
        // Android only exposes child drawing order publicly from API 29.
        if (!buildSdkVersionProvider.isAtLeastQ) return null

        val drawingRanks = IntArray(childCount)
        var drawingPosition = 0
        var orderIsValid = true
        while (drawingPosition < childCount && orderIsValid) {
            val childIndex = childIndexAtDrawingPosition(drawingPosition)
            val childIndexIsValid = childIndex != null &&
                childIndex in drawingRanks.indices &&
                drawingRanks[childIndex] == UNRANKED_CHILD
            if (childIndexIsValid && childIndex != null) {
                drawingRanks[childIndex] = drawingPosition + 1
            } else {
                orderIsValid = false
            }
            drawingPosition++
        }
        return if (orderIsValid) drawingRanks else null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    // A custom ViewGroup may throw from getChildDrawingOrder; the exception is contained here.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun ViewGroup.childIndexAtDrawingPosition(drawingPosition: Int): Int? {
        return try {
            getChildDrawingOrder(drawingPosition)
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

    private data class HostedTapTarget(
        val target: ViewTarget,
        val hostView: View
    )

    private data class CachedDrawingRanks(val value: IntArray?)

    private class DrawingOrderCache {

        private val drawingRanksByParent = IdentityHashMap<ViewGroup, CachedDrawingRanks>()

        // The map and non-null value wrapper are private to one traversal, so lookups cannot observe
        // concurrent mutation or a stored null. computeDrawingRanks handles platform callback failures.
        @Suppress("UnsafeThirdPartyFunctionCall")
        fun getOrCompute(
            parent: ViewGroup,
            computeDrawingRanks: () -> IntArray?
        ): IntArray? {
            val cachedRanks = drawingRanksByParent[parent]
            if (cachedRanks != null) return cachedRanks.value

            val computedRanks = CachedDrawingRanks(computeDrawingRanks())
            drawingRanksByParent[parent] = computedRanks
            return computedRanks.value
        }
    }

    private companion object {

        private const val UNRANKED_CHILD = 0
    }
}
