/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

@Suppress("TooManyFunctions") // Each function validates one independent mutation invariant.
internal class CapturedMutationValidation(
    private val mutation: CapturedMutationSet,
    private val base: CapturedFullSnapshot
) {
    private val failures = mutableListOf<CaptureValidationFailure>()
    private val adds = mutation.adds.valueOrEmpty()
    private val removes = mutation.removes.valueOrEmpty()
    private val updates = mutation.updates.valueOrEmpty()
    private val baseLayers = base.layers.associateBy { it.identity.wireId }
    private val baseWireframes = base.wireframes.associateBy { it.identity.wireId }

    fun validate(): CaptureValidationResult {
        validateScope()
        validateOperationDuplicates()
        validateContradictions()
        validateTargets()
        val replacementRoot = validateReplacementRoot()

        if (failures.isNotEmpty()) return CaptureValidationResult.Invalid(failures)
        return CapturedSnapshotValidation(
            snapshot = effectiveSnapshot(replacementRoot),
            validateWireframeDefinitions = false
        ).validate()
    }

    private fun validateScope() {
        if (mutation.scope != base.scope) {
            failures += validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE)
        }
    }

    private fun validateOperationDuplicates() {
        validateOperationDuplicates(adds.map { it.identity })
        validateOperationDuplicates(removes)
        validateOperationDuplicates(updates.map { it.identity })
    }

    private fun validateOperationDuplicates(identities: List<CapturedIdentity>) {
        identities.groupBy(CapturedIdentity::wireId).values.forEach { operations ->
            if (operations.size > 1) {
                operations.firstOrNull()?.let {
                    failures += validationFailure(CaptureValidationErrorCode.DUPLICATE_MUTATION_OPERATION, it)
                }
            }
        }
    }

    private fun validateContradictions() {
        val addIds = adds.map { it.identity.wireId }.toSet()
        val removeIds = removes.map(CapturedIdentity::wireId).toSet()
        val updateIds = updates.map { it.identity.wireId }.toSet()
        (addIds intersect removeIds).forEach { reportContradiction(it) }
        (addIds intersect updateIds).forEach { reportContradiction(it) }
        (removeIds intersect updateIds).forEach { reportContradiction(it) }
    }

    private fun reportContradiction(wireId: Long) {
        val identity = adds.firstOrNull { it.identity.wireId == wireId }?.identity
            ?: removes.firstOrNull { it.wireId == wireId }
            ?: updates.firstOrNull { it.identity.wireId == wireId }?.identity
        failures += validationFailure(CaptureValidationErrorCode.CONTRADICTORY_MUTATION, identity)
    }

    private fun validateTargets() {
        removes.forEach(::validateRemoval)
        updates.forEach(::validateUpdate)
        adds.forEach(::validateAddition)
    }

    private fun validateRemoval(identity: CapturedIdentity) {
        if (identity.scope != mutation.scope) {
            failures += validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, identity)
        }
        validateExistingTarget(identity)
    }

    private fun validateUpdate(update: CapturedLayerUpdate) {
        if (update.identity.scope != mutation.scope) {
            failures += validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, update.identity)
        }
        validateExistingTarget(update.identity)
    }

    private fun validateExistingTarget(identity: CapturedIdentity) {
        val target = baseLayers[identity.wireId]
        when {
            target == null -> failures += validationFailure(
                CaptureValidationErrorCode.UNKNOWN_MUTATION_TARGET,
                identity
            )

            target.identity != identity -> failures += validationFailure(
                CaptureValidationErrorCode.REFERENCE_IDENTITY_MISMATCH,
                identity
            )
        }
    }

    private fun validateAddition(layer: CapturedLayer) {
        if (layer.identity.scope != mutation.scope) {
            failures += validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, layer.identity)
        }
        val identityAlreadyExists = baseLayers[layer.identity.wireId] != null ||
            baseWireframes[layer.identity.wireId] != null ||
            base.root?.identity?.wireId == layer.identity.wireId
        if (identityAlreadyExists) {
            failures += validationFailure(CaptureValidationErrorCode.CONTRADICTORY_MUTATION, layer.identity)
        }
    }

    private fun validateReplacementRoot(): CapturedLayer? {
        val replacement = (mutation.root as? CapturedChange.Set)?.value ?: return null
        val isValid = replacement.kind == CapturedLayerKind.SYNTHETIC_SCREEN_ROOT &&
            replacement.identity.kind == CapturedIdentityKind.SCREEN_ROOT &&
            replacement.identity.scope == mutation.scope &&
            (base.root == null || replacement.identity == base.root.identity) &&
            // Only meaningful when base.root == null (a stable root's own wireId can't newly
            // collide with a wireframe): establishing the root for the first time via a mutation
            // must not claim a wireId an existing wireframe already owns, or full-snapshot
            // validation of the equivalent state would reject it as DUPLICATE_IDENTITY.
            baseWireframes[replacement.identity.wireId] == null
        if (!isValid) {
            failures += validationFailure(CaptureValidationErrorCode.INVALID_ROOT_REPLACEMENT, replacement.identity)
        }
        return replacement
    }

    // The map is a mutable copy owned by this validation operation.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun effectiveSnapshot(replacementRoot: CapturedLayer?): CapturedFullSnapshot {
        val effectiveLayers = baseLayers.toMutableMap()
        removes.forEach { effectiveLayers.remove(it.wireId) }
        adds.forEach { effectiveLayers[it.identity.wireId] = it }
        updates.forEach { update ->
            effectiveLayers[update.identity.wireId]?.let {
                effectiveLayers[update.identity.wireId] = it.apply(update)
            }
        }
        return base.copy(
            timestamp = mutation.timestamp,
            root = replacementRoot ?: base.root,
            layers = effectiveLayers.values.toList()
        )
    }

    private fun CapturedLayer.apply(update: CapturedLayerUpdate): CapturedLayer {
        val effectiveCompositeOperation = when (val operation = update.compositeOperation) {
            is CapturedChange.Set -> operation.value
            CapturedChange.Unchanged -> compositeOperation
        }
        return copy(
            bounds = bounds.copy(
                x = update.x.valueOr(bounds.x),
                y = update.y.valueOr(bounds.y),
                width = update.width.valueOr(bounds.width),
                height = update.height.valueOr(bounds.height)
            ),
            children = update.children.valueOr(children),
            modifiers = update.modifiers.valueOr(modifiers),
            compositeOperation = effectiveCompositeOperation
        )
    }

    private fun <T> CapturedChange<List<T>>.valueOrEmpty(): List<T> =
        (this as? CapturedChange.Set)?.value.orEmpty()

    private fun <T> CapturedChange<T>.valueOr(default: T): T =
        (this as? CapturedChange.Set)?.value ?: default
}
