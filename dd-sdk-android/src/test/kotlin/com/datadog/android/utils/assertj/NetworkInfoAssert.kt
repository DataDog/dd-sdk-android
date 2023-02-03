/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.datadog.android.v2.api.context.NetworkInfo
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class NetworkInfoAssert(actual: NetworkInfo) :
    AbstractObjectAssert<NetworkInfoAssert, NetworkInfo>(actual, NetworkInfoAssert::class.java) {

    fun hasConnectivity(expected: NetworkInfo.Connectivity): NetworkInfoAssert {
        assertThat(actual.connectivity)
            .overridingErrorMessage(
                "Expected networkInfo to have connectivity $expected " +
                    "but was ${actual.connectivity}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasCarrierName(expected: String?): NetworkInfoAssert {
        assertThat(actual.carrierName)
            .overridingErrorMessage(
                "Expected networkInfo to have carrierName $expected " +
                    "but was ${actual.carrierName}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasCarrierId(expected: Long?): NetworkInfoAssert {
        assertThat(actual.carrierId)
            .overridingErrorMessage(
                "Expected networkInfo to have carrierId $expected " +
                    "but was ${actual.carrierId}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasCellularTechnology(expected: String?): NetworkInfoAssert {
        assertThat(actual.cellularTechnology)
            .overridingErrorMessage(
                "Expected networkInfo to have cellularTechnology $expected " +
                    "but was ${actual.cellularTechnology}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUpSpeed(expected: Long?): NetworkInfoAssert {
        assertThat(actual.upKbps)
            .overridingErrorMessage(
                "Expected networkInfo to have upKbps $expected " +
                    "but was ${actual.upKbps}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDownSpeed(expected: Long?): NetworkInfoAssert {
        assertThat(actual.downKbps)
            .overridingErrorMessage(
                "Expected networkInfo to have downKbps $expected " +
                    "but was ${actual.downKbps}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasStrength(expected: Long?): NetworkInfoAssert {
        assertThat(actual.strength)
            .overridingErrorMessage(
                "Expected networkInfo to have strength $expected " +
                    "but was ${actual.strength}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(actual: NetworkInfo): NetworkInfoAssert =
            NetworkInfoAssert(actual)
    }
}
