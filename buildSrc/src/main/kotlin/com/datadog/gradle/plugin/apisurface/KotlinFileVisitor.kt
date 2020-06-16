/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import java.io.File
import kotlinx.ast.common.AstSource
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.AstNode
import kotlinx.ast.common.ast.AstTerminal
import kotlinx.ast.common.print
import kotlinx.ast.grammar.kotlin.target.antlr.kotlin.KotlinGrammarAntlrKotlinParser

class KotlinFileVisitor {

    val description = StringBuilder()
    private lateinit var pkg: String
    private val imports = mutableMapOf<String, String>()

    // region KotlinFileVisitor

    fun visitFile(file: File, printAst: Boolean = false) {
        val code = file.readText()
        val source = AstSource.String(code)
        val ast = KotlinGrammarAntlrKotlinParser.parseKotlinFile(source)

        if (printAst) ast.print()
        imports.clear()
        try {
            visitAst(ast, level = 0)
        } catch (e: Exception) {
            throw IllegalStateException("Error generating API surface for $file", e)
        }
    }

    // endregion

    // region Internal/Visitor

    private fun visitAst(ast: Ast, level: Int) {
        when (ast) {
            is AstNode -> visitAstNode(ast, level)
            is AstTerminal -> ignoreNode()
            else -> throw IllegalStateException("Unable to handle $ast")
        }
    }

    private fun visitAstNode(node: AstNode, level: Int) {
        when (node.description) {
            "kotlinFile",
            "importList",
            "topLevelObject",
            "declaration",
            "classMemberDeclarations",
            "classMemberDeclaration",
            "enumEntries",
            "kamoulox" -> node.children.forEach {
                visitAst(it, level)
            }
            "packageHeader" -> visitPackage(node)
            "importHeader" -> visitImport(node)
            "classDeclaration" -> visitTypeDeclaration(node, level, "class")
            "objectDeclaration" -> visitTypeDeclaration(node, level, "object")
            "companionObject" -> visitTypeDeclaration(node, level, "companion object")
            "secondaryConstructor" -> visitSecondaryConstructor(node, level)
            "functionDeclaration" -> visitFunctionDeclaration(node, level)
            "propertyDeclaration" -> visitProperty(node, level)
            "typeAlias" -> visitTypeAlias(node, level)
            "enumEntry" -> visitEnumEntry(node, level)
            "semis" -> ignoreNode()
            else -> println("??? ${node.description}")
        }
    }

