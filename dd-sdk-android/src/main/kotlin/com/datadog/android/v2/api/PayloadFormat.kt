/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

/**
 * Describes the format of a payload for an instance of [SDKFeature].
 */
interface PayloadFormat {

    /**
     * @return the prefix of a payload, appended before the first item in the payload.
     */
    fun prefixBytes(): ByteArray

    /**
     * @return the suffix of a payload, appended after the last item in the payload.
     */
    fun suffixBytes(): ByteArray

    /**
     * @return the separator of a payload, appended between each item in the payload.
     */
    fun separatorBytes(): ByteArray
}
