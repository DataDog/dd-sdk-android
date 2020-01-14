/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import java.io.File
import java.lang.UnsupportedOperationException
import kastree.ast.Node
import kastree.ast.psi.Parser

class KotlinFileVisitor {

    val description = StringBuilder()
    private val imports = mutableMapOf<String, String>()

    // region KotlinFileVisitor

    fun visitFile(file: File) {
        val code = file.readText()
        val ast = Parser.parseFile(code, throwOnError = false)

        imports.clear()
        ast.imports.forEach {
            imports[it.alias ?: it.names.last()] = it.names.joinToString(".")
        }

        val pkg = ast.pkg?.names?.joinToString(".", postfix = ".").orEmpty()
        ast.decls.forEach { visitDeclaration(it, pkg, 0) }
    }

    // endregion

    // region Internal/Visitor

    private fun visitDeclaration(
        declaration: Node.Decl,
        pkg: String,
        level: Int
    ) {
        if (declaration is Node.WithModifiers) {
            if (!declaration.isPublic()) return

            description.append("  ".repeat(level))

            if (declaration.isProtected()) {
                description.append("protected ")
            }
            if (declaration.isOpen()) {
                description.append("open ")
            }
            if (declaration.isAbstract()) {
                description.append("abstract ")
            }
        }

        when (declaration) {
            is Node.Decl.Structured -> visitStructured(declaration, pkg, level)
            is Node.Decl.Init -> {
            }
            is Node.Decl.Func -> visitFunction(declaration)
            is Node.Decl.Property -> visitProperty(declaration)
            is Node.Decl.TypeAlias -> visitTypeAlias(declaration, level)
            is Node.Decl.Constructor -> visitConstructor(declaration)
            is Node.Decl.EnumEntry -> visitEnumEntry(declaration)
        }
    }

    private fun visitEnumEntry(declaration: Node.Decl.EnumEntry) {
        description.append("- ")
        description.append(declaration.name)
        description.append("\n")
    }

    private fun visitConstructor(constructor: Node.Decl.Constructor) {
        description.append("constructor(")
        visitParameters(constructor.params)
        description.append(")\n")
    }

    private fun visitStructured(
        structured: Node.Decl.Structured,
        pkg: String,
        level: Int
    ) {
        if (structured.isSealed()) {
            description.append("sealed ")
        }
        description.append(structured.form.description())
        if (structured.form != Node.Decl.Structured.Form.COMPANION_OBJECT) {
            description.append(" ")
            if (level == 0) {
                description.append(pkg)
            }
            description.append(structured.name)
        }

        description.append(structured.typeParams.description())
        description.append(structured.parentDescription())

        description.append('\n')

        // Visit children
        structured.primaryConstructor?.asConstructor()?.let {
            visitDeclaration(it, pkg, level + 1)
        }

        structured.members.forEach {
            visitDeclaration(it, pkg, level + 1)
        }
    }

    private fun visitTypeAlias(
        typeAlias: Node.Decl.TypeAlias,
        level: Int
    ) {
        if (typeAlias.isPublic()) {
            description.append("  ".repeat(level))
            description.append("typealias ")
            description.append(typeAlias.name)
            description.append(" = ")
            description.append(typeAlias.type.description())
            description.append('\n')
        }
    }

    private fun visitFunction(func: Node.Decl.Func) {
        if (func.isOverride()) {
            description.append("override fun ")
        } else {
            description.append("fun ")
        }

        description.append(func.typeParams.description(postfix = " "))

        description.append(func.name)
        description.append("(")
        visitParameters(func.params)
        description.append(")")

        val returnType = func.type.description()
        if (returnType.isNotBlank()) {
            description.append(": ")
            description.append(returnType)
        }
        description.append('\n')
    }

    private fun visitParameters(params: List<Node.Decl.Func.Param>) {
        if (params.isNotEmpty()) {
            params.forEachIndexed { i, param ->
                if (i > 0) description.append(", ")
                description.append(param.type.description())

                if (param.default != null) {
                    description.append(" = ")
                    description.append(param.default.description())
                }
            }
        }
    }

