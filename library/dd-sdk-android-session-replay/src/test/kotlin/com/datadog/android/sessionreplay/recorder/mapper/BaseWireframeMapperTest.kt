/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.densityNormalized
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.annotation.FloatForgery
import java.util.stream.Stream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.params.provider.Arguments

internal abstract class BaseWireframeMapperTest {

    @FloatForgery(min = 1f, max = 10f)
    var fakePixelDensity: Float = 1f

    protected fun View.toShapeWireframe(): MobileSegment.Wireframe.ShapeWireframe {
        val coordinates = IntArray(2)
        this.getLocationInWindow(coordinates)
        val x = coordinates[0].densityNormalized(fakePixelDensity).toLong()
        val y = coordinates[1].densityNormalized(fakePixelDensity).toLong()
        return MobileSegment.Wireframe.ShapeWireframe(
            id.toLong(),
            x = x,
            y = y,
            width = width.toLong().densityNormalized(fakePixelDensity),
            height = height.toLong().densityNormalized(fakePixelDensity)
        )
    }

    protected fun TextView.toTextWireframe(): MobileSegment.Wireframe.TextWireframe {
        val coordinates = IntArray(2)
        this.getLocationInWindow(coordinates)
        val x = coordinates[0].densityNormalized(fakePixelDensity).toLong()
        val y = coordinates[1].densityNormalized(fakePixelDensity).toLong()
        return MobileSegment.Wireframe.TextWireframe(
            id.toLong(),
            x = x,
            y = y,
            text = text.toString(),
            width = width.toLong().densityNormalized(fakePixelDensity),
            height = height.toLong().densityNormalized(fakePixelDensity),
            textStyle = MobileSegment.TextStyle(
                "sans-serif",
                0,
                "#000000FF"
            ),
            textPosition = MobileSegment.TextPosition(
                MobileSegment.Padding(0, 0, 0, 0),
                alignment =
                MobileSegment.Alignment(
                    MobileSegment.Horizontal.LEFT,
                    MobileSegment.Vertical
                        .CENTER
                )
            )
        )
    }

    companion object {
        const val ALPHA_MASK: Long = 0x000000FF

        @JvmStatic
        fun textTypefaces(): Stream<Arguments> {
            // we initialize the TYPEFACES
            Typeface::class.java.setStaticValue("DEFAULT", mock<Typeface>())
            Typeface::class.java.setStaticValue("MONOSPACE", mock<Typeface>())
            Typeface::class.java.setStaticValue("DEFAULT_BOLD", mock<Typeface>())
            Typeface::class.java.setStaticValue("SERIF", mock<Typeface>())
            Typeface::class.java.setStaticValue("SANS_SERIF", mock<Typeface>())
            return listOf(
                Arguments.of(Typeface.DEFAULT, TextWireframeMapper.SANS_SERIF_FAMILY_NAME),
                Arguments.of(Typeface.DEFAULT_BOLD, TextWireframeMapper.SANS_SERIF_FAMILY_NAME),
                Arguments.of(Typeface.MONOSPACE, TextWireframeMapper.MONOSPACE_FAMILY_NAME),
                Arguments.of(Typeface.SERIF, TextWireframeMapper.SERIF_FAMILY_NAME),
                Arguments.of(mock<Typeface>(), TextWireframeMapper.SANS_SERIF_FAMILY_NAME)
            )
                .stream()
        }

        @JvmStatic
        fun textAlignments(): Stream<Arguments> {
            return listOf(
                Arguments.of(
                    TextView.TEXT_ALIGNMENT_CENTER,
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.CENTER,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    TextView.TEXT_ALIGNMENT_TEXT_END,
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    TextView.TEXT_ALIGNMENT_VIEW_END,
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    TextView.TEXT_ALIGNMENT_TEXT_START,
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    TextView.TEXT_ALIGNMENT_VIEW_START,
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Int.MAX_VALUE,
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                )
            ).stream()
        }

        @JvmStatic
        fun textAlignmentsFromGravity(): Stream<Arguments> {
            return listOf(
                Arguments.of(
                    Gravity.START.or(Gravity.TOP),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.TOP
                    )
                ),
                Arguments.of(
                    Gravity.LEFT.or(Gravity.TOP),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.TOP
                    )
                ),
                Arguments.of(
                    Gravity.RIGHT.or(Gravity.TOP),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.TOP
                    )
                ),
                Arguments.of(
                    Gravity.END.or(Gravity.TOP),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.TOP
                    )
                ),
                Arguments.of(
                    Gravity.CENTER.or(Gravity.TOP),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.CENTER,
                        vertical = MobileSegment.Vertical.TOP
                    )
                ),
                Arguments.of(
                    Gravity.CENTER_HORIZONTAL.or(Gravity.TOP),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.CENTER,
                        vertical = MobileSegment.Vertical.TOP
                    )
                ),
                Arguments.of(
                    Gravity.START.or(Gravity.BOTTOM),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.BOTTOM
                    )
                ),
                Arguments.of(
                    Gravity.LEFT.or(Gravity.BOTTOM),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.BOTTOM
                    )
                ),
                Arguments.of(
                    Gravity.RIGHT.or(Gravity.BOTTOM),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.BOTTOM
                    )
                ),
                Arguments.of(
                    Gravity.END.or(Gravity.BOTTOM),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.BOTTOM
                    )
                ),
                Arguments.of(
                    Gravity.CENTER.or(Gravity.BOTTOM),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.CENTER,
                        vertical = MobileSegment.Vertical.BOTTOM
                    )
                ),
                Arguments.of(
                    Gravity.CENTER_HORIZONTAL.or(Gravity.BOTTOM),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.CENTER,
                        vertical = MobileSegment.Vertical.BOTTOM
                    )
                ),
                Arguments.of(
                    Gravity.START.or(Gravity.CENTER),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.LEFT.or(Gravity.CENTER),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.RIGHT.or(Gravity.CENTER),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.END.or(Gravity.CENTER),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.CENTER.or(Gravity.CENTER),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.CENTER,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.CENTER_HORIZONTAL.or(Gravity.CENTER),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.CENTER,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.START.or(Gravity.CENTER_VERTICAL),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.LEFT.or(Gravity.CENTER_VERTICAL),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.RIGHT.or(Gravity.CENTER_VERTICAL),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.END.or(Gravity.CENTER_VERTICAL),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.RIGHT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.CENTER.or(Gravity.CENTER_VERTICAL),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.CENTER,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Gravity.CENTER_HORIZONTAL.or(Gravity.CENTER_VERTICAL),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.CENTER,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                ),
                Arguments.of(
                    Int.MAX_VALUE.or(Int.MIN_VALUE),
                    MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                )

            ).stream()
        }

        @AfterAll
        @JvmStatic
        fun `tear down all`() {
            // we reset the TYPEFACES
            Typeface::class.java.setStaticValue("DEFAULT", null)
            Typeface::class.java.setStaticValue("MONOSPACE", null)
            Typeface::class.java.setStaticValue("DEFAULT_BOLD", null)
            Typeface::class.java.setStaticValue("SERIF", null)
            Typeface::class.java.setStaticValue("SANS_SERIF", null)
        }
    }
}
