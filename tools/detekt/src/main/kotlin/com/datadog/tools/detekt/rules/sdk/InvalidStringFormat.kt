/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.ext.fqTypeName
import com.datadog.tools.detekt.ext.unwrapSmartCast
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.children
import java.util.Locale

/**
 * A rule to detekt invalid String.format() calls (that is calls where the number of arguments
 * does not match the template string).
 *
 * @active
 */
class InvalidStringFormat(
    config: Config = Config.empty
) : Rule(config, "This rule reports when a String format pattern does not match the provided arguments."),
    RequiresAnalysisApi {

    /**
     * The format string and argument types resolved for a `format` call.
     *
     * @param formatString the format pattern, when it could be resolved
     * @param formatArgs the fully qualified type names of the arguments passed to the pattern
     */
    private data class ResolvedFormatCall(
        val formatString: String?,
        val formatArgs: List<String?>
    )

    // region Rule

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        // check method call
        if (expression.calleeExpression?.text != FORMAT_METHOD) return

        val resolved = analyze(expression) { resolveFormatCall(expression) } ?: return

        checkFormat(expression, resolved.formatString, resolved.formatArgs)
    }

    // endregion

    // region Internal

    @Suppress("ReturnCount")
    private fun KaSession.resolveFormatCall(expression: KtCallExpression): ResolvedFormatCall? {
        val call = expression.resolveToCall()?.singleFunctionCallOrNull() ?: return null

        val receiver = call.extensionReceiver ?: call.dispatchReceiver ?: return null

        // check receiver type. `"…".format(…)` is an extension on String, while `String.format(…)`
        // is an extension on String's companion object.
        val receiverTypeName = fqTypeName(receiver.type).removeSuffix("?")
        val isCompanionReceiver = receiverTypeName == STRING_COMPANION_CLASS
        if (receiverTypeName != STRING_CLASS && !isCompanionReceiver) return null

        return ResolvedFormatCall(
            formatString = extractFormatString(call, receiver, isCompanionReceiver),
            formatArgs = extractFormatArgs(call, isCompanionReceiver)
        )
    }

    private fun KaSession.extractFormatString(
        call: KaFunctionCall<*>,
        receiver: KaReceiverValue,
        isCompanionReceiver: Boolean
    ): String? {
        val stringExpression = if (isCompanionReceiver) {
            // String.format(…): the pattern is the `format` parameter
            call.valueArgumentMapping
                .filterValues { !it.symbol.isVararg && fqTypeName(it.returnType) == STRING_CLASS }
                .keys
                .singleOrNull()
        } else {
            // "…".format(…): the pattern is the receiver itself
            (receiver.unwrapSmartCast() as? KaExplicitReceiverValue)?.expression
        }

        val expression = if (stringExpression is KtDotQualifiedExpression) {
            // Referencing a constant from another class
            stringExpression.selectorExpression
        } else {
            stringExpression
        }
        return resolveStringExpression(expression)
    }

    private fun KaSession.resolveStringExpression(stringExpression: KtExpression?): String? {
        return if (stringExpression is KtStringTemplateExpression) {
            stringExpression.node
                .children()
                .filter { it.text != "\"" }
                .joinToString("") { it.text }
        } else if (stringExpression is KtReferenceExpression) {
            val constantValue = stringExpression.evaluate()?.value as? String
            if (constantValue == null) {
                println("Unable to resolve a constant value for $stringExpression")
            }
            constantValue
        } else {
            println("Unknown string expression: $stringExpression")
            null
        }
    }

    private fun KaSession.extractFormatArgs(
        call: KaFunctionCall<*>,
        isCompanionReceiver: Boolean
    ): List<String?> {
        val rawTypes = call.valueArgumentMapping
            .filterValues { it.symbol.isVararg }
            .keys
            .map { argument -> argument.expressionType?.let { fqTypeName(it) } }

        return if (isCompanionReceiver && rawTypes.firstOrNull() == LOCALE_CLASS) {
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
        private const val STRING_COMPANION_CLASS = "kotlin.String.Companion"
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
