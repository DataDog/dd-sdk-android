/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import java.io.File

/**
 * Builds the API surface description of a set of Kotlin files.
 *
 * This uses the Kotlin compiler's own PSI parser, which holds no state between files, so the
 * memory used is proportional to the file being parsed rather than to everything parsed so far.
 *
 * Note that types are rendered the way they are written in the source, with only the file's
 * own import list applied — no symbol resolution happens, and none is wanted: the surface
 * file records the API as it is declared.
 */
class KotlinFileVisitor {

    val description = StringBuilder()

    private var pkg: String = ""
    private val imports = mutableMapOf<String, String>()

    // region KotlinFileVisitor

    @Suppress("TooGenericExceptionCaught")
    fun visitFile(file: File) {
        val ktFile = PSI_FACTORY.createFile(file.name, file.readText())
        imports.clear()
        try {
            visitKtFile(ktFile)
        } catch (e: Exception) {
            throw IllegalStateException("Error generating API surface for $file", e)
        }
    }

    // endregion

    // region Internal/Visitor

    private fun visitKtFile(ktFile: KtFile) {
        val packageName = ktFile.packageDirective?.qualifiedName.orEmpty()
        pkg = if (packageName.isEmpty()) "" else "$packageName."

        ktFile.importDirectives.forEach { directive ->
            val imported = directive.importedFqName?.asString() ?: return@forEach
            val alias = directive.aliasName
            imports[alias ?: imported.substringAfterLast(".")] = imported
        }

        ktFile.declarations.forEach { visitDeclaration(it, level = 0) }
    }

    private fun visitDeclaration(declaration: KtDeclaration, level: Int) {
        when (declaration) {
            is KtEnumEntry -> visitEnumEntry(declaration, level)
            is KtClass -> visitTypeDeclaration(declaration, level, "class")
            is KtObjectDeclaration -> visitTypeDeclaration(
                declaration,
                level,
                if (declaration.isCompanion()) "companion object" else "object"
            )

            is KtSecondaryConstructor -> visitSecondaryConstructor(declaration, level)
            is KtNamedFunction -> visitFunctionDeclaration(declaration, level)
            is KtProperty -> visitProperty(declaration, level)
            is KtTypeAlias -> visitTypeAlias(declaration, level)
            else -> ignoreDeclaration()
        }
    }

