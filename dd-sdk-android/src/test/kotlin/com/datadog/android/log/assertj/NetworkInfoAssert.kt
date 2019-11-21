/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.assertj

import com.datadog.android.log.internal.net.NetworkInfo
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions

internal class NetworkInfoAssert(actual: NetworkInfo) :
    AbstractObjectAssert<NetworkInfoAssert, NetworkInfo>(actual, NetworkInfoAssert::class.java) {

    fun hasConnectivity(expected: NetworkInfo.Connectivity): NetworkInfoAssert {
        Assertions.assertThat(actual.connectivity)
            .overridingErrorMessage(
                "Expected networkInfo to have connectivity $expected but was ${actual.connectivity}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasCarrierName(expected: String?): NetworkInfoAssert {
        Assertions.assertThat(actual.carrierName)
            .overridingErrorMessage(
                "Expected networkInfo to have carrierName $expected but was ${actual.carrierName}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasCarrierId(expected: Int): NetworkInfoAssert {
        Assertions.assertThat(actual.carrierId)
            .overridingErrorMessage(
                "Expected networkInfo to have carrierId $expected but was ${actual.carrierId}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(actual: NetworkInfo): NetworkInfoAssert =
            NetworkInfoAssert(actual)
    }
}
