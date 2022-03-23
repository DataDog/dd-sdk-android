/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.assertj

import org.assertj.core.api.ListAssert

/**
 *  Verifies that the actual list contains at least one object which is an instance
 *  of the provided class.
 *
 *  @param clazz the class object
 */
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
