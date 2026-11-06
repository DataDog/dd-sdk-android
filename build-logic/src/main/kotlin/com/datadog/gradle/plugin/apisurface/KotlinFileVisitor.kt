/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameter
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
 * Enum constructors are excluded because they are private even without an explicit modifier.
 */
// Each declaration kind has its own rendering path.
@Suppress("TooManyFunctions")
class KotlinFileVisitor {

    val description = StringBuilder()

    private var pkg: String = ""
    private var typeRenderer = KotlinTypeRenderer(emptyMap())

    // region KotlinFileVisitor

    @Suppress("TooGenericExceptionCaught")
    fun visitFile(file: File) {
        val ktFile = PSI_FACTORY.createFile(file.name, file.readText())
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

        val imports = buildMap {
            ktFile.importDirectives.forEach { directive ->
                val imported = directive.importedFqName?.asString() ?: return@forEach
                val alias = directive.aliasName
                put(alias ?: imported.substringAfterLast("."), imported)
            }
        }
        typeRenderer = KotlinTypeRenderer(imports)

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
            else -> Unit
        }
    }

    private fun visitTypeDeclaration(declaration: KtClassOrObject, level: Int, type: String) {
        if (declaration.isExcludedFromApi()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        appendModifiers(
            declaration,
            KtTokens.DATA_KEYWORD,
            KtTokens.SEALED_KEYWORD,
            KtTokens.PROTECTED_KEYWORD,
            KtTokens.OPEN_KEYWORD,
            KtTokens.ABSTRACT_KEYWORD
        )

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
        if (constructor.getContainingClassOrObject().isEnum()) return
        if (constructor.isExcludedFromApi()) return

        description.append(INDENT.repeat(level))
        description.append("constructor")
        visitParameters(constructor.valueParameters)
        description.append("\n")
    }

    private fun visitSecondaryConstructor(constructor: KtSecondaryConstructor, level: Int) {
        if (constructor.getContainingClassOrObject().isEnum()) return
        if (constructor.isExcludedFromApi()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        appendModifiers(constructor, KtTokens.PROTECTED_KEYWORD)

        description.append("constructor")
        visitParameters(constructor.valueParameters)
        description.append("\n")
    }

    private fun visitFunctionDeclaration(function: KtNamedFunction, level: Int) {
        if (function.isExcludedFromApi()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        appendModifiers(
            function,
            KtTokens.OVERRIDE_KEYWORD,
            KtTokens.PROTECTED_KEYWORD,
            KtTokens.OPEN_KEYWORD,
            KtTokens.ABSTRACT_KEYWORD
        )

        description.append("fun ")

        // Generics
        visitTypeParameters(function)
        if (function.typeParameters.isNotEmpty()) {
            description.append(" ")
        }

        // Receiver
        function.receiverTypeReference?.let {
            description.append(typeRenderer.render(it))
            description.append(".")
        }

        // Name
        description.append(function.declaredName())

        // Parameters
        visitParameters(function.valueParameters)

        // Return type
        function.typeReference?.let {
            description.append(": ")
            description.append(typeRenderer.render(it))
        }

        // EOL
        description.append("\n")
    }

    private fun visitProperty(property: KtProperty, level: Int) {
        if (property.isExcludedFromApi()) return

        description.append(INDENT.repeat(level))

        // Modifiers
        appendModifiers(
            property,
            KtTokens.OVERRIDE_KEYWORD,
            KtTokens.PROTECTED_KEYWORD,
            KtTokens.OPEN_KEYWORD,
            KtTokens.ABSTRACT_KEYWORD,
            KtTokens.CONST_KEYWORD
        )

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
        description.append(typeRenderer.render(propertyType))

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
            .mapNotNull { it.typeReference?.let(typeRenderer::render) }
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
            val bound = parameter.extendsBound?.let(typeRenderer::render)
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
        if (typeAlias.isExcludedFromApi()) return

        val name = typeAlias.declaredName()
        val type = typeAlias.getTypeReference()

        description.append(INDENT.repeat(level))
        description.append("typealias $name = ${type?.let(typeRenderer::render)}\n")
    }

    // endregion

    // region Internal/Ext/Modifiers

    private fun KtClassOrObject.isEnum(): Boolean = this is KtClass && isEnum()

    private fun KtClassOrObject.isInterface(): Boolean = this is KtClass && isInterface()

    private fun appendModifiers(declaration: KtModifierListOwner, vararg modifiers: KtModifierKeywordToken) {
        if (declaration.isDeprecated()) description.append("DEPRECATED ")
        modifiers.forEach { modifier ->
            if (declaration.hasModifier(modifier)) {
                description.append(modifier.value).append(' ')
            }
        }
    }

    private fun KtModifierListOwner.isExcludedFromApi(): Boolean =
        hasModifier(KtTokens.INTERNAL_KEYWORD) || hasModifier(KtTokens.PRIVATE_KEYWORD)

    private fun KtModifierListOwner.isDeprecated(): Boolean {
        val annotations = modifierList?.annotationEntries.orEmpty()
        return annotations.any { entry ->
            val typeElement = entry.typeReference?.typeElement
            typeElement is KtUserType && typeRenderer.render(typeElement) in DEPRECATED_ANNOTATIONS
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

    private fun KtParameter.parameterType(): String {
        val typeName = typeReference?.let(typeRenderer::render).orEmpty()
        val default = defaultValue ?: return typeName
        return "$typeName = ${KotlinDefaultValueRenderer.render(default)}"
    }

    // endregion

    companion object {
        private const val INDENT = "  "

        /**
         * Share the PSI application and project for the lifetime of the isolated worker classloader.
         * Only the parser is registered: no compiler configuration, source roots, dependency
         * classpath or Analysis API session is needed. The factory creates non-physical files
         * without an analysis context, so parsed files are not retained between visits.
         */
        private val PSI_FACTORY: KtPsiFactory by lazy {
            val disposable = Disposer.newDisposable("KotlinFileVisitor")
            setupIdeaStandaloneExecution()
            val applicationEnvironment = KotlinCoreApplicationEnvironment.create(
                disposable,
                KotlinCoreApplicationEnvironmentMode.Production
            ).apply {
                registerParserDefinition(KotlinParserDefinition())
                registerFileType(KotlinFileType.INSTANCE, KotlinFileType.EXTENSION)
            }
            val environment = KotlinCoreProjectEnvironment(disposable, applicationEnvironment)
            KtPsiFactory(environment.project, markGenerated = false)
        }

        private val DEPRECATED_ANNOTATIONS = arrayOf(
            "java.lang.Deprecated",
            "kotlin.Deprecated",
            "Deprecated"
        )
    }
}
