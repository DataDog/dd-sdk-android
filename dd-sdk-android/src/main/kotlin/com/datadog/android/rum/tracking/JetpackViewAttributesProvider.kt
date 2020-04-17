package com.datadog.android.rum.tracking

import android.content.res.Resources
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.rum.RumAttributes

/**
 * Provides extra attributes for the touch target View.
 * Those special attributes are specifically related with Jetpack components.
 * <ul>
 *     <li> If the parent of the target view is a RecyclerView it will add the position
 *     of the target inside the adapter attribute together
 *     with the container class name and resource id </li>
 * </ul>
 * @see [RumAttributes.TAG_TARGET_POSITION_IN_SCROLLABLE_CONTAINER]
 * @see [RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_CLASS_NAME]
 * @see [RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_RESOURCE_ID]
 */
class JetpackViewAttributesProvider : ViewAttributesProvider {

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
                attributes[RumAttributes.TAG_TARGET_POSITION_IN_SCROLLABLE_CONTAINER] =
                    positionInAdapter
                attributes[RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_CLASS_NAME] =
                    parent.javaClass.canonicalName
                attributes[RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_RESOURCE_ID] =
                    resolveIdOrResourceName(parent)
                break
            }
            child = parent as? View
            parent = parent.parent
        }
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

    private fun viewIdAsHexa(view: View) = "0x${view.id.toString(16)}"

    // endregion
}
