/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.trace.TracingHeaderType
import okhttp3.HttpUrl

/**
 * Interface to be implemented by the class which wants to check if the given url is first party
 * and if there is any tracing header types associated with it.
 */
@Suppress("PackageNameVisibility") // Can't mark it as @InternalApi as it would apply to implementations as well
interface FirstPartyHostHeaderTypeResolver {

    /**
     * Check if given URL is first party.
     *
     * @param url URL to check.
     */
    fun isFirstPartyUrl(url: HttpUrl): Boolean

    /**
     * Check if given URL is first party.
     *
     * @param url URL to check.
     */
    fun isFirstPartyUrl(url: String): Boolean

    /**
     * Returns the set of tracing header types associated with given URL.
     *
     * @param url URL to check.
     */
    fun headerTypesForUrl(url: String): Set<TracingHeaderType>

    /**
     * Returns the set of tracing header types associated with given URL.
     *
     * @param url URL to check.
     */
    fun headerTypesForUrl(url: HttpUrl): Set<TracingHeaderType>

    /**
     * Returns all tracing header types registered.
     */
    fun getAllHeaderTypes(): Set<TracingHeaderType>

    /**
     * Shows if resolver has any first party URLs registered or not.
     */
    fun isEmpty(): Boolean
}
