/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.ext.isContainingEntryPointPublic
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtThrowExpression

/**
 * A rule to detekt thrown exceptions.
 * @active
 */
class ThrowingInternalException(
    config: Config = Config.empty
) : Rule(config, "This rule reports when an exception is thrown from a private or internal class.") {

    override fun visitThrowExpression(expression: KtThrowExpression) {
        if (!expression.isContainingEntryPointPublic()) {
            report(
                Finding(
                    Entity.from(expression),
                    message = "An exception is thrown from an internal or private part of the code."
                )
            )
        }
        super.visitThrowExpression(expression)
    }
}
