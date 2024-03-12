/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

/**
 * A utility interface to convert Android/JVM colors to web hexadecimal strings.
 * This interface is meant for internal usage, please use it carefully.
 */
interface ColorStringFormatter {

    /**
     * Converts a color as an int to a standard web hexadecimal representation, as RGBA (e.g.: #A538AFFF).
     * @param color the color value (with or without alpha in the first 8 bits)
     * @return new color value as a HTML formatted hexadecimal String
     */
    fun formatColorAsHexString(color: Int): String

    /**
     * Converts a color as an int to a standard web hexadecimal representation, as RGBA (e.g.: #A538AFFF).
     * If also overrides the color's alpha channel
     * @param color the color value (with or without alpha in the first 8 bits)
     * @param alpha the override alpha in a [O…255] range
     * @return new color value as a HTML formatted hexadecimal String
     */
    fun formatColorAndAlphaAsHexString(color: Int, alpha: Int): String
}
