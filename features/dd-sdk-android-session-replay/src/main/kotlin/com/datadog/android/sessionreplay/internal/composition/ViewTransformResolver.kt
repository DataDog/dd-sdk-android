/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Matrix
import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.sessionreplay.utils.GlobalBounds
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.floor

/** UI-owned matrix values; no live hierarchy is retained by the resulting pixel capture. */
internal class ViewTransform(val matrix: FloatArray, val pixelBounds: GlobalBounds) {
    /** Only a positive uniform scale can be represented by native bounds and scalar text metrics. */
    val uniformScale: Float?
        get() {
            val scale = matrix[Matrix.MSCALE_X]
            val supported = scale > 0f && scale == matrix[Matrix.MSCALE_Y] && matrix[Matrix.MSKEW_X] == 0f &&
                matrix[Matrix.MSKEW_Y] == 0f && matrix[Matrix.MPERSP_0] == 0f &&
                matrix[Matrix.MPERSP_1] == 0f && matrix[Matrix.MPERSP_2] == 1f
            return if (supported) scale else null
        }

    /** Projects local rectangular clips for the uniform-scale semantic path. */
    @Suppress("UnsafeThirdPartyFunctionCall") // Finite primitive arithmetic over a validated uniform matrix.
    fun mapBounds(bounds: GlobalBounds): GlobalBounds {
        val scale = matrix[Matrix.MSCALE_X].toDouble()
        val left = floor(matrix[Matrix.MTRANS_X] + bounds.x * scale).toLong()
        val top = floor(matrix[Matrix.MTRANS_Y] + bounds.y * scale).toLong()
        val right = ceil(matrix[Matrix.MTRANS_X] + (bounds.x + bounds.width) * scale).toLong()
        val bottom = ceil(matrix[Matrix.MTRANS_Y] + (bounds.y + bounds.height) * scale).toLong()
        return GlobalBounds(left, top, right - left, bottom - top)
    }

    val isAxisAligned: Boolean
        get() = matrix[Matrix.MPERSP_0] == 0f && matrix[Matrix.MPERSP_1] == 0f &&
            (
                (matrix[Matrix.MSKEW_X] == 0f && matrix[Matrix.MSKEW_Y] == 0f) ||
                    (matrix[Matrix.MSCALE_X] == 0f && matrix[Matrix.MSCALE_Y] == 0f)
                )
}

@Suppress("UnsafeThirdPartyFunctionCall") // floor/ceil on primitive doubles cannot throw.
internal fun GlobalBounds.toTransformedReplayBounds(density: Float): CapturedBounds {
    val scale = if (density == 0f) 1.0 else 1.0 / density
    val left = floor(x * scale).toLong()
    val top = floor(y * scale).toLong()
    val right = ceil((x + width) * scale).toLong()
    val bottom = ceil((y + height) * scale).toLong()
    return CapturedBounds(left, top, right - left, bottom - top)
}

/** Equivalent to concatenating View matrices through the hierarchy, also on API 23–28. */
internal object ViewTransformResolver {
    @UiThread
    @Suppress("ReturnCount") // Known inherited scale and identity matrices avoid an unnecessary matrix read.
    fun hasSpatialTransform(view: View, inheritedScale: Float = 1f): Boolean {
        if (inheritedScale != 1f) return true
        val ownMatrix = view.matrix
        if (ownMatrix == null || ownMatrix.isIdentity) return false
        val matrix = identity()
        @Suppress("UnsafeThirdPartyFunctionCall") // Owned nine-element matrix array.
        ownMatrix.getValues(matrix)
        return matrix[Matrix.MSCALE_X] != 1f || matrix[Matrix.MSKEW_X] != 0f || matrix[Matrix.MSKEW_Y] != 0f ||
            matrix[Matrix.MSCALE_Y] != 1f ||
            matrix[Matrix.MPERSP_0] != 0f || matrix[Matrix.MPERSP_1] != 0f || matrix[Matrix.MPERSP_2] != 1f
    }

