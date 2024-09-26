/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.view.View
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.PrivacyLevel
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

internal object PrivacyOverrideManager {
    private val overridesMap = ConcurrentHashMap<WeakViewKey, PrivacyOverride>()

    internal fun addPrivacyOverride(view: View, level: PrivacyLevel?) {
        val key = WeakViewKey(view)
        val existingPrivacy = overridesMap[key] ?: PrivacyOverride()

        overridesMap[key] =
            when (level) {
                is ImagePrivacy -> existingPrivacy.copy(
                    imagePrivacy = level
                )

                is TextAndInputPrivacy -> existingPrivacy.copy(
                    textAndInputPrivacy = level
                )

                is TouchPrivacy -> existingPrivacy.copy(
                    touchPrivacy = level
                )

                else -> return
            }
    }

    internal fun removeTextAndInputPrivacyOverride(view: View) {
        val key = WeakViewKey(view)
        val existingPrivacy = overridesMap[key] ?: return

        overridesMap[key] = existingPrivacy.copy(
            textAndInputPrivacy = null
        )
    }

    internal fun removeTouchPrivacyOverride(view: View) {
        val key = WeakViewKey(view)
        val existingPrivacy = overridesMap[key] ?: return

        overridesMap[key] = existingPrivacy.copy(
            touchPrivacy = null
        )
    }

    internal fun removeImagePrivacyOverride(view: View) {
        val key = WeakViewKey(view)
        val existingPrivacy = overridesMap[key] ?: return

        overridesMap[key] = existingPrivacy.copy(
            imagePrivacy = null
        )
    }

    internal fun addHiddenOverride(view: View) {
        val key = WeakViewKey(view)
        val existingPrivacy = overridesMap[key] ?: PrivacyOverride()

        overridesMap[key] = existingPrivacy.copy(
            hiddenPrivacy = true
        )
    }

    internal fun removeHiddenOverride(view: View) {
        val key = WeakViewKey(view)
        val existingPrivacy = overridesMap[key] ?: return

        overridesMap[key] = existingPrivacy.copy(
            hiddenPrivacy = false
        )
    }

    internal fun getPrivacyOverrides(view: View): PrivacyOverride? {
        val key = WeakViewKey(view)
        return overridesMap[key]
    }

    internal fun isHidden(view: View): Boolean {
        val key = WeakViewKey(view)
        return overridesMap[key]?.hiddenPrivacy == true
    }

    private class WeakViewKey(view: View) : WeakReference<View>(view) {
        override fun equals(other: Any?): Boolean {
            val otherValue = (other as? WeakViewKey)?.get()
            return get() == otherValue
        }

        override fun hashCode(): Int {
            return get()?.hashCode() ?: 0
        }
    }
}