    private fun visitTypeDeclaration(node: AstNode, level: Int, type: String) {
        if (node.isInternal() || node.isPrivate()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        if (node.isDeprecated()) description.append("DEPRECATED ")
        if (node.isSealed()) description.append("sealed ")
        if (node.isProtected()) description.append("protected ")
        if (node.isOpen()) description.append("open ")
        if (node.isAbstract()) description.append("abstract ")

        when {
            node.hasChildTerminal("INTERFACE") -> description.append("interface ")
            node.isEnum() -> description.append("enum ")
            else -> description.append("$type ")
        }

        // Canonical name
        if (level == 0) description.append(pkg)
        description.append(node.identifierName())

        // Generics
        visitTypeParameters(node)

        // Parent types
        visitParentTypes(node)

        // EOL
        description.append("\n")

        // Primary constructor
        val primaryConstructor = node.firstChildNodeOrNull("primaryConstructor")
        if (primaryConstructor != null) {
            visitPrimaryConstructor(primaryConstructor, level + 1)
        }

        // Content
        node.firstChildNodeOrNull("classBody")?.children?.forEach {
            visitAst(it, level + 1)
        }
        node.firstChildNodeOrNull("enumClassBody")?.children?.forEach {
            visitAst(it, level + 1)
        }
    }

    private fun visitPrimaryConstructor(node: AstNode, level: Int) {
        if (node.isInternal() || node.isPrivate()) return

        description.append(INDENT.repeat(level))

        description.append("constructor")

        // Parameters
        visitConstructorParameters(node)

        // EOL
        description.append("\n")
    }

    private fun visitSecondaryConstructor(node: AstNode, level: Int) {
        if (node.isInternal() || node.isPrivate()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        if (node.isDeprecated()) description.append("DEPRECATED ")
        if (node.isProtected()) description.append("protected ")

        description.append("constructor")

        // Parameters
        visitFunctionParameters(node)

        // EOL
        description.append("\n")
    }

    private fun visitFunctionDeclaration(node: AstNode, level: Int) {
        if (node.isInternal() || node.isPrivate()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        if (node.isDeprecated()) description.append("DEPRECATED ")
        if (node.isOverride()) description.append("override ")
        if (node.isProtected()) description.append("protected ")
        if (node.isOpen()) description.append("open ")
        if (node.isAbstract()) description.append("abstract ")

        description.append("fun ")

        // Generics
        visitTypeParameters(node)
        if (node.hasChildNode("typeParameters")) {
            description.append(" ")
        }

        // Name
        description.append(node.identifierName())

        // Parameters
        visitFunctionParameters(node)

        // Return type
        val type = node.firstChildNodeOrNull("type")
        type?.let {
            description.append(": ")
            description.append(it.typeName())
        }

        // EOL
        description.append("\n")
    }

    private fun visitProperty(node: AstNode, level: Int) {
        if (node.isInternal() || node.isPrivate()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        if (node.isDeprecated()) description.append("DEPRECATED ")
        if (node.isOverride()) description.append("override ")
        if (node.isProtected()) description.append("protected ")
        if (node.isOpen()) description.append("open ")
        if (node.isAbstract()) description.append("abstract ")
        if (node.isConst()) description.append("const ")

        // Property type
        if (node.hasChildTerminal("VAL")) {
            description.append("val ")
        } else if (node.hasChildTerminal("VAR")) {
            description.append("var ")
        }

        val variableDeclaration = node.firstChildNode("variableDeclaration")
        description.append(variableDeclaration.identifierName())

        // Type
        description.append(": ")
        val propertyType = variableDeclaration.firstChildNodeOrNull("type")
        checkNotNull(propertyType) {
            "Public properties should use an explicit type. " +
                "Error on property ${variableDeclaration.identifierName()}"
        }
        description.append(propertyType.typeName())

        // EOL
        description.append("\n")
    }

    private fun visitEnumEntry(node: AstNode, level: Int) {
        description.append(INDENT.repeat(level))
        description.append("- ")
        description.append(node.identifierName())
        description.append("\n")
    }

    private fun visitParentTypes(node: AstNode) {
        val parentSpecifiers = node.firstChildNodeOrNull("delegationSpecifiers")
            ?.childrenNodes("annotatedDelegationSpecifier")
            ?.map {
                val delegation = it.firstChildNode("delegationSpecifier")
                val typeRef = delegation.firstChildNodeOrNull("constructorInvocation")
                    ?: delegation.firstChildNodeOrNull("explicitDelegation")
                    ?: delegation
                typeRef.typeReferenceName()
            }
        if (!parentSpecifiers.isNullOrEmpty()) {
            description.append(" : ")
            description.append(parentSpecifiers.joinToString(", "))
        }
    }

    private fun visitTypeParameters(node: AstNode) {
        val typeParameters = node.firstChildNodeOrNull("typeParameters") ?: return
        val generics = typeParameters.childrenNodes("typeParameter")
            .map {
                val name = it.identifierName()
                val type = it.firstChildNodeOrNull("type")?.typeName()
                name + (if (type == null) "" else ": $type")
            }
        description.append(
            generics.joinToString(", ", prefix = "<", postfix = ">")
        )
    }

    private fun visitFunctionParameters(node: AstNode) {
        description.append("(")
        description.append(
            node.firstChildNode("functionValueParameters")
                .childrenNodes("functionValueParameter")
                .joinToString(", ") { it.parameterType() }
        )
        description.append(")")
    }

    private fun visitConstructorParameters(node: AstNode) {
        description.append("(")
        description.append(
            node.firstChildNode("classParameters")
                .childrenNodes("classParameter")
                .joinToString(", ") { it.constructorParameterType() }
        )
        description.append(")")
    }

    private fun visitPackage(node: AstNode) {
        val identifier = node.firstChildNodeOrNull("identifier")
        pkg = if (identifier != null) {
            identifier.identifierName() + "."
        } else {
            ""
        }
    }

    private fun visitImport(node: AstNode) {
        val import = node.firstChildNode("identifier").identifierName()

        val importAlias = node.firstChildNodeOrNull("importAlias")?.identifierName()

        imports[importAlias ?: import.substringAfterLast(".")] = import
    }

    private fun visitTypeAlias(node: AstNode, level: Int) {
        if (node.isInternal() || node.isPrivate()) return

        val name = node.identifierName()

        val type = node.firstChildNode("type")

        description.append(INDENT.repeat(level))
        description.append("typealias $name = ${type.lambdaName()}\n")
    }

    private fun ignoreNode() {
        // Do Nothing
    }

    // endregion

    // region Internal/Ext/Modifiers

    private fun AstNode.isSealed(): Boolean {
        return hasModifier("classModifier", "SEALED")
    }

    private fun AstNode.isEnum(): Boolean {
        return hasModifier("classModifier", "ENUM")
    }

    private fun AstNode.isProtected(): Boolean {
        return hasModifier("visibilityModifier", "PROTECTED")
    }

    private fun AstNode.isPrivate(): Boolean {
        return hasModifier("visibilityModifier", "PRIVATE")
    }

    private fun AstNode.isInternal(): Boolean {
        return hasModifier("visibilityModifier", "INTERNAL")
    }

    private fun AstNode.isOpen(): Boolean {
        return hasModifier("inheritanceModifier", "OPEN")
    }

    private fun AstNode.isAbstract(): Boolean {
        return hasModifier("inheritanceModifier", "ABSTRACT")
    }

    private fun AstNode.isOverride(): Boolean {
        return hasModifier("memberModifier", "OVERRIDE")
    }

    private fun AstNode.isConst(): Boolean {
        return hasModifier("propertyModifier", "CONST")
    }

    private fun AstNode.hasModifier(group: String, modifier: String): Boolean {
        val modifiers = firstChildNodeOrNull("modifiers") ?: return false
        return modifiers.childrenNodes("modifier")
            .mapNotNull { it.firstChildNodeOrNull(group) }
            .any { it.firstChildTerminalOrNull(modifier) != null }
    }

    private fun AstNode.isDeprecated(): Boolean {
        val modifiers = firstChildNodeOrNull("modifiers") ?: return false
        return modifiers.childrenNodes("annotation")
            .mapNotNull { it.firstChildNodeOrNull("singleAnnotation") }
            .mapNotNull { it.firstChildNodeOrNull("unescapedAnnotation") }
            .map { it.firstChildNodeOrNull("constructorInvocation") ?: it }
            .filter { it.hasChildNode("userType") }
            .any { it.typeReferenceName() in DEPRECATED_ANNOTATIONS }
    }

    // endregion

    // region Internal/Ext/Node

    private fun Ast.isNode(description: String): Boolean {
        return this is AstNode && this.description == description
    }

    private fun AstNode.firstChildNode(description: String): AstNode {
        val first = firstChildNodeOrNull(description)
        checkNotNull(first) { "Unable to find a child with description $description in \n$this" }
        return first
    }

    private fun AstNode.firstChildNodeOrNull(description: String): AstNode? {
        return children.firstOrNull { it.isNode(description) } as? AstNode
    }

    private fun AstNode.hasChildNode(description: String): Boolean {
        return children.any { it.isNode(description) }
    }

    private fun AstNode.childrenNodes(description: String): List<AstNode> {
        return children.filterIsInstance<AstNode>()
            .filter { it.description == description }
    }

    // endregion

    // region Internal/Ext/Terminal

    private fun Ast.isTerminal(description: String): Boolean {
        return this is AstTerminal && this.description == description
    }

    private fun AstNode.firstChildTerminal(description: String): AstTerminal {
        val first = firstChildTerminalOrNull(description)
        checkNotNull(first) { "Unable to find a child with description $description in \n$this" }
        return first
    }

    private fun AstNode.firstChildTerminalOrNull(description: String): AstTerminal? {
        return children.firstOrNull { it.isTerminal(description) } as? AstTerminal
    }

    private fun AstNode.hasChildTerminal(description: String): Boolean {
        return children.any { it.isTerminal(description) }
    }

    // endregion

    // region Internal/Ext/Names

    private fun AstNode.identifierName(): String {
        return childrenNodes("simpleIdentifier")
            .joinToString(".") {
                it.firstChildTerminalOrNull("Identifier")?.text ?: it.children.first()
                    .expressionValue()
            }
    }

    private fun AstNode.typeName(): String {
        val nullableType = firstChildNodeOrNull("nullableType")
        return when {
            nullableType != null -> nullableType.typeName() + "?"
            hasChildNode("functionType") -> lambdaName()
            else -> firstChildNode("typeReference").typeReferenceName()
        }
    }

    private fun AstNode.typeReferenceName(): String {
        val simpleUserTypes = firstChildNode("userType")
            .childrenNodes("simpleUserType")

        return simpleUserTypes.fold("") { aggr, userType ->
            val typeName = userType.identifierName()
            val generics = userType.firstChildNodeOrNull("typeArguments")
                ?.childrenNodes("typeProjection")
                ?.joinToString(", ", prefix = "<", postfix = ">") {
                    it.firstChildNode("type").typeName()
                } ?: ""
            if (aggr.isEmpty()) {
                (imports[typeName] ?: typeName) + generics
            } else {
                "$aggr.$typeName$generics"
            }
        }
    }

    private fun AstNode.lambdaName(): String {
        val functionType = firstChildNode("functionType")

        val receiver = functionType.firstChildNodeOrNull("receiverType")?.typeName()
        val params = functionType.firstChildNode("functionTypeParameters")
            .childrenNodes("type")
            .joinToString(", ") { it.typeName() }
        val returns = functionType.firstChildNode("type").typeName()

        return if (receiver == null) {
            "($params) -> $returns"
        } else {
            "$receiver.($params) -> $returns"
        }
    }

    private fun AstNode.parameterType(): String {
        val typeName = firstChildNode("parameter")
            .firstChildNode("type")
            .typeName()

        return if (hasChildNode("expression")) {
            val defaultValue = firstChildNode("expression").expressionValue()
            "$typeName = $defaultValue"
        } else {
            typeName
        }
    }

    private fun AstNode.constructorParameterType(): String {
        val typeName = firstChildNode("type")
            .typeName()

        return if (hasChildNode("expression")) {
            val defaultValue = firstChildNode("expression").expressionValue()
            "$typeName = $defaultValue"
        } else {
            typeName
        }
    }

    private fun Ast.expressionValue(): String {
        return if (this is AstNode) {
            children.map { it.expressionValue() }.joinToString("")
        } else if (this is AstTerminal) {
            text
        } else {
            println("EXPR ?? $this")
            "…"
        }
    }

    // endregion

    companion object {
        private const val INDENT = "  "

        private val DEPRECATED_ANNOTATIONS = arrayOf(
            "java.lang.Deprecated",
            "kotlin.Deprecated",
            "Deprecated"
        )
    }
}
