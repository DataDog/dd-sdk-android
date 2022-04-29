/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.core.graphics.scale
import com.datadog.android.sessionreplay.model.ElementNode
import com.datadog.android.sessionreplay.model.Node
import com.datadog.android.sessionreplay.model.Tags
import com.datadog.android.sessionreplay.model.TextNode
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.security.SecureRandom
import java.util.LinkedList
import kotlin.math.roundToInt

private val random = SecureRandom()

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal fun View.toJson(context: Context, scale: Float): JsonElement? {
    if (id in setOf(android.R.id.navigationBarBackground, android.R.id.statusBarBackground)
        || this is ViewStub || this.javaClass.name == "androidx.appcompat.widget.ActionBarContextView"
    ) {
        return null
    }
    if (this.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
        return SessionReplay.rootNodes[this.hashCode()]?.toJson(scale)
    }

    val scaledHeight = height.toPx(scale)
    val scaledWidth = width.toPx(scale)
    val jsonElement = JsonObject()
    jsonElement.addProperty("displayName", this::class.java.simpleName)
    jsonElement.addProperty("height", scaledHeight)
    jsonElement.addProperty("width", scaledWidth)
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
            if (scaledWidth > 0 && scaledHeight > 0) {
                SessionReplay.persistBitmap(
                    captureViewBitmap(this, scaledWidth, scaledHeight),
                    "${this.id}_${System.nanoTime()}"
                )
            }
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
    val scaledHeight = height.toPx(scale)
    val scaledWidth = width.toPx(scale)
    val x = this.layoutInfo.coordinates.positionInParent().x
    val y = this.layoutInfo.coordinates.positionInParent().y
    jsonElement.addProperty("displayName", this::class.java.simpleName)
    jsonElement.addProperty("height", scaledHeight)
    jsonElement.addProperty("width", scaledWidth)
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
    val semanticsModifiers =
        layoutInfo.getModifierInfo()
            .filter { it.modifier is SemanticsModifier }
            .map { it.modifier as SemanticsModifier }
    val textProperties =
        semanticsModifiers.mapNotNull { it.semanticsConfiguration.getOrNull(SemanticsProperties.Text) }
            .flatten()
    // val imageRole =
    //     semanticsModifiers.mapNotNull { it.semanticsConfiguration.getOrNull(SemanticsProperties.Role) }
    //         .filter { it.toString() == "Image" }
    if (textProperties.size > 0) {
        val textValue = textProperties.joinToString(" ")
        jsonElement.addProperty("type", "text")
        jsonElement.addProperty("label", textValue)
    }

    // if(imageRole!=null){
    //     layoutInfo.getModifierInfo().filter { it is  }
    // }
    return jsonElement
}

internal fun captureViewBitmap(view: View, scaledWidth: Int, scaledHeight: Int): Bitmap {
    val bitmap: Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    bitmap.scale(scaledWidth, scaledHeight)
    return bitmap
}

internal fun View.toNode(scale: Float): Node? {

    if (id in setOf(android.R.id.navigationBarBackground, android.R.id.statusBarBackground)
        || this is ViewStub || this.javaClass.name == "androidx.appcompat.widget.ActionBarContextView"
    ) {
        return null
    }
    if (this.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
        return null
        // return SessionReplay.rootNodes[this.hashCode()]?.toNode(scale)
    }

    val attributes = HashMap<String, Any>()
    val scaledHeight = height.toPx(scale)
    val scaledWidth = width.toPx(scale)
    attributes["width"] = scaledWidth
    attributes["height"] = scaledHeight
    attributes["x"] = this.x.toPx(scale)
    attributes["y"] = this.y.toPx(scale)
    attributes["alpha"] = alpha

    val viewBackground = background
    if (viewBackground is ColorDrawable) {
        val color = viewBackground.color
        val colorAsHexa = String.format("#%06X", 0xFFFFFF and color)
        attributes["backgroundColor"] = colorAsHexa
    }

    val childNodes = LinkedList<Node>()
    if (this is ViewGroup && childCount > 0) {
        if (this.javaClass.name == "androidx.appcompat.widget.ActionBarOverlayLayout") {
            for (i in childCount - 1 downTo 0) {
                val view = getChildAt(i) ?: continue
                view.toNode(scale)?.let {
                    childNodes.add(it)
                }
            }
        } else {
            for (i in 0 until childCount) {
                val view = getChildAt(i) ?: continue
                view.toNode(scale)?.let {
                    childNodes.add(it)
                }
            }
        }
    }

    when {
        Button::class.java.isAssignableFrom(this::class.java) -> {
            return (this as Button).asNode(attributes, scale)
        }
        ImageView::class.java.isAssignableFrom(this::class.java) -> {
            return (this as ImageView).asNode(attributes, scaledWidth, scaledHeight)
        }
        TextView::class.java.isAssignableFrom(this::class.java) -> {
            return (this as TextView).asNode(attributes, scale)
        }
        LinearLayout::class.java.isAssignableFrom(this::class.java) -> {
            return (this as LinearLayout).asNode(attributes, childNodes)
        }
        RelativeLayout::class.java.isAssignableFrom(this::class.java) -> {
            return (this as RelativeLayout).asNode(attributes, childNodes)
        }
        else -> {
            return ElementNode(
                childNodes = childNodes,
                id = this.resolvedId(),
                attributes = attributes
            )
        }
    }
}