    private fun visitProperty(property: Node.Decl.Property) {
        if (property.isConst()) {
            description.append("const val ")
        } else if (property.readOnly) {
            description.append("val ")
        } else {
            description.append("var ")
        }

        property.vars
            .filterNotNull()
            .forEach {
                description.append(it.name)
                checkNotNull(it.type) { "Public properties should use an explicit type. Error on property ${it.name}" }
                description.append(": ")
                description.append(it.type.description())
            }

        description.append('\n')
    }

    // endregion

    // region Internal/Ext

    private fun Node.Decl.Structured.PrimaryConstructor.asConstructor(): Node.Decl.Constructor {
        return Node.Decl.Constructor(
            mods = mods,
            params = params,
            delegationCall = null,
            block = null
        )
    }

    private fun Node.Decl.Structured.Form.description(): String {
        return when (this) {
            Node.Decl.Structured.Form.CLASS -> "class"
            Node.Decl.Structured.Form.ENUM_CLASS -> "enum"
            Node.Decl.Structured.Form.INTERFACE -> "interface"
            Node.Decl.Structured.Form.OBJECT -> "object"
            Node.Decl.Structured.Form.COMPANION_OBJECT -> "companion object"
        }
    }

    private fun Node.Type?.description(): String {
        return this?.ref.description()
    }

    private fun Node.TypeRef?.description(): String {
        return when (this) {
            is Node.TypeRef.Nullable -> "${type.description()}?"
            is Node.TypeRef.Simple -> pieces.joinToString(", ") { p ->
                val name = imports[p.name] ?: p.name
                if (p.typeParams.isEmpty()) {
                    name
                } else {
                    "$name<${p.typeParams.joinToString(", ") { it?.description().orEmpty() }}>"
                }
            }
            is Node.TypeRef.Func -> {
                val prefix = if (receiverType != null) {
                    "${receiverType.description()}."
                } else {
                    ""
                }
                val inputs = params.joinToString(", ") { it.type.description() }
                val output = type.description()
                "$prefix($inputs) -> $output"
            }
            null -> ""
            else -> throw UnsupportedOperationException("Unable to get description for TypeRef $this")
        }
    }

    private fun Node.Expr?.description(): String {
        return when (this) {
            is Node.Expr.Const -> this.value
            is Node.Expr.Call -> "${this.expr.description()}()"
            is Node.Expr.Name -> imports[this.name] ?: this.name
            else -> throw UnsupportedOperationException("Unable to get description for Expr $this")
        }
    }

    private fun List<Node.TypeParam>.description(postfix: String = ""): String {
        return if (isEmpty()) {
            ""
        } else {
            val list = joinToString(", ") {
                if (it.type != null) {
                    "${it.name}: ${it.type.description()}"
                } else {
                    it.name
                }
            }
            "<$list>$postfix"
        }
    }

    private fun Node.Decl.Structured.parentDescription(): String {
        return if (parents.isEmpty()) {
            ""
        } else {
            parents.joinToString(", ", prefix = " : ") {
                when (it) {
                    is Node.Decl.Structured.Parent.CallConstructor -> it.type.description()
                    is Node.Decl.Structured.Parent.Type -> it.type.description()
                }
            }
        }
    }

    private fun Node.WithModifiers.isPublic(): Boolean {
        return !(Node.Modifier.Lit(Node.Modifier.Keyword.INTERNAL) in mods ||
            Node.Modifier.Lit(Node.Modifier.Keyword.PRIVATE) in mods)
    }

    private fun Node.WithModifiers.isProtected(): Boolean {
        return Node.Modifier.Lit(Node.Modifier.Keyword.PROTECTED) in mods
    }

    private fun Node.WithModifiers.isConst(): Boolean {
        return Node.Modifier.Lit(Node.Modifier.Keyword.CONST) in mods
    }

    private fun Node.WithModifiers.isOverride(): Boolean {
        return Node.Modifier.Lit(Node.Modifier.Keyword.OVERRIDE) in mods
    }

    private fun Node.WithModifiers.isOpen(): Boolean {
        return Node.Modifier.Lit(Node.Modifier.Keyword.OPEN) in mods
    }

    private fun Node.WithModifiers.isAbstract(): Boolean {
        return Node.Modifier.Lit(Node.Modifier.Keyword.ABSTRACT) in mods
    }

    private fun Node.WithModifiers.isSealed(): Boolean {
        return Node.Modifier.Lit(Node.Modifier.Keyword.SEALED) in mods
    }

    // endregion
}
