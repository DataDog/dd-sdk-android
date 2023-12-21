/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
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

    // region Rule

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Maintainability,
        "This rule reports api surface.",
        Debt.FIVE_MINS
    )

    override fun visitClass(klass: KtClass) {
        if (klass.hasModifier(KtTokens.PRIVATE_KEYWORD) || klass.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
            return
        }
        if (klass.isInterface()) {
            return
        }
        super.visitClass(klass)
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        if (constructor.hasModifier(KtTokens.PRIVATE_KEYWORD) || constructor.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
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

    @Suppress("ReturnCount")
    override fun visitNamedFunction(function: KtNamedFunction) {
        if (function.hasModifier(KtTokens.PRIVATE_KEYWORD) || function.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
            return
        }
        val parameterList = function.getChildrenOfType<KtParameterList>().firstOrNull()
        if (function.name == "toString" && parameterList?.children.isNullOrEmpty()) {
            return
        }
        if (function.getStrictParentOfType<KtObjectLiteralExpression>() != null) {
            // Function is overriding something in an anonymous object
            // e.g.: val x = object : Interface { override fun foo() {} }
            return
        }
        if (function.isExtensionDeclaration()) {
            val target = function.receiverTypeReference?.nameForReceiverLabel()
            val fqName = target?.resolveFullType()
            outputFile.appendText("$fqName.${function.nameAsSafeName}(")
        } else {
            val parentName = function.containingClassOrObject?.fqName
                ?: function.containingKtFile.packageFqName
            outputFile.appendText("$parentName.${function.nameAsSafeName}(")
        }

        parameterList?.children?.filterIsInstance<KtParameter>()?.forEachIndexed { idx, p ->
            val typeElement = p.typeReference?.typeElement
            if (idx > 0) outputFile.appendText(", ")
            outputFile.appendText(typeElement?.fullType() ?: "???")
        }

        outputFile.appendText(")\n")
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
}
