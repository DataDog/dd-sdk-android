/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package androidx.compose.foundation.selection

import androidx.compose.ui.Modifier

/**
 * Test stub class for [LayoutNodeUtilsTest].
 *
 * The production code [LayoutNodeUtils.resolveLayoutNode] identifies clickable/scrollable elements
 * by checking `modifier::class.qualifiedName` against known Compose class names. Since `::class`
 * is a Kotlin language intrinsic that cannot be mocked, we create real stub classes with the exact
 * package and class names that the production code expects. When instantiated, these stubs return
 * the correct qualified name (e.g., "androidx.compose.foundation.selection.SelectableElement"),
 * allowing us to unit test the class name matching logic.
 */
internal class SelectableElement : Modifier.Element
