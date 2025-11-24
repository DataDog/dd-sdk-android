/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.assertj

import com.google.gson.internal.LazilyParsedNumber
import org.assertj.core.api.RecursiveComparisonAssert
import java.util.function.BiPredicate

/**
 * Custom predicate for comparing Gson's LazilyParsedNumber with Integer fields.
 *
 * See [withGsonIntEqualsForFields].
 */
private val lazilyParsedIntPredicate = BiPredicate { v1: LazilyParsedNumber, v2: Int -> v1.toInt() == v2 }

/**
 * Adds support for comparing Gson's LazilyParsedNumber with Integer fields
 * during AssertJ recursive comparison.
 *
 * Gson deserializes Integer values as LazilyParsedNumber. During recursive
 * comparison, AssertJ may attempt reflective field comparison (e.g. Integer.value),
 * which fails because the field is private. This forces value-based comparison instead.
 */
fun<T : RecursiveComparisonAssert<T>> RecursiveComparisonAssert<T>.withGsonIntEqualsForFields(
    vararg fieldNames: String
): RecursiveComparisonAssert<T> {
    return this.withEqualsForFields(lazilyParsedIntPredicate, *fieldNames)
}
