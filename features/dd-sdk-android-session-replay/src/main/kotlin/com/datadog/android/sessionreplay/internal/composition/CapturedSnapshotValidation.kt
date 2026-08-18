/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentityKind
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeBorder
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedTextStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframeKind
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope

@Suppress("TooManyFunctions") // Each function validates one independent snapshot invariant.
internal class CapturedSnapshotValidation(
    private val snapshot: CapturedFullSnapshot,
    private val validateWireframeDefinitions: Boolean = true
) {
    private val failures = mutableListOf<CaptureValidationFailure>()

    // Wireframe identities referenced by a layer child but absent from wireframesById while
    // validateWireframeDefinitions is false - i.e. delivered outside this mutation. Tracked
    // separately from snapshot.wireframes so validateParentCounts can still catch two references
    // to the same not-yet-delivered wireframe, without resurrecting UNREFERENCED_WIREFRAME/
    // DANGLING_WIREFRAME_REFERENCE for wireframes this validation pass has no definition for.
    private val referencedUnknownWireframes = mutableMapOf<Long, CapturedIdentity>()

    fun validate(): CaptureValidationResult {
        val root = snapshot.root ?: return missingRootResult()
        validateRoot(root)

        // All collections are local captured values and are not mutated while validating.
        @Suppress("UnsafeThirdPartyFunctionCall")
        val layers = listOf(root) + snapshot.layers
        validateDefinitions(layers)

        val layersById = snapshot.layers.associateBy { it.identity.wireId }
        val wireframesById = snapshot.wireframes.associateBy { it.identity.wireId }
        val parentCounts = mutableMapOf<Long, Int>()
        validateReferences(layers, layersById, wireframesById, parentCounts)
        validateParentCounts(parentCounts)
        validateAcyclic(layers, layersById)
        return failures.toValidationResult()
    }

    // This result always contains the single missing-root failure constructed here.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun missingRootResult(): CaptureValidationResult = CaptureValidationResult.Invalid(
        listOf(validationFailure(CaptureValidationErrorCode.MISSING_ROOT))
    )

    private fun validateRoot(root: CapturedLayer) {
        if (root.kind != CapturedLayerKind.SYNTHETIC_SCREEN_ROOT ||
            root.identity.kind != CapturedIdentityKind.SCREEN_ROOT
        ) {
            failures += validationFailure(CaptureValidationErrorCode.INVALID_ROOT, root.identity)
        }
    }

    private fun validateDefinitions(layers: List<CapturedLayer>) {
        val wireframeIdentities = if (validateWireframeDefinitions) {
            snapshot.wireframes.map { it.identity }
        } else {
            emptyList()
        }
        val identities = layers.map { it.identity } + wireframeIdentities
        validateIdentityDefinitions(identities)
        layers.forEach(::validateLayer)
        if (validateWireframeDefinitions) snapshot.wireframes.forEach(::validateWireframe)
    }

    private fun validateIdentityDefinitions(identities: List<CapturedIdentity>) {
        identities.filter { it.scope != snapshot.scope }.forEach {
            failures += validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, it)
        }
        identities.groupBy(CapturedIdentity::wireId).values.forEach { matchingWireIds ->
            if (matchingWireIds.size > 1) {
                matchingWireIds.firstOrNull()?.let {
                    failures += validationFailure(CaptureValidationErrorCode.DUPLICATE_IDENTITY, it)
                }
            }
        }
        identities.groupBy {
            IdentityDefinitionKey(it.scope, it.kind, it.wireframeKind, it.namespace, it.localId)
        }
            .values
            .forEach { matchingIdentities ->
                if (matchingIdentities.size > 1) {
                    matchingIdentities.firstOrNull()?.let {
                        failures += validationFailure(CaptureValidationErrorCode.DUPLICATE_IDENTITY, it)
                    }
                }
            }
    }

    private fun validateLayer(layer: CapturedLayer) {
        if (layer.identity.scope != snapshot.scope) {
            failures += validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, layer.identity)
        }
        if (layer.identity.kind != layer.kind.identityKind || layer.identity.wireframeKind != null) {
            failures += validationFailure(CaptureValidationErrorCode.IDENTITY_KIND_MISMATCH, layer.identity)
        }
        if (layer.bounds.width < 0 || layer.bounds.height < 0) {
            failures += validationFailure(CaptureValidationErrorCode.INVALID_BOUNDS, layer.identity)
        }
        layer.modifiers.forEach { validateModifier(it, layer.identity) }
    }

    private fun validateWireframe(captured: CapturedWireframe) {
        if (captured.identity.scope != snapshot.scope) {
            failures += validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, captured.identity)
        }
        if (captured.identity.kind != CapturedIdentityKind.WIREFRAME ||
            captured.identity.wireframeKind != captured.identityKind
        ) {
            failures += validationFailure(CaptureValidationErrorCode.IDENTITY_KIND_MISMATCH, captured.identity)
        }
        if (captured.bounds.width < 0 || captured.bounds.height < 0) {
            failures += validationFailure(CaptureValidationErrorCode.INVALID_BOUNDS, captured.identity)
        }
        if (captured is CapturedWireframe.Pixel && !captured.resource.isResolved()) {
            failures += validationFailure(
                CaptureValidationErrorCode.UNRESOLVED_PIXEL_RESOURCE,
                captured.identity
            )
        }
        validateStyle(captured.style, captured.identity)
        validateBackgroundGradient(captured.style, captured.identity)
        validateBorder(captured.border, captured.identity)
        if (captured is CapturedWireframe.Text) validateTextStyle(captured.textStyle, captured.identity)
    }

    private fun validateReferences(
        layers: List<CapturedLayer>,
        layersById: Map<Long, CapturedLayer>,
        wireframesById: Map<Long, CapturedWireframe>,
        parentCounts: MutableMap<Long, Int>
    ) {
        layers.forEach { parent ->
            parent.children.forEach { child ->
                validateReference(child, layersById, wireframesById, parentCounts)
            }
        }
    }

    private fun validateReference(
        child: CapturedChild,
        layersById: Map<Long, CapturedLayer>,
        wireframesById: Map<Long, CapturedWireframe>,
        parentCounts: MutableMap<Long, Int>
    ) {
        when (child) {
            is CapturedChild.Layer -> validateLayerReference(child, layersById, parentCounts)
            is CapturedChild.Wireframe -> validateWireframeReference(child, layersById, wireframesById, parentCounts)
        }
    }

    private fun validateLayerReference(
        child: CapturedChild.Layer,
        layersById: Map<Long, CapturedLayer>,
        parentCounts: MutableMap<Long, Int>
    ) {
        if (child.identity.kind == CapturedIdentityKind.WIREFRAME) {
            failures += validationFailure(CaptureValidationErrorCode.IDENTITY_KIND_MISMATCH, child.identity)
        }
        validateReferenceScope(child.identity)
        val definition = layersById[child.identity.wireId]
        when {
            definition == null -> failures += validationFailure(
                CaptureValidationErrorCode.DANGLING_LAYER_REFERENCE,
                child.identity
            )

            definition.identity != child.identity -> failures += validationFailure(
                CaptureValidationErrorCode.REFERENCE_IDENTITY_MISMATCH,
                child.identity
            )

            else -> parentCounts.increment(child.identity.wireId)
        }
    }

    private fun validateWireframeReference(
        child: CapturedChild.Wireframe,
        layersById: Map<Long, CapturedLayer>,
        wireframesById: Map<Long, CapturedWireframe>,
        parentCounts: MutableMap<Long, Int>
    ) {
        validateWireframeReferenceIdentity(child)
        val definition = wireframesById[child.identity.wireId]
        when {
            // Only a wholly absent definition is tolerated during mutation validation - the
            // wireframe may be delivered outside this mutation - but it's still counted, or a
            // second reference introduced by this mutation's layer changes would silently give
            // the same wireframe multiple parents. See validateParentCounts. A definition that
            // DOES exist under this wireId but doesn't match is a real collision (e.g. two
            // WebViews from different owners landing on the same unshifted slot id) and is
            // rejected below regardless of validateWireframeDefinitions. An undelivered wireframe
            // id colliding with an existing layer or the root is also a real collision - full
            // snapshot validation would reject the equivalent state as DUPLICATE_IDENTITY - so it
            // isn't waved through just because no wireframe claims that id yet.
            definition == null && !validateWireframeDefinitions -> {
                val collidesWithLayer = layersById[child.identity.wireId] != null ||
                    snapshot.root?.identity?.wireId == child.identity.wireId
                if (collidesWithLayer) {
                    failures += validationFailure(CaptureValidationErrorCode.DUPLICATE_IDENTITY, child.identity)
                } else {
                    parentCounts.increment(child.identity.wireId)
                    referencedUnknownWireframes[child.identity.wireId] = child.identity
                }
            }

            definition == null -> failures += validationFailure(
                CaptureValidationErrorCode.DANGLING_WIREFRAME_REFERENCE,
                child.identity
            )

            definition.identity != child.identity -> failures += validationFailure(
                CaptureValidationErrorCode.REFERENCE_IDENTITY_MISMATCH,
                child.identity
            )

            else -> parentCounts.increment(child.identity.wireId)
        }
    }

    private fun validateWireframeReferenceIdentity(child: CapturedChild.Wireframe) {
        if (child.identity.kind != CapturedIdentityKind.WIREFRAME) {
            failures += validationFailure(CaptureValidationErrorCode.IDENTITY_KIND_MISMATCH, child.identity)
        }
        validateReferenceScope(child.identity)
    }

    private fun validateReferenceScope(identity: CapturedIdentity) {
        if (identity.scope != snapshot.scope) {
            failures += validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, identity)
        }
    }

    private fun validateParentCounts(parentCounts: Map<Long, Int>) {
        snapshot.layers.forEach {
            validateParentCount(it.identity, parentCounts, CaptureValidationErrorCode.UNREFERENCED_LAYER)
        }
        // Multiple parents on a wireframe is invalid regardless of validateWireframeDefinitions - a
        // mutation's layer additions/updates can introduce a second reference to an existing base
        // wireframe even though wireframes themselves aren't part of the mutation. Zero parents, by
        // contrast, is expected during mutations: CapturedMutationSet has no way to represent a
        // wireframe addition, so a layer's children can legitimately reference a wireframe that isn't
        // in base.wireframes yet (delivered separately), and only get counted once it is.
        snapshot.wireframes.forEach {
            validateWireframeParentCount(it, parentCounts)
        }
        // Same invariant as above, for wireframe identities this pass has no definition for: still
        // referenced ≥1 time by construction (that's how they ended up in this map), so only the
        // multiple-parents case is meaningful here.
        referencedUnknownWireframes.forEach { (wireId, identity) ->
            if ((parentCounts[wireId] ?: 0) > 1) {
                failures += validationFailure(CaptureValidationErrorCode.MULTIPLE_PARENTS, identity)
            }
        }
    }

    private fun validateWireframeParentCount(
        wireframe: CapturedWireframe,
        parentCounts: Map<Long, Int>
    ) {
        when (parentCounts[wireframe.identity.wireId] ?: 0) {
            0 -> if (validateWireframeDefinitions && !wireframe.isHiddenSlot) {
                failures += validationFailure(
                    CaptureValidationErrorCode.UNREFERENCED_WIREFRAME,
                    wireframe.identity
                )
            }

            1 -> Unit
            else -> failures += validationFailure(
                CaptureValidationErrorCode.MULTIPLE_PARENTS,
                wireframe.identity
            )
        }
    }

    private fun validateParentCount(
        identity: CapturedIdentity,
        parentCounts: Map<Long, Int>,
        unreferencedCode: CaptureValidationErrorCode
    ) {
        when (parentCounts[identity.wireId] ?: 0) {
            0 -> failures += validationFailure(unreferencedCode, identity)
            1 -> Unit
            else -> failures += validationFailure(CaptureValidationErrorCode.MULTIPLE_PARENTS, identity)
        }
    }

    // The regular expression is compiled from a constant controlled by this class.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun validateModifier(modifier: CapturedModifier, identity: CapturedIdentity) {
        val valid = when (modifier) {
            is CapturedModifier.BrightnessBias -> modifier.value in -1.0..1.0
            is CapturedModifier.Clip -> modifier.path.isNotBlank()
            is CapturedModifier.ColorMatrix ->
                modifier.values.size == COLOR_MATRIX_SIZE && modifier.values.all { it.isFinite() }
            is CapturedModifier.GaussianBlur -> modifier.radius >= 0 && modifier.radius.isFinite()
            is CapturedModifier.MaskImage -> modifier.resourceId.isNotBlank()
            is CapturedModifier.Opacity -> modifier.value in 0.0..1.0
            is CapturedModifier.Saturate -> modifier.value >= 0 && modifier.value.isFinite()
            is CapturedModifier.Shadow ->
                modifier.radius >= 0 && modifier.radius.isFinite() &&
                    modifier.offsetX.isFinite() && modifier.offsetY.isFinite() &&
                    HEX_COLOR.matches(modifier.color)
        }
        if (!valid) failures += validationFailure(CaptureValidationErrorCode.INVALID_MODIFIER, identity)
    }

    // The regular expression is compiled from a constant controlled by this class.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun validateStyle(style: CapturedShapeStyle?, identity: CapturedIdentity) {
        if (style == null) return
        val opacity = style.opacity
        val backgroundColor = style.backgroundColor
        val valid = (opacity == null || opacity.toDouble() in 0.0..1.0) &&
            style.cornerRadius.isFiniteOrNull() &&
            (backgroundColor == null || HEX_COLOR.matches(backgroundColor))
        if (!valid) failures += validationFailure(CaptureValidationErrorCode.INVALID_STYLE, identity)
    }

    // The regular expression is compiled from a constant controlled by this class.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun validateBorder(border: CapturedShapeBorder?, identity: CapturedIdentity) {
        if (border == null) return
        if (!HEX_COLOR.matches(border.color)) {
            failures += validationFailure(CaptureValidationErrorCode.INVALID_STYLE, identity)
        }
    }

    // The regular expression is compiled from a constant controlled by this class.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun validateTextStyle(textStyle: CapturedTextStyle, identity: CapturedIdentity) {
        if (!HEX_COLOR.matches(textStyle.color)) {
            failures += validationFailure(CaptureValidationErrorCode.INVALID_STYLE, identity)
        }
    }

    private fun Number?.isFiniteOrNull(): Boolean = this == null || toDouble().isFinite()

    // The regular expression is compiled from a constant controlled by this class.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun validateBackgroundGradient(style: CapturedShapeStyle?, identity: CapturedIdentity) {
        val gradient = style?.backgroundGradient ?: return
        val stops = gradient.stops
        val endpoints = listOf(gradient.startPoint.x, gradient.startPoint.y, gradient.endPoint.x, gradient.endPoint.y)
        val valid = stops.size >= MIN_GRADIENT_STOPS &&
            stops.all { it.offset in 0.0..1.0 && HEX_COLOR.matches(it.color) } &&
            stops.zipWithNext().all { (previous, next) -> previous.offset <= next.offset } &&
            endpoints.all { it.isFinite() }
        if (!valid) failures += validationFailure(CaptureValidationErrorCode.INVALID_GRADIENT, identity)
    }

    // Set membership operations cannot fail for these locally owned mutable sets.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun validateAcyclic(layers: List<CapturedLayer>, layersById: Map<Long, CapturedLayer>) {
        val visiting = mutableSetOf<Long>()
        val visited = mutableSetOf<Long>()

        fun visit(layer: CapturedLayer) {
            if (!visiting.add(layer.identity.wireId)) {
                failures += validationFailure(CaptureValidationErrorCode.CYCLE, layer.identity)
                return
            }
            if (visited.add(layer.identity.wireId)) {
                layer.children.filterIsInstance<CapturedChild.Layer>().forEach {
                    layersById[it.identity.wireId]?.let(::visit)
                }
            }
            visiting.remove(layer.identity.wireId)
        }
        // Every layer is checked as a potential DFS root, not just the tree root, so a cycle among
        // layers unreachable from root (e.g. two layers that only reference each other) is still
        // caught - such a component would otherwise have every node reporting a valid one-parent
        // count without ever being visited by a root-only traversal.
        layers.filterNot { it.identity.wireId in visited }.forEach(::visit)
    }

    private fun PixelResource.isResolved(): Boolean =
        this is PixelResource.Resolved && resourceId.isNotBlank()

    private val CapturedWireframe.isHiddenSlot: Boolean
        get() = when (this) {
            is CapturedWireframe.WebView -> isVisible == false
            else -> false
        }

    private fun MutableMap<Long, Int>.increment(id: Long) {
        this[id] = (this[id] ?: 0) + 1
    }

    private val CapturedLayerKind.identityKind: CapturedIdentityKind
        get() = when (this) {
            CapturedLayerKind.SYNTHETIC_SCREEN_ROOT -> CapturedIdentityKind.SCREEN_ROOT
            CapturedLayerKind.WINDOW_ROOT -> CapturedIdentityKind.WINDOW
            CapturedLayerKind.NATIVE_VIEW -> CapturedIdentityKind.VIEW
            CapturedLayerKind.COMPOSE_HOST -> CapturedIdentityKind.COMPOSE_HOST
            CapturedLayerKind.COMPOSE_NODE -> CapturedIdentityKind.COMPOSE_NODE
            CapturedLayerKind.COMPOSITION_LAYER -> CapturedIdentityKind.LAYER
        }

    private val CapturedWireframe.identityKind: CapturedWireframeKind
        get() = when (this) {
            is CapturedWireframe.Shape -> CapturedWireframeKind.SHAPE
            is CapturedWireframe.Text -> CapturedWireframeKind.TEXT
            is CapturedWireframe.Pixel -> CapturedWireframeKind.IMAGE
            is CapturedWireframe.PrivacyPlaceholder -> CapturedWireframeKind.PLACEHOLDER
            is CapturedWireframe.WebView -> CapturedWireframeKind.WEB_VIEW
        }

    private val CapturedWireframe.style: CapturedShapeStyle?
        get() = when (this) {
            is CapturedWireframe.Shape -> style
            is CapturedWireframe.Text -> style
            is CapturedWireframe.WebView -> style
            is CapturedWireframe.Pixel -> style
            is CapturedWireframe.PrivacyPlaceholder -> null
        }

    private val CapturedWireframe.border: CapturedShapeBorder?
        get() = when (this) {
            is CapturedWireframe.Shape -> border
            is CapturedWireframe.Text -> border
            is CapturedWireframe.WebView -> border
            is CapturedWireframe.Pixel -> border
            is CapturedWireframe.PrivacyPlaceholder -> null
        }

    private companion object {
        const val COLOR_MATRIX_SIZE = 20
        const val MIN_GRADIENT_STOPS = 2
        val HEX_COLOR = Regex("^#[A-Fa-f0-9]{6}([A-Fa-f0-9]{2})?$")
    }

    private data class IdentityDefinitionKey(
        val scope: RumViewIdentityScope,
        val kind: CapturedIdentityKind,
        val wireframeKind: CapturedWireframeKind?,
        val namespace: List<String>,
        val localId: String
    )
}
