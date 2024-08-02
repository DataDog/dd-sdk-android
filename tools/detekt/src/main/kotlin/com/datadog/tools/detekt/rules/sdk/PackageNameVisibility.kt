/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.rules.AbstractTypedRule
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.util.isAnnotated

/**
 * A rule to detekt classes in the wrong package name.
 *
 * Our naming policy implies that a class that is Internal to the SDK be in a
 * `com.datadog.android.*.internal.*` package name.
 * @active
 */
@RequiresTypeResolution
class PackageNameVisibility(
    ruleSetConfig: Config
) : AbstractTypedRule(ruleSetConfig) {

    private val withBreakingChanges: Boolean by config(defaultValue = true)
    private val ignoredAnnotations: List<String> by config(defaultValue = emptyList())

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Warning,
        "This rule reports when a top level declaration's visibility doesn't match the package naming policy.",
        Debt.FIVE_MINS
    )

    private var currentPackageName: String = ""
    private var isCurrentPackageInternal: Boolean = false

    override fun visitPackageDirective(directive: KtPackageDirective) {
        currentPackageName = directive.fqName.asString()
        isCurrentPackageInternal = currentPackageName.split('.').any { it == INTERNAL_PACKAGE }
        super.visitPackageDirective(directive)
    }

    override fun visitNamedDeclaration(decl: KtNamedDeclaration) {
        val isDeclarationInternal = decl.isPrivate() || decl.isInternal()
        println("Annotations?: ${decl.isAnnotated}")
        decl.annotationEntries.forEach {
            val annotationName = it.shortName?.asString()?.resolveFullType()
            println("Annotation: $annotationName / ${it.shortName}")
        }
        val isIgnoredAnnotation = decl.annotationEntries.any {
            it.shortName?.asString()?.resolveFullType() in ignoredAnnotations
        }

        if (isDeclarationInternal && !isCurrentPackageInternal) {
            if (decl !is KtProperty) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(decl),
                        "Type ${decl.name} is marked Internal but is not in an internal package: $currentPackageName."
                    )
                )
            }
        } else if (isCurrentPackageInternal && !isDeclarationInternal) {
            if (withBreakingChanges && !isIgnoredAnnotation) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(decl),
                        "Type ${decl.name} is marked Public but is in an internal package: $currentPackageName."
                    )
                )
            }
        }
    }

    private fun KtModifierListOwner.isPrivate(): Boolean {
        return hasModifier(KtTokens.PRIVATE_KEYWORD)
    }

    private fun KtModifierListOwner.isInternal(): Boolean {
        return hasModifier(KtTokens.INTERNAL_KEYWORD)
    }

    companion object {
        private const val INTERNAL_PACKAGE = "internal"
    }
}