private fun View.resolvedId(): Int? {
    return if (id != View.NO_ID) id else null
}

private fun TextView.asNode(
    attributes: MutableMap<String, Any>,
    scale: Float,
): TextNode {
    addTextAttributes(attributes, this, scale)
    return TextNode(
        textContent = this.text.toString(),
        attributes = attributes,
        id = resolvedId()
    )
}

private fun Button.asNode(
    attributes: MutableMap<String, Any>,
    scale: Float
): ElementNode {
    addButtonAttributes(attributes, this, scale)
    return ElementNode(
        attributes = attributes,
        id = resolvedId(),
        tagName = Tags.BUTTON.tagValue,
        childNodes = listOf(TextNode(textContent = text.toString()))
    )
}

private fun ImageView.asNode(
    attributes: MutableMap<String, Any>,
    scaledWidth: Int,
    scaledHeight: Int
): ElementNode {
    val uniqueId = random.nextInt().toString()
    if (scaledWidth > 0 && scaledHeight > 0) {
        SessionReplay.persistBitmap(
            captureViewBitmap(this, scaledWidth, scaledHeight),
            uniqueId
        )
    }
    attributes["src"] = uniqueId
    return ElementNode(
        attributes = attributes,
        id = resolvedId(),
        tagName = Tags.IMAGE.tagValue,
    )
}

private fun LinearLayout.asNode(
    attributes: MutableMap<String, Any>,
    children: List<Node>
): ElementNode {
    addLinearLayoutAttributes(attributes, this)
    return ElementNode(childNodes = children, id = resolvedId(), attributes = attributes)
}

private fun RelativeLayout.asNode(
    attributes: MutableMap<String, Any>,
    children: List<Node>
): ElementNode {
    addRelativeLayoutAttributes(attributes, this)
    return ElementNode(childNodes = children, id = resolvedId(), attributes = attributes)
}

private fun addRelativeLayoutAttributes(
    attributes: MutableMap<String, Any>,
    relativeLayout: RelativeLayout
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        attributes["textAlign"] = relativeLayout.gravity.asHtmlTextAlignment()
    }
}

// private fun addImageAttributes(attributes: MutableMap<String, Any>, imageView: ImageView) {}

private fun addTextAttributes(
    attributes: MutableMap<String, Any>,
    textView: TextView,
    scale: Float
) {
    val textColor = String.format("#%06X", 0xFFFFFF and textView.currentTextColor)
    attributes["color"] = textColor
    attributes["textAlign"] = textView.gravity.asHtmlTextAlignment()
    attributes["fontSize"] = textView.textSize.toPx(scale)
    attributes["isClickable"] = textView.isClickable
}

private fun addButtonAttributes(attributes: MutableMap<String, Any>, button: Button, scale: Float) {
    val textColor = String.format("#%06X", 0xFFFFFF and button.currentTextColor)
    attributes["label"] = button.text.toString()
    attributes["color"] = textColor
    attributes["fontSize"] = button.textSize.toPx(scale)
    attributes["textAlign"] = button.gravity.asHtmlTextAlignment()
}

private fun addLinearLayoutAttributes(
    attributes: MutableMap<String, Any>,
    linearLayout: LinearLayout
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        attributes["textAlign"] = linearLayout.gravity.asHtmlTextAlignment()
    }
}

// fun ComposeNode.toNode(scale: Float): Node? {
//     return null
// }
