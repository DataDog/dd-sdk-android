/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import java.util.Locale

/**
 * Describe the category of a RUM Resource.
 * @see [RumMonitor]
 */
enum class RumResourceKind(internal val value: String) {
    // Specific kind of JS resources loading
    BEACON("beacon"),
    FETCH("fetch"),
    XHR("xhr"),
    DOCUMENT("document"),

    // Common kinds
    NATIVE("native"),
    UNKNOWN("unknown"),
    IMAGE("image"),
    JS("js"),
    FONT("font"),
    CSS("css"),
    MEDIA("media"),
    OTHER("other");

    companion object {

        /**
         * Converts string representation of MIME type into [RumResourceKind].
         *
         * @param mimeType MIME type to convert. If it is unknown, [NATIVE] will be returned.
         */
        fun fromMimeType(mimeType: String): RumResourceKind {
            val baseType = mimeType.substringBefore('/').lowercase(Locale.US)
            val subtype = mimeType.substringAfter('/').substringBefore(';').lowercase(Locale.US)

            return when {
                baseType == "image" -> IMAGE
                baseType == "video" || baseType == "audio" -> MEDIA
                baseType == "font" -> FONT
                baseType == "text" && subtype == "css" -> CSS
                baseType == "text" && subtype == "javascript" -> JS
                else -> NATIVE
            }
        }
    }
}
