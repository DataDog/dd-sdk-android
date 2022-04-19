/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public fun View.toJson(context: Context, scale: Float): JsonElement? {
    if (id in setOf(android.R.id.navigationBarBackground, android.R.id.statusBarBackground)
        || this is ViewStub || this.javaClass.name == "androidx.appcompat.widget.ActionBarContextView"
    ) {
        return null
    }
    if (this.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
        return SessionReplay.rootNodes[this.hashCode()]?.toJson(scale)
    }

    val jsonElement = JsonObject()
    jsonElement.addProperty("displayName", this::class.java.simpleName)
    jsonElement.addProperty("height", this.height.toPx(scale))
    jsonElement.addProperty("width", this.width.toPx(scale))
    jsonElement.addProperty("x", this.x.toPx(scale))
    jsonElement.addProperty("y", this.y.toPx(scale))
    jsonElement.addProperty("opacity", alpha)

    val viewBackground = background
    if (viewBackground is ColorDrawable) {
        val color = viewBackground.color
        val colorAsHexa = String.format("#%06X", 0xFFFFFF and color)
        jsonElement.addProperty("backgroundColor", colorAsHexa)
    }

    when {
        // Toolbar::class.java.isAssignableFrom(this::class.java) -> {
        //     val toolbar = this as Toolbar
        //     // if (viewBackground == null) {
        //     //     jsonElement.addProperty("backgroundColor", getPrimaryColor(toolbar.context))
        //     // }
        // }
        Button::class.java.isAssignableFrom(this::class.java) -> {
            val button = this as Button
            val textColor = String.format("#%06X", 0xFFFFFF and button.currentTextColor)
            jsonElement.addProperty("label", button.text.toString())
            jsonElement.addProperty("color", textColor)
            jsonElement.addProperty("type", "button")
            jsonElement.addProperty("textAlign", button.gravity.asHtmlTextAlignment())
        }
        ImageView::class.java.isAssignableFrom(this::class.java) -> {
            jsonElement.addProperty("type", "button")
        }
        TextView::class.java.isAssignableFrom(this::class.java) -> {
            val textView = this as TextView
            val textColor = String.format("#%06X", 0xFFFFFF and textView.currentTextColor)
            jsonElement.addProperty("label", textView.text.toString())
            jsonElement.addProperty("color", textColor)
            jsonElement.addProperty("textAlign", textView.gravity.asHtmlTextAlignment())
            jsonElement.addProperty("fontSize", textView.textSize.toPx(scale))
            val textType = if (isClickable) "button" else "p"
            jsonElement.addProperty("type", textType)
        }
        LinearLayout::class.java.isAssignableFrom(this::class.java) -> {
            val linearLayout = this as LinearLayout
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                jsonElement.addProperty("textAlign", linearLayout.gravity.asHtmlTextAlignment())
            }
            jsonElement.addProperty("type", "div")
        }
        RelativeLayout::class.java.isAssignableFrom(this::class.java) -> {
            val relativeLayout = this as RelativeLayout
            jsonElement.addProperty("textAlign", relativeLayout.gravity.asHtmlTextAlignment())
            jsonElement.addProperty("type", "div")
        }
        else -> {
            jsonElement.addProperty("type", "div")
        }
    }

    if (this is ViewGroup && childCount > 0) {
        val childrenArray = JsonArray()
        if (this.javaClass.name == "androidx.appcompat.widget.ActionBarOverlayLayout") {
            for (i in childCount - 1 downTo 0) {
                val view = getChildAt(i) ?: continue
                view.toJson(context, scale)?.let {
                    childrenArray.add(it)
                }
            }
        } else {
            for (i in 0 until childCount) {
                val view = getChildAt(i) ?: continue
                view.toJson(context, scale)?.let {
                    childrenArray.add(it)
                }
            }
        }
        jsonElement.add("children", childrenArray)
    }

    return jsonElement
}

private fun Int.toPx(scale: Float) = (this / scale).roundToInt()
private fun Float.toPx(scale: Float) = (this / scale).roundToInt()

// @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
// private fun getPrimaryColor(context: Context): String {
//     val typedValue = TypedValue()
//     context.getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true)
//     val color = typedValue.data
//     return String.format("#%06X", 0xFFFFFF and color)
// }

@SuppressLint("RtlHardcoded")
private fun Int.asHtmlTextAlignment(): String {
    return when (this) {
        Gravity.START -> "left"
        Gravity.END -> "right"
        Gravity.LEFT -> "left"
        Gravity.RIGHT -> "right"
        Gravity.CENTER -> "center"
        else -> "center"
    }
}

internal fun ComposeNode.toJson(scale: Float): JsonElement? {
    val jsonElement = JsonObject()
    val width = this.layoutInfo.width
    val height = this.layoutInfo.height
    val x = this.layoutInfo.coordinates.positionInParent().x
    val y = this.layoutInfo.coordinates.positionInParent().y
    jsonElement.addProperty("displayName", this::class.java.simpleName)
    jsonElement.addProperty("height", height.toPx(scale))
    jsonElement.addProperty("width", width.toPx(scale))
    jsonElement.addProperty("x", x.toPx(scale))
    jsonElement.addProperty("y", y.toPx(scale))
    jsonElement.addProperty("type", "div")
    jsonElement.addProperty("opacity", 1)
    val childrenJsonArray = JsonArray()
    children.values.map { it.toJson(scale) }.fold(childrenJsonArray, { acc, element ->
        acc.add(element)
        acc
    })
    if (childrenJsonArray.size() > 0) {
        jsonElement.add("children", childrenJsonArray)
    }
    val textProperties = layoutInfo.getModifierInfo()
        .filter { it.modifier is SemanticsModifier }.map { it.modifier as SemanticsModifier }
        .mapNotNull { it.semanticsConfiguration.getOrNull(SemanticsProperties.Text) }.flatten()
    if (textProperties.size > 0) {
        val textValue = textProperties.joinToString(" ")
        jsonElement.addProperty("type", "text")
        jsonElement.addProperty("label", textValue)
    }

    return jsonElement
}

