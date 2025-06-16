/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.content.res.Resources
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.internal.utils.toHexString
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.tracking.ViewAttributesProvider

/**
 * Provides extra attributes for the touch target View.
 * Those special attributes are specifically related with Jetpack components.
 * <ul>
 *     <li> If the parent of the target view is a RecyclerView it will add the position
 *     of the target inside the adapter attribute together
 *     with the container class name and resource id </li>
 * </ul>
 * @see [RumAttributes.ACTION_TARGET_PARENT_INDEX]
 * @see [RumAttributes.ACTION_TARGET_PARENT_CLASSNAME]
 * @see [RumAttributes.ACTION_TARGET_PARENT_RESOURCE_ID]
 */
internal class JetpackViewAttributesProvider :
    ViewAttributesProvider {

    // region ViewAttributesProvider

    override fun extractAttributes(
        view: View,
        attributes: MutableMap<String, Any?>
    ) {
        // traverse the target parents
        var parent = view.parent
        var child: View? = view
        while (parent != null) {
            if (parent is RecyclerView && child != null && isDirectChildOfRecyclerView(child)) {
                val positionInAdapter = parent.getChildAdapterPosition(child)
                attributes[RumAttributes.ACTION_TARGET_PARENT_INDEX] =
                    positionInAdapter
                attributes[RumAttributes.ACTION_TARGET_PARENT_CLASSNAME] =
                    parent.javaClass.canonicalName
                attributes[RumAttributes.ACTION_TARGET_PARENT_RESOURCE_ID] =
                    resolveIdOrResourceName(parent)
                break
            }
            child = parent as? View
            parent = parent.parent
        }
    }

    // endregion

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    // endregion

    // region Internal

    private fun isDirectChildOfRecyclerView(child: View): Boolean {
        return child.layoutParams is RecyclerView.LayoutParams
    }

    private fun resolveIdOrResourceName(view: View): String {
        @Suppress("SwallowedException")
        return try {
            view.resources.getResourceEntryName(view.id) ?: viewIdAsHexa(view)
        } catch (e: Resources.NotFoundException) {
            viewIdAsHexa(view)
        }
    }

    private fun viewIdAsHexa(view: View) = "0x${view.id.toHexString()}"

    // endregion
}
