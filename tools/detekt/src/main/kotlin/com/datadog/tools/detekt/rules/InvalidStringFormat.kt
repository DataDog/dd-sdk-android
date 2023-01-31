/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules

import com.datadog.tools.detekt.ext.fullType
import com.datadog.tools.detekt.ext.type
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VarargValueArgument
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.scopes.receivers.ClassQualifier
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.types.lowerIfFlexible
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.util.Locale

/**
 * A rule to detekt invalid String.format() calls (that is calls where the number of arguments
 * does not match the template string).
 *
 * @active
 */
class InvalidStringFormat : Rule() {

    // region Rule

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "This rule reports when a String format pattern does not match the provided arguments.",
        Debt.TWENTY_MINS
    )

    @Suppress("ReturnCount")
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (bindingContext == BindingContext.EMPTY) {
            return
        }

        val resolvedCall = expression.getResolvedCall(bindingContext) ?: return
        val call = resolvedCall.call

        // check receiver type
        val explicitReceiver = call.explicitReceiver
        val receiverType = explicitReceiver?.type(bindingContext)
        if (receiverType != STRING_CLASS) return

        // check method call
        val calleeExpression = call.calleeExpression?.node?.text
        if (calleeExpression != FORMAT_METHOD) return

        val formatString = extractFormatString(resolvedCall)
        val formatArgs = extractFormatArgs(resolvedCall)

        checkFormat(expression, formatString, formatArgs)
    }

    // endregion

    // region Internal

    private fun extractFormatString(
        resolvedCall: ResolvedCall<out CallableDescriptor>
    ): String? {
        val call = resolvedCall.call
        val explicitReceiver = call.explicitReceiver
        val stringExpression = when (explicitReceiver) {
            is ClassQualifier -> {
                // String.format(…)
                val arguments = resolvedCall.valueArguments.toList().firstOrNull {
                    it.first.type.fullType().toString() == STRING_CLASS
                }?.second?.arguments
                if (arguments != null && arguments.size == 1) {
                    arguments.first().getArgumentExpression()
                } else {
                    null
                }
            }
            is ExpressionReceiver -> {
                // "…".format(…)
                explicitReceiver.expression
            }
            else -> {
                println("Unknown receiver:$explicitReceiver")
                null
            }
        }

        val expression = if (stringExpression is KtDotQualifiedExpression) {
            // Referencing a constant from another class
            stringExpression.selectorExpression
        } else {
            stringExpression
        }
        return resolveStringExpression(expression)
    }

    private fun resolveStringExpression(stringExpression: KtExpression?): String? {
        return if (stringExpression is KtStringTemplateExpression) {
            stringExpression.node
                .children()
                .filter { it.text != "\"" }
                .joinToString("") { it.text }
        } else if (stringExpression is KtReferenceExpression) {
            val referenceTarget = stringExpression.getReferenceTargets(bindingContext)
                .firstIsInstanceOrNull<VariableDescriptor>()

            if (referenceTarget is VariableDescriptor && !referenceTarget.isVar) {
                val compileTimeInitializer = referenceTarget.compileTimeInitializer
                if (compileTimeInitializer != null) {
                    compileTimeInitializer.value as? String
                } else {
                    println("Unable to get compileTimeInitializer for $referenceTarget")
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

    private fun extractFormatArgs(
        resolvedCall: ResolvedCall<out CallableDescriptor>
    ): List<String?> {
        val receiver = resolvedCall.call.explicitReceiver
        val formatParams = resolvedCall.valueArgumentsByIndex?.last() as? VarargValueArgument

        val rawTypes = formatParams?.arguments.orEmpty()
            .map {
                it.getArgumentExpression()?.getType(bindingContext)?.lowerIfFlexible()?.fullType()
            }
        return if (receiver is ClassQualifier && rawTypes.firstOrNull() == LOCALE_CLASS) {
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
            report(CodeSmell(issue, Entity.from(expression), ERROR_UNKNOWN_FORMAT_STRING))
        } else if (formatArgs.isEmpty()) {
            report(CodeSmell(issue, Entity.from(expression), ERROR_UNKNOWN_FORMAT_STRING))
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
                report(CodeSmell(issue, Entity.from(expression), message))
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
            report(CodeSmell(issue, Entity.from(expression), message))
        } else if (allowedTypes != ANY_TYPES && argType !in allowedTypes) {
            val message = ERROR_INVALID_ARGUMENT_TYPE +
                " Expected one of ${allowedTypes.joinToString()}; but was $argType."
            report(CodeSmell(issue, Entity.from(expression), message))
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
