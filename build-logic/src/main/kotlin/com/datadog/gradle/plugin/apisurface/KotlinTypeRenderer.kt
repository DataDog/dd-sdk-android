/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

/**
 * Renders declared types using one file's imports, without resolving symbols.
 */
internal class KotlinTypeRenderer(private val imports: Map<String, String>) {

    fun render(reference: KtTypeReference): String {
        val element = reference.typeElement ?: return ""
        return renderTypeElement(element)
    }

    /**
     * Keeps the qualifier chain as written, expanding only its first segment through imports.
     */
    fun render(type: KtUserType): String {
        val segments = mutableListOf<KtUserType>()
        var current: KtUserType? = type
        while (current != null) {
            segments.add(0, current)
            current = current.qualifier
        }

        return segments.foldIndexed("") { index, aggregate, segment ->
            val typeName = segment.referencedName.orEmpty()
            val generics = segment.typeArgumentList?.arguments
                ?.joinToString(", ", prefix = "<", postfix = ">") { renderProjection(it) }
                ?: ""
            if (index == 0) {
                (imports[typeName] ?: typeName) + generics
            } else {
                "$aggregate.$typeName$generics"
            }
        }
    }

    private fun renderTypeElement(element: KtTypeElement): String = when (element) {
        is KtNullableType -> (element.innerType?.let(::renderTypeElement) ?: "") + "?"
        is KtFunctionType -> renderFunctionType(element)
        is KtUserType -> render(element)
        else -> element.text
    }

    private fun renderProjection(projection: KtTypeProjection): String =
        projection.typeReference?.let(::render) ?: "*"

    private fun renderFunctionType(type: KtFunctionType): String {
        val receiver = type.receiverTypeReference?.let(::render)
        val params = type.parameters.joinToString(", ") { it.typeReference?.let(::render).orEmpty() }
        val returns = type.returnTypeReference?.let(::render).orEmpty()

        return if (receiver == null) {
            "($params) -> $returns"
        } else {
            "$receiver.($params) -> $returns"
        }
    }
}
