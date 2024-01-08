/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.Activity
import android.content.ComponentName
import androidx.navigation.ActivityNavigator
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator

/**
 * Uniquely identifier to link multiple events relating to the same entity.
 * This fixes an issue where we would keep (weak) references to activities/fragments
 *
 */
internal data class RumScopeKey(
    val id: String,
    val url: String,
    val name: String?
) {

    companion object {
        fun from(key: Any): RumScopeKey {
            val componentName = resolveComponent(key)
            return if (componentName == null) {
                val id = resolveId(key)
                val url = resolveUrl(key)
                val name = resolveName(key)
                RumScopeKey(id, url, name)
            } else {
                val id = "${componentName.className}@${System.identityHashCode(key)}"
                val url = resolveComponentUrl(componentName)
                val name = componentName.className
                RumScopeKey(id, url, name)
            }
        }

        private fun resolveId(key: Any): String {
            return when (key) {
                is String -> key

                is DialogFragmentNavigator.Destination -> "${key.className}#${key.id}"
                is FragmentNavigator.Destination -> "${key.className}#${key.id}"

                else -> "${key.javaClass.name}@${System.identityHashCode(key)}"
            }
        }

        private fun resolveUrl(key: Any): String {
            return when (key) {
                is String -> key

                is DialogFragmentNavigator.Destination -> key.className
                is FragmentNavigator.Destination -> key.className

                else -> key.javaClass.name
            }
        }

        private fun resolveName(key: Any): String {
            return when (key) {
                is String -> key

                is DialogFragmentNavigator.Destination -> key.className
                is FragmentNavigator.Destination -> key.className

                else -> key.javaClass.name
            }
        }

        private fun resolveComponentUrl(key: ComponentName): String {
            return when {
                key.packageName.isEmpty() -> key.className
                key.className.startsWith("${key.packageName}.") -> key.className
                key.className.contains('.') -> key.className
                else -> "${key.packageName}.${key.className}"
            }
        }

        private fun resolveComponent(key: Any): ComponentName? {
            return when (key) {
                is Activity -> key.componentName
                is ActivityNavigator.Destination -> key.component
                else -> null
            }
        }
    }
}
