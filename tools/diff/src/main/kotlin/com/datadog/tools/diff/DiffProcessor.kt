/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.diff

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Visibility
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private enum class DiffStrategy { REPLACE, MERGE, IGNORE, APPEND, MAP }

private data class DiffConfigData(
    val classDecl: KSClassDeclaration,
    val fieldStrategies: Map<String, DiffStrategy>,
    val visibility: KModifier?,
    val containingFile: KSFile,
    val outputPackage: String,
)

private data class PropertyInfo(
    val name: String,
    val typeName: TypeName,
    val strategy: DiffStrategy,
    val mergeClassDecl: KSClassDeclaration?,
)

class DiffProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private val DIFF_OPTIONAL = ClassName("com.datadog.tools.diff", "DiffOptional")
        private val MAKE_DIFF = MemberName("com.datadog.tools.diff", "makeDiff")
        private val WRAP_OPTIONAL = MemberName("com.datadog.tools.diff", "wrapOptional")
        private val DIFF_MAP = MemberName("com.datadog.tools.diff", "diffMap")
        private val MAP_CLASS = ClassName("kotlin.collections", "Map")
    }

    // Populated at the start of each process() round; used by readPropInfos to resolve
    // external field strategies for classes annotated via @DiffConfig.
    private var configMap: Map<KSClassDeclaration, DiffConfigData> = emptyMap()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val configSymbols = resolver.getSymbolsWithAnnotation(DiffConfig::class.qualifiedName!!)
        val (validConfigSymbols, deferredConfigs) = configSymbols.partition { it.validate() }
        configMap = buildConfigMap(validConfigSymbols.filterIsInstance<KSFile>())

        val symbols = resolver.getSymbolsWithAnnotation(Diff::class.qualifiedName!!)
        val (valid, deferred) = symbols.partition { it.validate() }
        val validClasses = valid.filterIsInstance<KSClassDeclaration>()

        // Conflict check: a class must not have both @Diff and @DiffConfig.
        val diffClassNames = validClasses.mapNotNull { it.qualifiedName?.asString() }.toSet()
        for (configData in configMap.values) {
            if (configData.classDecl.qualifiedName?.asString() in diffClassNames) {
                logger.error(
                    "Class ${configData.classDecl.qualifiedName?.asString()} " +
                        "has both @Diff and @DiffConfig annotations — remove one.",
                    configData.containingFile,
                )
            }
        }

        validClasses.forEach { classDecl ->
            logger.info("DiffPlugin: processing @Diff class ${classDecl.qualifiedName?.asString()}")
            generateDiffFor(
                classDecl = classDecl,
                outputPackage = classDecl.packageName.asString(),
                visibility = classDecl.getVisibility().toKModifier(),
                originatingFiles = listOf(classDecl.containingFile!!),
            )
        }

        // Classes that appear as MERGE targets inside another @DiffConfig only provide field
        // strategies via configMap — they must not also generate a standalone diff file, since
        // the parent already embeds their diff as a nested type.
        val mergeTargets = collectMergeTargets(configMap)

        configMap.values
            .filter { it.classDecl.qualifiedName?.asString() !in diffClassNames }
            .filter { it.classDecl !in mergeTargets }
            .forEach { configData ->
                logger.info("DiffPlugin: processing @DiffConfig class ${configData.classDecl.qualifiedName?.asString()}")
                generateDiffFor(
                    classDecl = configData.classDecl,
                    outputPackage = configData.outputPackage,
                    visibility = configData.visibility,
                    originatingFiles = listOf(configData.containingFile),
                )
            }

        return deferred + deferredConfigs
    }

    private fun buildConfigMap(files: List<KSFile>): Map<KSClassDeclaration, DiffConfigData> {
        val result = mutableMapOf<KSClassDeclaration, DiffConfigData>()
        for (file in files) {
            val annotations = file.annotations.filter { it.shortName.asString() == "DiffConfig" }
            for (annotation in annotations) {
                val args = annotation.arguments.associateBy { it.name?.asString() }

                val forClassType = args["forClass"]?.value as? KSType
                if (forClassType == null) {
                    logger.error("@DiffConfig missing valid forClass argument", file)
                    continue
                }
                val classDecl = forClassType.declaration as? KSClassDeclaration
                if (classDecl == null) {
                    logger.error("@DiffConfig forClass must reference a class declaration", file)
                    continue
                }

                fun strSet(key: String): Set<String> =
                    ((args[key]?.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()).toSet()

                val fieldStrategies: Map<String, DiffStrategy> = buildMap {
                    strSet("merge").forEach { put(it, DiffStrategy.MERGE) }
                    strSet("ignore").forEach { put(it, DiffStrategy.IGNORE) }
                    strSet("append").forEach { put(it, DiffStrategy.APPEND) }
                    strSet("diffMap").forEach { put(it, DiffStrategy.MAP) }
                }

                val visibilityEntryName = (args["visibility"]?.value as? KSType)
                    ?.declaration?.simpleName?.asString()
                val visibility = parseVisibility(visibilityEntryName)

                if (result.containsKey(classDecl)) {
                    logger.error(
                        "Multiple @DiffConfig annotations targeting ${classDecl.qualifiedName?.asString()}",
                        file,
                    )
                    continue
                }

                result[classDecl] = DiffConfigData(
                    classDecl = classDecl,
                    fieldStrategies = fieldStrategies,
                    visibility = visibility,
                    containingFile = file,
                    outputPackage = file.packageName.asString(),
                )
            }
        }
        return result
    }

    // Returns the set of class declarations that appear as MERGE-strategy fields in any DiffConfig.
    private fun collectMergeTargets(configs: Map<KSClassDeclaration, DiffConfigData>): Set<KSClassDeclaration> {
        val targets = mutableSetOf<KSClassDeclaration>()
        for ((classDecl, configData) in configs) {
            val mergeFieldNames = configData.fieldStrategies
                .filterValues { it == DiffStrategy.MERGE }.keys
            for (prop in classDecl.getDeclaredProperties()) {
                if (prop.simpleName.asString() in mergeFieldNames) {
                    (prop.type.resolve().declaration as? KSClassDeclaration)?.let { targets.add(it) }
                }
            }
        }
        return targets
    }

    private fun parseVisibility(entryName: String?): KModifier? = when (entryName) {
        "PUBLIC" -> KModifier.PUBLIC
        "PRIVATE" -> KModifier.PRIVATE
        "PROTECTED" -> KModifier.PROTECTED
        else -> KModifier.INTERNAL
    }

    // Normalizes a Map/MutableMap type (possibly nullable) to a non-nullable immutable Map<K,V>.
    // diffMap() always returns Map<K,V>, so the diff field must use the immutable type.
    private fun TypeName.toImmutableMapType(): TypeName {
        val base = copy(nullable = false)
        return if (base is ParameterizedTypeName &&
            base.rawType.canonicalName in setOf("kotlin.collections.Map", "kotlin.collections.MutableMap")
        ) {
            MAP_CLASS.parameterizedBy(base.typeArguments)
        } else {
            base
        }
    }

    // Returns true if the class has any @DiffIgnore fields, meaning its diff function always
    // returns a value (never DiffOptional.empty()).
    private fun nestedClassAlwaysHasDiff(mergeClassDecl: KSClassDeclaration): Boolean =
        readPropInfos(mergeClassDecl).any { it.strategy == DiffStrategy.IGNORE }

    // Returns the field type in a diff data class for a @DiffMerge property.
    // If the merged class always produces a diff, the field is a direct type (or nullable type);
    // otherwise it is wrapped in DiffOptional.
    private fun mergeFieldType(info: PropertyInfo, nestedDiffType: ClassName): TypeName {
        val mergeDecl = info.mergeClassDecl!!
        return if (nestedClassAlwaysHasDiff(mergeDecl)) {
            if (info.typeName.isNullable) nestedDiffType.copy(nullable = true) else nestedDiffType
        } else {
            DIFF_OPTIONAL.parameterizedBy(nestedDiffType)
        }
    }

    private fun generateDiffFor(
        classDecl: KSClassDeclaration,
        outputPackage: String,
        visibility: KModifier?,
        originatingFiles: List<KSFile>,
    ) {
        val className = classDecl.simpleName.asString()
        val sourceType = classDecl.toClassName()
        val diffType = ClassName(outputPackage, "${className}Diff")

        val propInfos = readPropInfos(classDecl)

        val fileBuilder = FileSpec.builder(outputPackage, "${className}Diff")
            .addFileComment("Auto-generated by DiffPlugin — do not edit")

        val (nestedTypeSpecs, nestedFunSpecs) = buildNestedContent(propInfos, diffType, visibility)

        nestedFunSpecs.forEach { fileBuilder.addFunction(it) }
        fileBuilder.addFunction(buildMainDiffFun(sourceType, diffType, propInfos, visibility))

        val mainDiffFields = propInfos.map { info ->
            val fieldType = when (info.strategy) {
                DiffStrategy.IGNORE -> info.typeName
                DiffStrategy.REPLACE -> DIFF_OPTIONAL.parameterizedBy(info.typeName)
                DiffStrategy.APPEND -> DIFF_OPTIONAL.parameterizedBy(info.typeName.copy(nullable = false))
                DiffStrategy.MAP -> DIFF_OPTIONAL.parameterizedBy(info.typeName.toImmutableMapType())
                DiffStrategy.MERGE -> {
                    val nestedName = "${info.mergeClassDecl!!.simpleName.asString()}Diff"
                    mergeFieldType(info, diffType.nestedClass(nestedName))
                }
            }
            info.name to fieldType
        }
        fileBuilder.addType(
            buildDataClass(
                name = "${className}Diff",
                fields = mainDiffFields,
                visibility = visibility,
                nestedTypes = nestedTypeSpecs,
            )
        )

        fileBuilder.build()
            .writeTo(codeGenerator, aggregating = false, originatingFiles)
    }

    private fun readPropInfos(classDecl: KSClassDeclaration): List<PropertyInfo> {
        val externalStrategies = configMap[classDecl]?.fieldStrategies ?: emptyMap()
        return classDecl.getDeclaredProperties().map { prop ->
            val propName = prop.simpleName.asString()
            val typeName = prop.type.toTypeName()
            val strategy = externalStrategies[propName] ?: run {
                val annotations = prop.annotations.map { it.shortName.asString() }.toSet()
                when {
                    annotations.contains("DiffIgnore") -> DiffStrategy.IGNORE
                    annotations.contains("DiffAppend") -> DiffStrategy.APPEND
                    annotations.contains("DiffMerge") -> DiffStrategy.MERGE
                    annotations.contains("DiffMap") -> DiffStrategy.MAP
                    else -> DiffStrategy.REPLACE
                }
            }
            val resolvedType = prop.type.resolve()
            PropertyInfo(
                name = propName,
                typeName = typeName,
                strategy = strategy,
                mergeClassDecl = if (strategy == DiffStrategy.MERGE) resolvedType.declaration as? KSClassDeclaration else null,
            )
        }.toList()
    }

    // Returns (nested TypeSpecs to embed in the diff class, FunSpecs to emit at file level)
    private fun buildNestedContent(
        propInfos: List<PropertyInfo>,
        containingDiffType: ClassName,
        visibility: KModifier?,
    ): Pair<List<TypeSpec>, List<FunSpec>> {
        val nestedTypeSpecs = mutableListOf<TypeSpec>()
        val nestedFunSpecs = mutableListOf<FunSpec>()

        for (info in propInfos.filter { it.strategy == DiffStrategy.MERGE }) {
            val mergeDecl = info.mergeClassDecl ?: continue
            val nestedDiffSimpleName = "${mergeDecl.simpleName.asString()}Diff"
            val nestedDiffType = containingDiffType.nestedClass(nestedDiffSimpleName)
            val mergeSourceType = mergeDecl.toClassName()
            val nestedPropInfos = readPropInfos(mergeDecl)

            // Recurse into deeper @DiffMerge properties
            val (deeperTypeSpecs, deeperFunSpecs) = buildNestedContent(nestedPropInfos, nestedDiffType, visibility)

            nestedFunSpecs += deeperFunSpecs
            nestedFunSpecs += buildNestedDiffFun(mergeSourceType, nestedDiffType, nestedPropInfos, visibility)

            val nestedDiffFields = nestedPropInfos.map { nestedInfo ->
                val fieldType = when (nestedInfo.strategy) {
                    DiffStrategy.IGNORE -> nestedInfo.typeName
                    DiffStrategy.REPLACE -> DIFF_OPTIONAL.parameterizedBy(nestedInfo.typeName)
                    DiffStrategy.APPEND -> DIFF_OPTIONAL.parameterizedBy(nestedInfo.typeName.copy(nullable = false))
                    DiffStrategy.MAP -> DIFF_OPTIONAL.parameterizedBy(nestedInfo.typeName.toImmutableMapType())
                    DiffStrategy.MERGE -> {
                        val deeperName = "${nestedInfo.mergeClassDecl!!.simpleName.asString()}Diff"
                        mergeFieldType(nestedInfo, nestedDiffType.nestedClass(deeperName))
                    }
                }
                nestedInfo.name to fieldType
            }

            nestedTypeSpecs += buildDataClass(
                name = nestedDiffSimpleName,
                fields = nestedDiffFields,
                visibility = null,
                nestedTypes = deeperTypeSpecs,
            )
        }

        return nestedTypeSpecs to nestedFunSpecs
    }

    // --- Code generation helpers ---

    private fun buildDataClass(
        name: String,
        fields: List<Pair<String, TypeName>>,
        visibility: KModifier?,
        nestedTypes: List<TypeSpec>,
    ): TypeSpec {
        val ctor = FunSpec.constructorBuilder().apply {
            fields.forEach { (n, t) -> addParameter(n, t) }
        }.build()

        return TypeSpec.classBuilder(name)
            .addModifiers(KModifier.DATA)
            .apply { if (visibility != null) addModifiers(visibility) }
            .primaryConstructor(ctor)
            .apply {
                fields.forEach { (n, t) ->
                    addProperty(PropertySpec.builder(n, t).initializer(n).build())
                }
                nestedTypes.forEach { addType(it) }
            }
            .build()
    }

    // Emits the MAP field computation for a single property, used in both buildMainDiffFun
    // and buildNestedDiffFun.
    private fun CodeBlock.Builder.addMapComputation(info: PropertyInfo) {
        val mapResult = "${info.name}MapResult"
        if (info.typeName.isNullable) {
            addStatement(
                "val %N = %M(%N.orEmpty(), other.%N.orEmpty())",
                mapResult, DIFF_MAP, info.name, info.name,
            )
        } else {
            addStatement("val %N = %M(%N, other.%N)", mapResult, DIFF_MAP, info.name, info.name)
        }
        beginControlFlow("val %N = if (%N.isNotEmpty())", "${info.name}Diff", mapResult)
        addStatement("%N.%M()", mapResult, WRAP_OPTIONAL)
        nextControlFlow("else")
        addStatement("%T.empty()", DIFF_OPTIONAL)
        endControlFlow()
    }

    // Emits the MERGE field computation for a single property, used in both buildMainDiffFun
    // and buildNestedDiffFun.
    private fun CodeBlock.Builder.addMergeComputation(info: PropertyInfo) {
        val nestedAlwaysHasDiff = nestedClassAlwaysHasDiff(info.mergeClassDecl!!)
        if (info.typeName.isNullable) {
            if (nestedAlwaysHasDiff) {
                addStatement(
                    "val %N = if (%N != null && other.%N != null) %N.diff(other.%N) else null",
                    "${info.name}Diff", info.name, info.name, info.name, info.name,
                )
            } else {
                beginControlFlow("val %N = if (%N != null && other.%N != null)", "${info.name}Diff", info.name, info.name)
                addStatement("%N.diff(other.%N)", info.name, info.name)
                nextControlFlow("else")
                addStatement("%T.empty()", DIFF_OPTIONAL)
                endControlFlow()
            }
        } else {
            addStatement("val %N = %N.diff(other.%N)", "${info.name}Diff", info.name, info.name)
        }
    }

    private fun buildMainDiffFun(
        sourceType: ClassName,
        diffType: ClassName,
        propInfos: List<PropertyInfo>,
        visibility: KModifier?,
    ): FunSpec {
        val code = CodeBlock.builder()

        for (info in propInfos.filter { it.strategy == DiffStrategy.REPLACE }) {
            code.addStatement("val %N = %M(%N, other.%N)", "${info.name}Diff", MAKE_DIFF, info.name, info.name)
        }

        for (info in propInfos.filter { it.strategy == DiffStrategy.APPEND }) {
            val dropped = "${info.name}Dropped"
            code.addStatement("val %N = other.%N.orEmpty().drop(%N.orEmpty().size)", dropped, info.name, info.name)
            code.beginControlFlow("val %N = if (%N.isNotEmpty())", "${info.name}Diff", dropped)
            code.addStatement("%N.%M()", dropped, WRAP_OPTIONAL)
            code.nextControlFlow("else")
            code.addStatement("%T.empty()", DIFF_OPTIONAL)
            code.endControlFlow()
        }

        for (info in propInfos.filter { it.strategy == DiffStrategy.MAP }) {
            code.addMapComputation(info)
        }

        for (info in propInfos.filter { it.strategy == DiffStrategy.MERGE }) {
            code.addMergeComputation(info)
        }

        code.add("return %T(\n", diffType)
        code.indent()
        for ((i, info) in propInfos.withIndex()) {
            val comma = if (i < propInfos.lastIndex) "," else ""
            when (info.strategy) {
                DiffStrategy.IGNORE -> code.add("%N = other.%N$comma\n", info.name, info.name)
                DiffStrategy.REPLACE, DiffStrategy.APPEND, DiffStrategy.MAP, DiffStrategy.MERGE ->
                    code.add("%N = %N$comma\n", info.name, "${info.name}Diff")
            }
        }
        code.unindent()
        code.add(")\n")

        return FunSpec.builder("diff")
            .receiver(sourceType)
            .addParameter("other", sourceType)
            .returns(diffType)
            .apply { if (visibility != null) addModifiers(visibility) }
            .addCode(code.build())
            .build()
    }

    private fun buildNestedDiffFun(
        mergeSourceType: ClassName,
        nestedDiffType: ClassName,
        propInfos: List<PropertyInfo>,
        visibility: KModifier?,
    ): FunSpec {
        // A class always produces a diff (never returns empty) when it has @DiffIgnore fields,
        // or when it has a non-nullable @DiffMerge field whose nested class always has a diff.
        val alwaysPresent = propInfos.any { info ->
            info.strategy == DiffStrategy.IGNORE ||
                (!info.typeName.isNullable && info.strategy == DiffStrategy.MERGE &&
                    nestedClassAlwaysHasDiff(info.mergeClassDecl!!))
        }
        val returnType = if (alwaysPresent) nestedDiffType else DIFF_OPTIONAL.parameterizedBy(nestedDiffType)

        val code = CodeBlock.builder()

        for (info in propInfos.filter { it.strategy == DiffStrategy.REPLACE }) {
            code.addStatement("val %N = %M(%N, other.%N)", "${info.name}Diff", MAKE_DIFF, info.name, info.name)
        }

        for (info in propInfos.filter { it.strategy == DiffStrategy.APPEND }) {
            val dropped = "${info.name}Dropped"
            code.addStatement("val %N = other.%N.orEmpty().drop(%N.orEmpty().size)", dropped, info.name, info.name)
            code.beginControlFlow("val %N = if (%N.isNotEmpty())", "${info.name}Diff", dropped)
            code.addStatement("%N.%M()", dropped, WRAP_OPTIONAL)
            code.nextControlFlow("else")
            code.addStatement("%T.empty()", DIFF_OPTIONAL)
            code.endControlFlow()
        }

        for (info in propInfos.filter { it.strategy == DiffStrategy.MAP }) {
            code.addMapComputation(info)
        }

        for (info in propInfos.filter { it.strategy == DiffStrategy.MERGE }) {
            code.addMergeComputation(info)
        }

        code.add("\n")

        if (!alwaysPresent) {
            val changeable = propInfos.filter { it.strategy != DiffStrategy.IGNORE }
            if (changeable.isNotEmpty()) {
                val noneChangedParts = changeable.map { info ->
                    // Nullable MERGE where nested always has diff: field is SubDiff?, check for null
                    if (info.strategy == DiffStrategy.MERGE && info.typeName.isNullable &&
                        nestedClassAlwaysHasDiff(info.mergeClassDecl!!)
                    ) {
                        "${info.name}Diff == null"
                    } else {
                        "!${info.name}Diff.exists"
                    }
                }
                code.beginControlFlow("if (%L)", noneChangedParts.joinToString(" && "))
                code.addStatement("return %T.empty()", DIFF_OPTIONAL)
                code.endControlFlow()
                code.add("\n")
            }
        }

        code.add("return %T(\n", nestedDiffType)
        code.indent()
        for ((i, info) in propInfos.withIndex()) {
            val comma = if (i < propInfos.lastIndex) "," else ""
            when (info.strategy) {
                DiffStrategy.IGNORE -> code.add("%N = other.%N$comma\n", info.name, info.name)
                DiffStrategy.REPLACE, DiffStrategy.APPEND, DiffStrategy.MAP, DiffStrategy.MERGE ->
                    code.add("%N = %N$comma\n", info.name, "${info.name}Diff")
            }
        }
        code.unindent()
        if (alwaysPresent) {
            code.add(")\n")
        } else {
            code.add(").%M()\n", WRAP_OPTIONAL)
        }

        return FunSpec.builder("diff")
            .receiver(mergeSourceType)
            .addParameter("other", mergeSourceType)
            .returns(returnType)
            .apply { if (visibility != null) addModifiers(visibility) }
            .addCode(code.build())
            .build()
    }

    private fun Visibility.toKModifier(): KModifier? = when (this) {
        Visibility.PUBLIC -> KModifier.PUBLIC
        Visibility.INTERNAL -> KModifier.INTERNAL
        Visibility.PRIVATE -> KModifier.PRIVATE
        Visibility.PROTECTED -> KModifier.PROTECTED
        else -> null
    }
}
