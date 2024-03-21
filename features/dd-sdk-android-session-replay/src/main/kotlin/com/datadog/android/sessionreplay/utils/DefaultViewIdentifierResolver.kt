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
object DefaultViewIdentifierResolver : ViewIdentifierResolver {

    internal const val DATADOG_UNIQUE_IDENTIFIER_KEY_PREFIX = "DATADOG_UNIQUE_IDENTIFIER_"
    private val secureRandom = SecureRandom()

    override fun resolveViewId(view: View): Long {
        // we will use the System.identityHashcode in here which returns a consistent
        // value for an instance even when it is mutable
        return System.identityHashCode(view).toLong()
    }

    override fun resolveChildUniqueIdentifier(parent: View, childName: String): Long? {
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
