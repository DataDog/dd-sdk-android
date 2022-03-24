/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tv

import android.view.View
import androidx.leanback.widget.Action
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.rum.tracking.ViewAttributesProvider

/**
 * Provides extra attributes for the touch target View.
 * Those special attributes are specifically related with Android TV [Action].
 * @see [ACTION_TARGET_ACTION_ID]
 * @see [ACTION_TARGET_LABEL1]
 * @see [ACTION_TARGET_LABEL2]
 */
class LeanbackViewAttributesProvider :
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
            if (parent is RecyclerView && child != null) {
                addActionInfoIfAny(parent, child, attributes)
                break
            }
            child = parent as? View
            parent = parent.parent
        }
    }

    // endregion

    // region Object

    private fun addActionInfoIfAny(
        parent: RecyclerView,
        child: View,
        attributes: MutableMap<String, Any?>
    ) {
        parent.findAction(child)?.let {
            attributes[ACTION_TARGET_ACTION_ID] = it.id
            it.label1?.let {
                attributes[ACTION_TARGET_LABEL1] = it
            }
            it.label2?.let {
                attributes[ACTION_TARGET_LABEL2] = it
            }
        }
    }

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

    private fun RecyclerView.ViewHolder.isBridgeAdapterViewHolder(): Boolean {
        return ItemBridgeAdapter.ViewHolder::class.java.isAssignableFrom(this::class.java)
    }

    private fun RecyclerView.findActionViewHolder(view: View): ItemBridgeAdapter.ViewHolder? {
        val viewHolder = findContainingViewHolder(view)
        if (viewHolder?.isBridgeAdapterViewHolder() == true) {
            return viewHolder as ItemBridgeAdapter.ViewHolder
        }
        return null
    }

    private fun RecyclerView.findAction(view: View): Action? {
        return findActionViewHolder(view)?.action()
    }

    private fun ItemBridgeAdapter.ViewHolder.action(): Action? {
        if (Action::class.java.isAssignableFrom(item::class.java)) {
            return item as Action
        }
        return null
    }

    // endregion

    companion object {

        /**
         * The action id assigned for the current target.
         * @see [Action]
         */
        const val ACTION_TARGET_ACTION_ID: String = "action.target.actionid"

        /**
         * The label displayed on the first line of the current target Action.
         * @see [Action]
         */
        const val ACTION_TARGET_LABEL1: String = "action.target.label1"

        /**
         * The label displayed on the second line of the current target Action.
         * @see [Action]
         */
        const val ACTION_TARGET_LABEL2: String = "action.target.label2"
    }
}
