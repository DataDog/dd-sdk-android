/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.resolve.BindingContext

/**
 * An abstract Detekt rule keeping track of imports to resolve types found in the code.
 *
 * @param ruleSetConfig the detekt ruleSet configuration
 */
abstract class AbstractTypedRule(
    ruleSetConfig: Config = Config.empty
) : Rule(ruleSetConfig) {

    private val imports = mutableMapOf<String, String>()
    private var packageName = ""

    // region Rule

    override fun visitKtFile(file: KtFile) {
        if (bindingContext == BindingContext.EMPTY) {
            println("\nMissing BindingContext when checking file:${file.virtualFilePath}")
        }
        imports.clear()
        super.visitKtFile(file)
    }

    override fun visitPackageDirective(directive: KtPackageDirective) {
        super.visitPackageDirective(directive)
        packageName = directive.fqName.asString()
    }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val path = importDirective.importPath?.pathStr ?: return
        val alias = importDirective.alias?.toString() ?: path.substringAfterLast('.')

        imports[alias] = path
    }

    // endregion

    // region AbstractTypedRule

    /**
     * Resolves the given type based on the current file's imports and the builtin kotlin types.
     * @return the fully qualified type name
     */
    protected fun String.resolveFullType(): String {
        val (nonNullType, suffix) = if (indexOf('.') > 0) {
            // Handles inner types usages
            substringBefore('.') to "." + substringAfter('.')
        } else if (endsWith('?')) {
            substringBeforeLast('?') to "?"
        } else {
            this to ""
        }

        val fullType = imports[nonNullType] ?: KotlinTypes[nonNullType]
        return if (fullType != null) {
            "$fullType$suffix"
        } else {
            // This can be because the class is local or a generic
            "$nonNullType$suffix"
        }
    }

    // endregion

    companion object {
        private val KotlinTypes = mapOf(
            // Base
            "Any" to "kotlin.Any",
            "Nothing" to "kotlin.Nothing",
            "Unit" to "kotlin.Unit",
            // Primitives
            "Boolean" to "kotlin.Boolean",
            "Byte" to "kotlin.Byte",
            "Char" to "kotlin.Char",
            "Double" to "kotlin.Double",
            "Float" to "kotlin.Float",
            "Int" to "kotlin.Int",
            "Long" to "kotlin.Long",
            "Short" to "kotlin.Short",
            "String" to "kotlin.String",
            // Tuples
            "Pair" to "kotlin.Pair",
            // Collections
            "Collection" to "kotlin.collections.Collection",
            "Iterable" to "kotlin.collections.Iterable",
            "List" to "kotlin.collections.List",
            "Map" to "kotlin.collections.Map",
            "Set" to "kotlin.collections.Set",
            "MutableCollection" to "kotlin.collections.MutableCollection",
            "MutableIterable" to "kotlin.collections.MutableIterable",
            "MutableList" to "kotlin.collections.MutableList",
            "MutableMap" to "kotlin.collections.MutableMap",
            "MutableSet" to "kotlin.collections.MutableSet",
            // Arrays
            "Array" to "kotlin.Array",
            "ByteArray" to "kotlin.ByteArray",
            "CharArray" to "kotlin.CharArray",
            "ShortArray" to "kotlin.ShortArray",
            "IntArray" to "kotlin.IntArray",
            "LongArray" to "kotlin.LongArray",
            "FloatArray" to "kotlin.FloatArray",
            "DoubleArray" to "kotlin.DoubleArray",
            "BooleanArray" to "kotlin.BooleanArray",
            // Throwables
            "Throwable" to "kotlin.Throwable",
            // Runnable
            "Runnable" to "java.lang.Runnable"
        )
    }
}
