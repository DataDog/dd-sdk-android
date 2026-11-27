/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.assertj

import com.datadog.android.sdk.integration.network.models.ObservedCronetResponse
import org.assertj.core.api.AbstractObjectAssert

internal class ObservedCronetResponseAssert(actual: ObservedCronetResponse) :
    AbstractObjectAssert<ObservedCronetResponseAssert, ObservedCronetResponse>(
        actual,
        ObservedCronetResponseAssert::class.java
    ) {

    fun isCached(): ObservedCronetResponseAssert {
        if (!actual.wasCached) {
            failWithMessage("Expected the response to be served from the cache but it wasn't: <%s>", actual)
        }
        return this
    }

    fun hasUrlChainSize(expected: Int): ObservedCronetResponseAssert {
        if (actual.urlChainSize != expected) {
            failWithMessage("Expected the URL chain to have <%s> entries: <%s>", expected, actual)
        }
        return this
    }

    fun hasReceivedByteCount(expected: Long): ObservedCronetResponseAssert {
        if (actual.receivedByteCount != expected) {
            failWithMessage("Expected the received byte count to be <%s>: <%s>", expected, actual)
        }
        return this
    }

    companion object {
        fun assertThat(actual: ObservedCronetResponse): ObservedCronetResponseAssert {
            return ObservedCronetResponseAssert(actual)
        }
    }
}
