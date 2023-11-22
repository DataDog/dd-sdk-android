/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.view.View
import java.security.SecureRandom

/**
 * Unique Identifier Generator.
 * This class is meant for internal usage so please use it with careful as it might change in time.
 */
object UniqueIdentifierGenerator {

    internal const val DATADOG_UNIQUE_IDENTIFIER_KEY_PREFIX = "DATADOG_UNIQUE_IDENTIFIER_"
    private val secureRandom = SecureRandom()

    /**
     * Generates a persistent unique identifier for a virtual child view based on its unique
     * name and its physical parent. The identifier will only be created once and persisted in
     * the parent [View] tag to provide consistency. In case there was already a value with the
     * same key in the tags and this was used by a different party we will try to use this value
     * as identifier if it's a [Long], in other case we will return null. This last scenario is
     * highly unlikely but we are doing this in order to safely treat possible collisions with
     * client tags.
     * @param parent [View] of this virtual child
     * @param childName as the unique name of the virtual child
     * @return the unique identifier as [Long] or null if the identifier could not be created
     */
    fun resolveChildUniqueIdentifier(parent: View, childName: String): Long? {
        val key = (DATADOG_UNIQUE_IDENTIFIER_KEY_PREFIX + childName).hashCode()
        val uniqueIdentifier = parent.getTag(key)
        return if (uniqueIdentifier != null) {
            // the identifier key was registered already by us or because of a collision with
            // client's key. In case was a collision we try our luck and check if the value
            // was a long and use that as an identifier.
            uniqueIdentifier as? Long
        } else {
            val newUniqueIdentifier = secureRandom.nextInt().toLong()
            parent.setTag(key, newUniqueIdentifier)
            newUniqueIdentifier
        }
    }
}
