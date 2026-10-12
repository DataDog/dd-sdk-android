/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.ext.ExplicitReceiver
import com.datadog.tools.detekt.ext.explicitReceiver
import com.datadog.tools.detekt.ext.fqTypeName
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.children
import java.util.Locale

/**
 * A rule to detekt invalid String.format() calls (that is calls where the number of arguments
 * does not match the template string).
 *
 * @active
 */
class InvalidStringFormat(
    config: Config
) : Rule(config, "This rule reports when a String format pattern does not match the provided arguments."),
    RequiresAnalysisApi {

    // region Rule

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val formatCall = analyze(expression) { resolveFormatCall(expression) } ?: return

        checkFormat(expression, formatCall.formatString, formatCall.formatArgs)
    }

    // endregion

    // region Internal

    private class FormatCall(
        val formatString: String?,
        val formatArgs: List<String?>
    )

    @Suppress("ReturnCount")
    private fun KaSession.resolveFormatCall(expression: KtCallExpression): FormatCall? {
        val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null

        // check receiver type
        val explicitReceiver = explicitReceiver(
            expression,
            call.partiallyAppliedSymbol.signature.symbol,
            treatGenericAsSuper = true,
            includeTypeArguments = true
        )
        if (explicitReceiver?.typeName != STRING_CLASS) return null

        // check method call
        val calleeExpression = expression.calleeExpression?.node?.text
        if (calleeExpression != FORMAT_METHOD) return null

        return FormatCall(
            formatString = extractFormatString(expression, call, explicitReceiver),
            formatArgs = extractFormatArgs(expression, call, explicitReceiver)
        )
    }

    private fun KaSession.extractFormatString(
        expression: KtCallExpression,
        call: KaFunctionCall<*>,
        explicitReceiver: ExplicitReceiver
    ): String? {
        val stringExpression = when (explicitReceiver) {
            is ExplicitReceiver.Qualifier -> {
                // String.format(…)
                val formatParameter = call.partiallyAppliedSymbol.signature.valueParameters.firstOrNull {
                    fqTypeName(it.returnType) == STRING_CLASS
                }
                val arguments = call.argumentMapping.filterValues { it.symbol == formatParameter?.symbol }.keys
                if (formatParameter != null && arguments.size == 1) {
                    arguments.first()
                } else {
                    null
                }
            }

            is ExplicitReceiver.Expression -> {
                // "…".format(…)
                explicitReceiver.expression
            }
        }

        val resolvedExpression = if (stringExpression is KtDotQualifiedExpression) {
            // Referencing a constant from another class
            stringExpression.selectorExpression
        } else {
            stringExpression
        }
        return resolveStringExpression(resolvedExpression)
    }

    private fun KaSession.resolveStringExpression(stringExpression: KtExpression?): String? {
        return if (stringExpression is KtStringTemplateExpression) {
            stringExpression.evaluate()?.value as? String
                ?: stringExpression.node
                    .children()
                    .filter { it.text != "\"" }
                    .joinToString("") { it.text }
        } else if (stringExpression is KtReferenceExpression) {
            val referenceTarget = stringExpression.references
                .filterIsInstance<KtReference>()
                .firstNotNullOfOrNull { it.resolveToSymbol() as? KaVariableSymbol }

            if (referenceTarget != null && referenceTarget.isVal) {
                val compileTimeValue = stringExpression.evaluate()?.value
                    ?: (referenceTarget.psi as? KtVariableDeclaration)?.initializer?.evaluate()?.value
                if (compileTimeValue != null) {
                    compileTimeValue as? String
                } else {
                    println("Unable to get compile time value for $referenceTarget")
                    null
                }
            } else {
                println("Unknown referenceTarget:$referenceTarget")
                null
            }
        } else {
            println("Unknown string expression: $stringExpression")
            null
        }
    }

    private fun KaSession.extractFormatArgs(
        expression: KtCallExpression,
        call: KaFunctionCall<*>,
        explicitReceiver: ExplicitReceiver
    ): List<String?> {
        val formatParameter = call.partiallyAppliedSymbol.signature.valueParameters.lastOrNull()
            ?.takeIf { it.symbol.isVararg }

        val rawTypes = expression.valueArguments
            .mapNotNull { it.getArgumentExpression() }
            .filter { formatParameter != null && call.argumentMapping[it]?.symbol == formatParameter.symbol }
            .map { argument -> argument.expressionType?.lowerBoundIfFlexible()?.let { fqTypeName(it) } }
        return if (explicitReceiver is ExplicitReceiver.Qualifier && rawTypes.firstOrNull() == LOCALE_CLASS) {
            rawTypes.drop(1)
        } else {
            rawTypes
        }
    }

    private fun checkFormat(
        expression: KtCallExpression,
        formatString: String?,
        formatArgs: List<String?>
    ) {
        if (formatString == null) {
            report(Finding(Entity.from(expression), ERROR_UNKNOWN_FORMAT_STRING))
        } else if (formatArgs.isEmpty()) {
            report(Finding(Entity.from(expression), ERROR_UNKNOWN_FORMAT_STRING))
        } else {
            val specifiers = SPECIFIER_REGEX.findAll(formatString).toList()
            checkSpecifiers(expression, specifiers, formatArgs)
        }
    }

    private fun checkSpecifiers(
        expression: KtCallExpression,
        specifiers: List<MatchResult>,
        formatArgs: List<String?>
    ) {
        var indexNoRef = 0
        specifiers.forEach { matchResult ->
            val type = matchResult.groupValues[INDEX_TYPE].lowercase(Locale.US).first()
            val ref = matchResult.groupValues[INDEX_REF].toIntOrNull()

            val argIndex = (ref ?: ++indexNoRef) - 1
            if (argIndex >= formatArgs.size) {
                val message = ERROR_INVALID_ARGUMENT_COUNT.format(Locale.US, matchResult.value)
                report(Finding(Entity.from(expression), message))
            } else {
                val argType = formatArgs[argIndex]
                checkArgumentType(expression, argType, type)
            }
        }
    }

    @Suppress("ReplaceArrayEqualityOpWithArraysEquals")
    private fun checkArgumentType(
        expression: KtCallExpression,
        argType: String?,
        type: Char
    ) {
        val allowedTypes: Array<String>? = when (type) {
            SPECIFIER_DECIMAL_INT,
            SPECIFIER_OCTAL_INT,
            SPECIFIER_HEXADECIMAL_INT -> INTEGER_TYPES

            SPECIFIER_DECIMAL_FLOAT,
            SPECIFIER_SCIENTIFIC_FLOAT,
            SPECIFIER_HEXADECIMAL_FLOAT,
            SPECIFIER_GENERAL_FLOAT -> FLOAT_TYPES

            SPECIFIER_CHARACTER -> CHAR_TYPES

            SPECIFIER_BOOLEAN,
            SPECIFIER_HASHCODE,
            SPECIFIER_STRING -> ANY_TYPES

            else -> null
        }

        if (allowedTypes == null) {
            val message = ERROR_INVALID_ARGUMENT_TYPE +
                "Unknown specifier %$type."
            report(Finding(Entity.from(expression), message))
        } else if (!allowedTypes.contentEquals(ANY_TYPES) && argType !in allowedTypes) {
            val message = ERROR_INVALID_ARGUMENT_TYPE +
                " Expected one of ${allowedTypes.joinToString()}; but was $argType."
            report(Finding(Entity.from(expression), message))
        }
    }

    // endregion

    companion object {
        private const val LOCALE_CLASS = "java.util.Locale"
        private const val STRING_CLASS = "kotlin.String"
        private const val FORMAT_METHOD = "format"

        private const val SPECIFIER_STRING = 's'
        private const val SPECIFIER_BOOLEAN = 'b'
        private const val SPECIFIER_HASHCODE = 'h'

        private const val SPECIFIER_CHARACTER = 'c'

        private const val SPECIFIER_DECIMAL_INT = 'd'
        private const val SPECIFIER_OCTAL_INT = 'o'
        private const val SPECIFIER_HEXADECIMAL_INT = 'x'

        private const val SPECIFIER_SCIENTIFIC_FLOAT = 'e'
        private const val SPECIFIER_GENERAL_FLOAT = 'g'
        private const val SPECIFIER_DECIMAL_FLOAT = 'f'
        private const val SPECIFIER_HEXADECIMAL_FLOAT = 'a'

        private val INTEGER_TYPES = arrayOf(
            "kotlin.Byte",
            "kotlin.Short",
            "kotlin.Int",
            "kotlin.Long",
            "java.math.BigInteger",
            "kotlin.Byte?",
            "kotlin.Short?",
            "kotlin.Int?",
            "kotlin.Long?",
            "java.math.BigInteger?"
        )
        private val FLOAT_TYPES = arrayOf(
            "kotlin.Float",
            "kotlin.Double",
            "java.math.BigDecimal",
            "kotlin.Float?",
            "kotlin.Double?",
            "java.math.BigDecimal?"
        )
        private val CHAR_TYPES = arrayOf(
            "kotlin.Byte",
            "kotlin.Short",
            "kotlin.Int",
            "kotlin.Char",
            "kotlin.Byte?",
            "kotlin.Short?",
            "kotlin.Int?",
            "kotlin.Char?"
        )
        private val ANY_TYPES = emptyArray<String>()

        // %[ref][flags][width][.precision]type
        private val SPECIFIER_REGEX = Regex(
            "%(\\d+\\$)?([flags]+)?(\\d+)?(\\.\\d+)?([sbhcdoxegfa])"
        )
        private const val INDEX_REF = 1
        private const val INDEX_TYPE = 5

        private const val ERROR_UNKNOWN_FORMAT_STRING = "Unable to detect the format string value."
        private const val ERROR_INVALID_ARGUMENT_COUNT = "An argument is missing for specifier " +
            "'%s' in the format String."
        private const val ERROR_INVALID_ARGUMENT_TYPE = "Argument provided doesn't match the " +
            "type specifier in the format String."
    }
}