    private fun visitTypeDeclaration(declaration: KtClassOrObject, level: Int, type: String) {
        if (declaration.isInternal() || declaration.isPrivate()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        if (declaration.isDeprecated()) description.append("DEPRECATED ")
        if (declaration.isData()) description.append("data ")
        if (declaration.isSealed()) description.append("sealed ")
        if (declaration.isProtected()) description.append("protected ")
        if (declaration.isOpen()) description.append("open ")
        if (declaration.isAbstract()) description.append("abstract ")

        when {
            declaration.isInterface() -> description.append("interface ")
            declaration.isEnum() -> description.append("enum ")
            declaration.isAnnotation() -> description.append("annotation ")
            else -> description.append("$type ")
        }

        // Canonical name
        if (level == 0) description.append(pkg)
        description.append(declaration.declaredName())

        // Generics
        visitTypeParameters(declaration)

        // Parent types
        visitParentTypes(declaration)

        // EOL
        description.append("\n")

        // Primary constructor
        declaration.primaryConstructor?.let { visitPrimaryConstructor(it, level + 1) }

        // Content
        declaration.body?.let { visitClassBody(it, level + 1) }
    }

    private fun visitClassBody(body: KtClassBody, level: Int) {
        body.declarations.forEach { visitDeclaration(it, level) }
    }

    private fun visitPrimaryConstructor(constructor: KtPrimaryConstructor, level: Int) {
        if (constructor.isInternal() || constructor.isPrivate()) return

        description.append(INDENT.repeat(level))
        description.append("constructor")
        visitParameters(constructor.valueParameters)
        description.append("\n")
    }

    private fun visitSecondaryConstructor(constructor: KtSecondaryConstructor, level: Int) {
        if (constructor.isInternal() || constructor.isPrivate()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        if (constructor.isDeprecated()) description.append("DEPRECATED ")
        if (constructor.isProtected()) description.append("protected ")

        description.append("constructor")
        visitParameters(constructor.valueParameters)
        description.append("\n")
    }

    private fun visitFunctionDeclaration(function: KtNamedFunction, level: Int) {
        if (function.isInternal() || function.isPrivate()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        if (function.isDeprecated()) description.append("DEPRECATED ")
        if (function.isOverride()) description.append("override ")
        if (function.isProtected()) description.append("protected ")
        if (function.isOpen()) description.append("open ")
        if (function.isAbstract()) description.append("abstract ")

        description.append("fun ")

        // Generics
        visitTypeParameters(function)
        if (function.typeParameters.isNotEmpty()) {
            description.append(" ")
        }

        // Receiver
        function.receiverTypeReference?.let {
            description.append(it.typeName())
            description.append(".")
        }

        // Name
        description.append(function.declaredName())

        // Parameters
        visitParameters(function.valueParameters)

        // Return type
        function.typeReference?.let {
            description.append(": ")
            description.append(it.typeName())
        }

        // EOL
        description.append("\n")
    }

    private fun visitProperty(property: KtProperty, level: Int) {
        if (property.isInternal() || property.isPrivate()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        if (property.isDeprecated()) description.append("DEPRECATED ")
        if (property.isOverride()) description.append("override ")
        if (property.isProtected()) description.append("protected ")
        if (property.isOpen()) description.append("open ")
        if (property.isAbstract()) description.append("abstract ")
        if (property.isConst()) description.append("const ")

        // Property type
        if (property.isVar) {
            description.append("var ")
        } else {
            description.append("val ")
        }

        description.append(property.declaredName())

        // Type
        description.append(": ")
        val propertyType = property.typeReference
        checkNotNull(propertyType) {
            "Public properties should use an explicit type. " +
                "Error on property ${property.name}"
        }
        description.append(propertyType.typeName())

        // EOL
        description.append("\n")
    }

    private fun visitEnumEntry(entry: KtEnumEntry, level: Int) {
        description.append(INDENT.repeat(level))
        description.append("- ")
        description.append(entry.declaredName())
        description.append("\n")
    }

    private fun visitParentTypes(declaration: KtClassOrObject) {
        val parentSpecifiers = declaration.superTypeListEntries
            .mapNotNull { it.typeReference?.typeName() }
        if (parentSpecifiers.isNotEmpty()) {
            description.append(" : ")
            description.append(parentSpecifiers.joinToString(", "))
        }
    }

    private fun visitTypeParameters(declaration: KtDeclaration) {
        val typeParameters = declaration.typeParametersOrNull() ?: return
        if (typeParameters.isEmpty()) return
        val generics = typeParameters.map { parameter ->
            val name = parameter.name.orEmpty()
            val bound = parameter.extendsBound?.typeName()
            name + (if (bound == null) "" else ": $bound")
        }
        description.append(generics.joinToString(", ", prefix = "<", postfix = ">"))
    }

    private fun visitParameters(parameters: List<KtParameter>) {
        description.append("(")
        description.append(parameters.joinToString(", ") { it.parameterType() })
        description.append(")")
    }

    private fun visitTypeAlias(typeAlias: KtTypeAlias, level: Int) {
        if (typeAlias.isInternal() || typeAlias.isPrivate()) return

        val name = typeAlias.declaredName()
        val type = typeAlias.getTypeReference()

        description.append(INDENT.repeat(level))
        description.append("typealias $name = ${type?.typeName()}\n")
    }

    private fun ignoreDeclaration() {
        // Do Nothing
    }

    // endregion

    // region Internal/Ext/Modifiers

    private fun KtClassOrObject.isSealed(): Boolean = this is KtClass && isSealed()

    private fun KtClassOrObject.isEnum(): Boolean = this is KtClass && isEnum()

    private fun KtClassOrObject.isInterface(): Boolean = this is KtClass && isInterface()

    private fun KtModifierListOwner.isProtected(): Boolean = hasModifier(KtTokens.PROTECTED_KEYWORD)

    private fun KtModifierListOwner.isPrivate(): Boolean = hasModifier(KtTokens.PRIVATE_KEYWORD)

    private fun KtModifierListOwner.isInternal(): Boolean = hasModifier(KtTokens.INTERNAL_KEYWORD)

    private fun KtModifierListOwner.isOpen(): Boolean = hasModifier(KtTokens.OPEN_KEYWORD)

    private fun KtModifierListOwner.isAbstract(): Boolean = hasModifier(KtTokens.ABSTRACT_KEYWORD)

    private fun KtModifierListOwner.isOverride(): Boolean = hasModifier(KtTokens.OVERRIDE_KEYWORD)

    private fun KtModifierListOwner.isConst(): Boolean = hasModifier(KtTokens.CONST_KEYWORD)

    private fun KtModifierListOwner.isDeprecated(): Boolean {
        val annotations = modifierList?.annotationEntries.orEmpty()
        return annotations.any { entry ->
            val typeElement = entry.typeReference?.typeElement
            typeElement is KtUserType && typeElement.userTypeName() in DEPRECATED_ANNOTATIONS
        }
    }

    // endregion

    // region Internal/Ext/Names

    /**
     * The name exactly as written: backticks are kept, and a companion object declared without
     * a name stays nameless rather than picking up the implicit `Companion`.
     */
    private fun KtNamedDeclaration.declaredName(): String = nameIdentifier?.text.orEmpty()

    private fun KtDeclaration.typeParametersOrNull(): List<KtTypeParameter>? = when (this) {
        is KtClassOrObject -> typeParameterList?.parameters
        is KtNamedFunction -> typeParameterList?.parameters
        else -> null
    }

    private fun KtTypeReference.typeName(): String {
        val element = typeElement ?: return ""
        return element.typeElementName()
    }

    private fun KtTypeElement.typeElementName(): String = when (this) {
        is KtNullableType -> (innerType?.typeElementName() ?: "") + "?"
        is KtFunctionType -> lambdaName()
        is KtUserType -> userTypeName()
        else -> text
    }

    /**
     * Renders a user type the way the surface file records it: the qualifier chain as written,
     * with only the first segment expanded through the file's imports.
     */
    private fun KtUserType.userTypeName(): String {
        val segments = mutableListOf<KtUserType>()
        var current: KtUserType? = this
        while (current != null) {
            segments.add(0, current)
            current = current.qualifier
        }

        return segments.foldIndexed("") { index, aggregate, segment ->
            val typeName = segment.referencedName.orEmpty()
            val generics = segment.typeArgumentList?.arguments
                ?.joinToString(", ", prefix = "<", postfix = ">") { it.projectionName() }
                ?: ""
            if (index == 0) {
                (imports[typeName] ?: typeName) + generics
            } else {
                "$aggregate.$typeName$generics"
            }
        }
    }

    private fun KtTypeProjection.projectionName(): String =
        typeReference?.typeName() ?: "*"

    private fun KtFunctionType.lambdaName(): String {
        val receiver = receiverTypeReference?.typeName()
        val params = parameters.joinToString(", ") { it.typeReference?.typeName().orEmpty() }
        val returns = returnTypeReference?.typeName().orEmpty()

        return if (receiver == null) {
            "($params) -> $returns"
        } else {
            "$receiver.($params) -> $returns"
        }
    }

    private fun KtParameter.parameterType(): String {
        val typeName = typeReference?.typeName().orEmpty()
        val default = defaultValue ?: return typeName
        return "$typeName = ${default.normalizedText()}"
    }

    /**
     * The expression rendered from its tokens alone, with all whitespace and comments dropped.
     *
     * The surface file records default values so reviewers can see them, but it should only
     * change when the API does. Rendering the source verbatim lets a reformat, or a comment
     * added inside a default value, mark the checked-in surface stale, and a default written
     * across several lines breaks the one-declaration-per-line layout.
     *
     * Spacing is therefore derived from the tokens themselves rather than from the trivia
     * between them: a space goes in only where leaving it out would run two tokens together
     * into a different one (`to 1` must not become `to1`). That makes the rendering canonical
     * — `foo(1,2)`, `foo(1, 2)` and `foo(1,/* note */2)` all come out as `foo(1,2)`.
     *
     * Tokens are walked rather than the text rewritten, so whitespace *inside* string literals
     * is left alone.
     */
    private fun PsiElement.normalizedText(): String {
        val builder = StringBuilder()
        appendTokens(this, builder)
        return builder.toString()
    }

    private fun appendTokens(element: PsiElement, builder: StringBuilder) {
        when {
            element is PsiComment || element is PsiWhiteSpace -> return

            // A string literal is several tokens (quote, content, quote) but has to stay
            // exactly as written, spaces included, so it is emitted as one unit.
            element is KtStringTemplateExpression -> appendToken(element.text, builder)

            element.firstChild == null -> appendToken(element.text, builder)

            else -> {
                var child = element.firstChild
                while (child != null) {
                    appendTokens(child, builder)
                    child = child.nextSibling
                }
            }
        }
    }

    private fun appendToken(token: String, builder: StringBuilder) {
        if (token.isEmpty()) return
        if (builder.isNotEmpty() && builder.needsSeparatorBefore(token)) {
            builder.append(" ")
        }
        builder.append(token)
    }

    /**
     * Whether a space belongs between what has been written so far and [token].
     *
     * This looks only at the two tokens, never at the source, which is what keeps the
     * rendering independent of formatting and comments.
     */
    private fun StringBuilder.needsSeparatorBefore(token: String): Boolean {
        val previous = last()
        return when {
            // An empty lambda stays tight: `{}`, never `{ }`.
            previous == '{' && token == "}" -> false
            // Lambdas are padded, so `remember{emptyMap()}` and `remember { emptyMap() }`
            // both render as the latter.
            previous == '{' || token == "{" || token == "}" -> true
            // Otherwise a space only where the tokens would otherwise merge into one.
            else -> previous.isIdentifierChar() && token.first().isIdentifierChar()
        }
    }

    /** Quotes count, so `"a" to 1` keeps its spaces instead of collapsing to `"a"to 1`. */

    private fun Char.isIdentifierChar(): Boolean =
        isLetterOrDigit() || this == '_' || this == '`' || this == '"' || this == '\''

    // endregion

    companion object {
        private const val INDENT = "  "

        /**
         * The compiler environment sets up an IntelliJ application, which is a per-process
         * singleton, so it is built once and kept for the life of the JVM. It holds no
         * per-file state: every parsed file becomes garbage as soon as we are done with it.
         */
        // Kotlin 2.2+ gates the K1 compiler front-end behind an opt-in. Parsing here is purely
        // syntactic (PSI only, no resolution), so the K1 environment stays the right tool until
        // the Analysis API offers a standalone parser.
        @OptIn(K1Deprecation::class)
        private val PSI_FACTORY: KtPsiFactory by lazy {
            val environment = KotlinCoreEnvironment.createForProduction(
                Disposer.newDisposable("KotlinFileVisitor"),
                CompilerConfiguration(),
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
            KtPsiFactory(environment.project, markGenerated = false)
        }

        private val DEPRECATED_ANNOTATIONS = arrayOf(
            "java.lang.Deprecated",
            "kotlin.Deprecated",
            "Deprecated"
        )
    }
}
