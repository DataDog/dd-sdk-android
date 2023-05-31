/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.annotation.Forgery
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.params.provider.Arguments
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.stream.Stream

internal abstract class BaseWireframeMapperTest {

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    protected fun mockNonDecorView(): View {
        return mock {
            whenever(it.parent).thenReturn(mock<ViewGroup>())
        }
    }

    protected fun mockViewWithNoViewTypeParent(): View {
        return mock()
    }

    protected fun mockViewWithEmptyParent(): View {
        return mock {
            whenever(it.parent).thenReturn(mock())
        }
    }

    protected fun View.toShapeWireframes(): List<MobileSegment.Wireframe.ShapeWireframe> {
        val coordinates = IntArray(2)
        val screenDensity = fakeMappingContext.systemInformation.screenDensity
        this.getLocationOnScreen(coordinates)
        val x = coordinates[0].densityNormalized(screenDensity).toLong()
        val y = coordinates[1].densityNormalized(screenDensity).toLong()
        return listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                System.identityHashCode(this).toLong(),
                x = x,
                y = y,
                width = width.toLong().densityNormalized(screenDensity),
                height = height.toLong().densityNormalized(screenDensity)
            )
        )
    }

    protected fun TextView.toTextWireframes(): List<MobileSegment.Wireframe.TextWireframe> {
        val coordinates = IntArray(2)
        this.getLocationOnScreen(coordinates)
        val screenDensity = fakeMappingContext.systemInformation.screenDensity
        val x = coordinates[0].densityNormalized(screenDensity).toLong()
        val y = coordinates[1].densityNormalized(screenDensity).toLong()
        val textColor = StringUtils.formatColorAndAlphaAsHexa(currentTextColor, OPAQUE_ALPHA_VALUE)
        return listOf(
            MobileSegment.Wireframe.TextWireframe(
                System.identityHashCode(this).toLong(),
                x = x,
                y = y,
                text = resolveTextValue(this),
                width = width.toLong().densityNormalized(screenDensity),
                height = height.toLong().densityNormalized(screenDensity),
                textStyle = MobileSegment.TextStyle(
                    TextViewMapper.SANS_SERIF_FAMILY_NAME,
                    textSize.toLong().densityNormalized(screenDensity),
                    textColor
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
        )
    }

    protected open fun resolveTextValue(textView: TextView): String {
        return textView.text.toString()
    }

    companion object {
        const val OPAQUE_ALPHA_VALUE: Int = 255
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
                Arguments.of(Typeface.DEFAULT, TextViewMapper.SANS_SERIF_FAMILY_NAME),
                Arguments.of(Typeface.DEFAULT_BOLD, TextViewMapper.SANS_SERIF_FAMILY_NAME),
                Arguments.of(Typeface.MONOSPACE, TextViewMapper.MONOSPACE_FAMILY_NAME),
                Arguments.of(Typeface.SERIF, TextViewMapper.SERIF_FAMILY_NAME),
                Arguments.of(mock<Typeface>(), TextViewMapper.SANS_SERIF_FAMILY_NAME),
                Arguments.of(null, TextViewMapper.SANS_SERIF_FAMILY_NAME)
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
