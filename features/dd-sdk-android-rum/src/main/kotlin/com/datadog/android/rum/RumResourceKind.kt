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

    /**
     * Beacon type resource.
     */
    BEACON("beacon"),

    /**
     * Fetch type resource.
     */
    FETCH("fetch"),

    /**
     * XHR type resource.
     */
    XHR("xhr"),

    /**
     * Document type resource.
     */
    DOCUMENT("document"),

    // Common kinds

    /**
     * Native type resource.
     */
    NATIVE("native"),

    /**
     * Unknown type resource.
     */
    UNKNOWN("unknown"),

    /**
     * Image type resource.
     */
    IMAGE("image"),

    /**
     * JS type resource.
     */
    JS("js"),

    /**
     * Font type resource.
     */
    FONT("font"),

    /**
     * CSS type resource.
     */
    CSS("css"),

    /**
     * Media type resource.
     */
    MEDIA("media"),

    /**
     * Other type resource.
     */
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