    @UiThread
    @Suppress("UnsafeThirdPartyFunctionCall") // Owned identity map accepts each non-null View.
    fun hasTransformedAncestor(view: View): Boolean {
        val visited = IdentityHashMap<View, Boolean>()
        var current: View? = view
        while (current != null && visited.put(current, true) == null) {
            if (hasSpatialTransform(current)) return true
            current = current.parent as? View
        }
        return false
    }

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Owned arrays; invalid matrices fail closed.
    @UiThread
    fun resolve(view: View): ViewTransform? {
        val ancestors = mutableListOf<View>()
        val visited = IdentityHashMap<View, Boolean>()
        var current: View? = view
        while (current != null) {
            if (visited.put(current, true) != null) return null
            ancestors += current
            current = current.parent as? View
        }
        val root = ancestors.last()
        val rootMatrix = values(root)
        if (rootMatrix[Matrix.MPERSP_2] == 0f) return null
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        // getLocationOnScreen includes the root's own transformed origin and the window offset.
        var matrix = multiply(
            translation(
                location[0] - rootMatrix[Matrix.MTRANS_X] / rootMatrix[Matrix.MPERSP_2],
                location[1] - rootMatrix[Matrix.MTRANS_Y] / rootMatrix[Matrix.MPERSP_2]
            ),
            rootMatrix
        )
        for (index in ancestors.lastIndex - 1 downTo 0) {
            val child = ancestors[index]
            val parent = ancestors[index + 1]
            matrix = multiply(
                multiply(
                    matrix,
                    translation((child.left - parent.scrollX).toFloat(), (child.top - parent.scrollY).toFloat())
                ),
                values(child)
            )
        }
        if (matrix.any { !it.isFinite() }) return null
        val xs = doubleArrayOf(0.0, view.width.toDouble(), view.width.toDouble(), 0.0)
        val ys = doubleArrayOf(0.0, 0.0, view.height.toDouble(), view.height.toDouble())
        for (index in xs.indices) {
            val x = xs[index]
            val y = ys[index]
            val w = matrix[Matrix.MPERSP_0] * x + matrix[Matrix.MPERSP_1] * y + matrix[Matrix.MPERSP_2]
            // A perspective plane crossing the camera cannot be bounded by its four corners.
            if (w <= 0.0 || !w.isFinite()) return null
            xs[index] = (matrix[Matrix.MSCALE_X] * x + matrix[Matrix.MSKEW_X] * y + matrix[Matrix.MTRANS_X]) / w
            ys[index] = (matrix[Matrix.MSKEW_Y] * x + matrix[Matrix.MSCALE_Y] * y + matrix[Matrix.MTRANS_Y]) / w
        }
        val left = floor(xs.min()).toLong()
        val top = floor(ys.min()).toLong()
        val right = ceil(xs.max()).toLong()
        val bottom = ceil(ys.max()).toLong()
        val fitsHorizontally = left >= Int.MIN_VALUE && right <= Int.MAX_VALUE
        val fitsVertically = top >= Int.MIN_VALUE && bottom <= Int.MAX_VALUE
        if (!fitsHorizontally || !fitsVertically) return null
        return ViewTransform(matrix, GlobalBounds(left, top, right - left, bottom - top))
    }

    @UiThread
    private fun values(view: View): FloatArray = identity().also { values ->
        @Suppress("UnsafeThirdPartyFunctionCall") // Matrix.getValues receives exactly nine entries.
        view.matrix?.getValues(values)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Fixed-size owned matrix arrays and primitive arithmetic.
    private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    @Suppress("UnsafeThirdPartyFunctionCall") // Fixed-size owned matrix arrays and primitive arithmetic.
    private fun translation(x: Float, y: Float) = floatArrayOf(1f, 0f, x, 0f, 1f, y, 0f, 0f, 1f)

    @Suppress("UnsafeThirdPartyFunctionCall") // Fixed-size owned matrix arrays and primitive arithmetic.
    private fun multiply(left: FloatArray, right: FloatArray): FloatArray = FloatArray(MATRIX_ELEMENTS) { index ->
        val row = index / MATRIX_DIMENSION
        val column = index % MATRIX_DIMENSION
        (0 until MATRIX_DIMENSION).sumOf {
            left[row * MATRIX_DIMENSION + it].toDouble() *
                right[it * MATRIX_DIMENSION + column]
        }.toFloat()
    }
}

private const val MATRIX_DIMENSION = 3
private const val MATRIX_ELEMENTS = MATRIX_DIMENSION * MATRIX_DIMENSION
