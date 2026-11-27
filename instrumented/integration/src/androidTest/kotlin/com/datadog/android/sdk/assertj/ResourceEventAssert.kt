/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.assertj

import com.datadog.android.rum.model.ResourceEvent
import org.assertj.core.api.AbstractObjectAssert

internal class ResourceEventAssert(actual: ResourceEvent) :
    AbstractObjectAssert<ResourceEventAssert, ResourceEvent>(
        actual,
        ResourceEventAssert::class.java
    ) {

    fun hasStatusCode(expected: Long?): ResourceEventAssert {
        if (actual.resource.statusCode != expected) {
            failWithMessage(
                "Expected resource.status_code to be <%s> but was <%s>",
                expected,
                actual.resource.statusCode
            )
        }
        return this
    }

    fun hasDeliveryType(expected: ResourceEvent.DeliveryType?): ResourceEventAssert {
        if (actual.resource.deliveryType != expected) {
            failWithMessage(
                "Expected resource.delivery_type to be <%s> but was <%s>",
                expected,
                actual.resource.deliveryType
            )
        }
        return this
    }

    fun hasTransferSize(expected: Long?): ResourceEventAssert {
        if (actual.resource.transferSize != expected) {
            failWithMessage(
                "Expected resource.transfer_size to be <%s> but was <%s>",
                expected,
                actual.resource.transferSize
            )
        }
        return this
    }

    fun hasPositiveTransferSize(): ResourceEventAssert {
        val transferSize = actual.resource.transferSize
        if (transferSize == null || transferSize <= 0L) {
            failWithMessage("Expected resource.transfer_size to be positive but was <%s>", transferSize)
        }
        return this
    }

    companion object {
        fun assertThat(actual: ResourceEvent): ResourceEventAssert {
            return ResourceEventAssert(actual)
        }
    }
}
