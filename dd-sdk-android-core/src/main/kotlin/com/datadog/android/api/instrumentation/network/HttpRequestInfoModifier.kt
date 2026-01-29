/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.api.instrumentation.network

/**
 * A builder interface for modifying [HttpRequestInfo] instances.
 * This interface allows modifying HTTP request properties such as URL, headers, and tags
 * before the request is processed by the instrumentation.
 *
 * Use [HttpRequestInfo.modify] to obtain a modifier instance.
 */
interface HttpRequestInfoModifier {

    /**
     * Sets the URL for this request.
     * @param url the new URL to set.
     * @return this modifier for chaining.
     */
    fun setUrl(url: String): HttpRequestInfoModifier

    /**
     * Adds a header with the specified key and values.
     * If a header with the same key already exists, the new values are appended.
     * @param key the header name.
     * @param values the header values.
     * @return this modifier for chaining.
     */
    fun addHeader(key: String, vararg values: String): HttpRequestInfoModifier

    /**
     * Removes a header with the specified key.
     * @param key the header name to remove.
     * @return this modifier for chaining.
     */
    fun removeHeader(key: String): HttpRequestInfoModifier

    /**
     * Replaces a header with the specified key and value.
     * This is equivalent to removing the existing header and adding a new one.
     * @param key the header name.
     * @param value the new header value.
     * @return this modifier for chaining.
     */
    fun replaceHeader(key: String, value: String) = apply {
        removeHeader(key)
        addHeader(key, value)
    }

    /**
     * Adds a tag of the specified type to this request.
     * Tags can be used to attach arbitrary metadata to requests for later retrieval.
     * @param T the type of the tag.
     * @param type the class representing the tag type.
     * @param tag the tag value, or null to remove the tag.
     * @return this modifier for chaining.
     */
    fun <T> addTag(type: Class<in T>, tag: T?): HttpRequestInfoModifier

    /**
     * Builds and returns the modified [HttpRequestInfo].
     * @return the resulting [HttpRequestInfo] with all modifications applied.
     */
    fun result(): HttpRequestInfo
}
