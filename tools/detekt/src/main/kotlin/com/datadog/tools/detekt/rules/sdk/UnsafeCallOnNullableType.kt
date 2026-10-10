/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtUnaryExpression

/**
 * The original UnsafeCallOnNullableType rule doesn't work with Android
 * projects using Kotlin (due to missing Type information).
 *
 * This rule will report any use of `!!`, even on non-nullable types.
 */
class UnsafeCallOnNullableType(
    config: Config = Config.empty
) : Rule(config, "This rule reports when an Unsafe null cast is used (ie: using !!).") {

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        super.visitUnaryExpression(expression)

        if (expression.operationToken == KtTokens.EXCLEXCL) {
            report(
                Finding(
                    Entity.from(expression),
                    "Calling !! on a nullable type will throw a " +
                        "NullPointerException at runtime in case the value is null. " +
                        "It must be avoided."
                )
            )
        }
    }
}
