/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.pyramid

import com.datadog.tools.detekt.rules.AbstractTypedRule
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import io.gitlab.arturbosch.detekt.rules.isOverride
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import java.io.File

/**
 * @active
 */
@RequiresTypeResolution
class ApiSurface(
    ruleSetConfig: Config
) : AbstractTypedRule(ruleSetConfig) {

    private val outputFileName: String by config(defaultValue = "apiSurface.log")
    private val outputFile: File by lazy { File(outputFileName) }
    private val internalPackagePrefix: String by config(defaultValue = "")
    private val ignoredAnnotations: List<String> by config(defaultValue = emptyList())
    private val ignoredClasses: List<String> by config(defaultValue = emptyList())

    // region Rule

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Maintainability,
        "This rule reports api surface.",
        Debt.FIVE_MINS
    )

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        val hasIgnoredKeyword = ignoredKeywords.any { declaration.hasModifier(it) }
        val isIgnoredClass = declaration.fqName?.asString() in ignoredClasses
        val hasIgnoredAnnotation = declaration.annotationEntries.any {
            it.shortName?.asString()?.resolveFullType() in ignoredAnnotations
        }

        @Suppress("ComplexCondition")
        if (isIgnoredClass || hasIgnoredKeyword || hasIgnoredAnnotation) {
            return
        }

        super.visitObjectDeclaration(declaration)
    }

    override fun visitClass(klass: KtClass) {
        val hasIgnoredKeyword = ignoredKeywords.any { klass.hasModifier(it) }
        val isInterface = klass.isInterface()
        val isEnum = klass.isEnum()
        val isIgnoredClass = klass.fqName?.asString() in ignoredClasses
        val hasIgnoredAnnotation = klass.annotationEntries.any {
            it.shortName?.asString()?.resolveFullType() in ignoredAnnotations
        }

        @Suppress("ComplexCondition")
        if (isIgnoredClass || hasIgnoredKeyword || isInterface || isEnum || hasIgnoredAnnotation) {
            return
        }

        super.visitClass(klass)
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        val hasIgnoredKeyword = ignoredKeywords.any { constructor.hasModifier(it) }

        if (hasIgnoredKeyword) {
            return
        }

        val parentName = constructor.containingClassOrObject?.fqName
            ?: constructor.containingKtFile.packageFqName
        outputFile.appendText("$parentName.constructor(")

        val parameterList = constructor.getChildrenOfType<KtParameterList>().firstOrNull()
        parameterList?.children?.filterIsInstance<KtParameter>()?.forEachIndexed { idx, p ->
            val typeElement = p.typeReference?.typeElement
            if (idx > 0) outputFile.appendText(", ")
            outputFile.appendText(typeElement?.fullType() ?: "???")
        }

        outputFile.appendText(")\n")
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        val hasIgnoredKeyword = ignoredKeywords.any { function.hasModifier(it) }
        val hasIgnoredName = function.name in ignoredFunctionNames
        val hasIgnoredAnnotation = function.annotationEntries.any {
            it.shortName?.asString()?.resolveFullType() in ignoredAnnotations
        }
        val isOverride = function.isOverride()

        @Suppress("ComplexCondition")
        if (hasIgnoredKeyword || hasIgnoredName || hasIgnoredAnnotation || isOverride) {
            return
        }

        val containerFqName = if (function.isExtensionDeclaration()) {
            val receiverType = function.receiverTypeReference?.typeElement
            val target = if (receiverType is KtNullableType) {
                receiverType.innerType?.fullType()
            } else {
                receiverType?.fullType()
            }
            val parentType = function.typeParameters.filter { it.name == target }
                .map { it.extendsBound?.typeElement?.fullType() }
                .firstOrNull()
            parentType ?: target ?: "null"
        } else {
            function.containingClassOrObject?.fqName?.asString()
                ?: function.containingKtFile.packageFqName.asString()
        }

        if (internalPackagePrefix.isBlank() || containerFqName.startsWith(internalPackagePrefix)) {
            outputFile.appendText("$containerFqName.${function.nameAsSafeName}(")

            val parameterList = function.getChildrenOfType<KtParameterList>().firstOrNull()
            parameterList?.children?.filterIsInstance<KtParameter>()?.forEachIndexed { idx, p ->
                val typeElement = p.typeReference?.typeElement
                if (idx > 0) outputFile.appendText(", ")
                outputFile.appendText(typeElement?.fullType() ?: "???")
            }

            outputFile.appendText(")\n")
        }
    }

    // endregion

    // region Internal

    private fun KtTypeElement.fullType(): String {
        return when (this) {
            is KtNullableType -> innerType?.fullType() + "?"
            is KtUserType -> fullUserType()
            is KtFunctionType -> {
                val receiverType = receiver?.typeReference
                if (receiverType == null) {
                    "kotlin.Function${parameters.size}"
                } else {
                    "kotlin.Function${parameters.size + 1}"
                }
            }

            else -> "unknown type ${this.javaClass}"
        }
    }

    private fun KtUserType.fullUserType(): String {
        return referencedName?.resolveFullType() ?: text.resolveFullType()
    }

    // endregion

    companion object {
        private val ignoredFunctionNames = setOf("toString", "equals", "hashCode")

        private val ignoredKeywords = setOf(
            KtTokens.PRIVATE_KEYWORD,
            KtTokens.PROTECTED_KEYWORD,
            KtTokens.INTERNAL_KEYWORD
        )
    }
}
