/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.assertj

import org.assertj.core.api.ListAssert

fun <T> ListAssert<T>.containsInstanceOf(clazz: Class<*>): ListAssert<T> {

    overridingErrorMessage(
        "Expected list to have at least one instance of ${clazz.simpleName} " +
            "but found none."
    )
        .matches { list ->
            list.any { clazz.isInstance(it) }
        }

    return this
}
