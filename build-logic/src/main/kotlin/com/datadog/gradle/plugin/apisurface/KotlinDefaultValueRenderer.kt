/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Canonicalizes default parameter expressions without changing string literal contents.
 */
internal object KotlinDefaultValueRenderer {

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
    fun render(expression: PsiElement): String {
        val builder = StringBuilder()
        appendTokens(expression, builder)
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
}
