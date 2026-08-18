/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

internal enum class CaptureValidationErrorCode {
    MISSING_ROOT,
    INVALID_ROOT,
    DUPLICATE_IDENTITY,
    WRONG_IDENTITY_SCOPE,
    IDENTITY_KIND_MISMATCH,
    REFERENCE_IDENTITY_MISMATCH,
    DANGLING_LAYER_REFERENCE,
    DANGLING_WIREFRAME_REFERENCE,
    UNREFERENCED_LAYER,
    UNREFERENCED_WIREFRAME,
    MULTIPLE_PARENTS,
    CYCLE,
    INVALID_BOUNDS,
    UNRESOLVED_PIXEL_RESOURCE,
    INVALID_MODIFIER,
    INVALID_GRADIENT,
    INVALID_STYLE,
    DUPLICATE_MUTATION_OPERATION,
    CONTRADICTORY_MUTATION,
    UNKNOWN_MUTATION_TARGET,
    INVALID_ROOT_REPLACEMENT
}

internal data class CaptureValidationFailure(
    val code: CaptureValidationErrorCode,
    val identity: CapturedIdentity? = null,
    val detail: String? = null
)

internal sealed interface CaptureValidationResult {
    object Valid : CaptureValidationResult

    data class Invalid(
        val failures: List<CaptureValidationFailure>
    ) : CaptureValidationResult
}

internal interface CapturedTreeValidator {
    fun validate(snapshot: CapturedFullSnapshot): CaptureValidationResult

    fun validate(
        mutation: CapturedMutationSet,
        base: CapturedFullSnapshot
    ): CaptureValidationResult
}

internal class DefaultCapturedTreeValidator : CapturedTreeValidator {

    override fun validate(snapshot: CapturedFullSnapshot): CaptureValidationResult =
        CapturedSnapshotValidation(snapshot).validate()

    override fun validate(
        mutation: CapturedMutationSet,
        base: CapturedFullSnapshot
    ): CaptureValidationResult = CapturedMutationValidation(mutation, base).validate()
}

internal fun validationFailure(
    code: CaptureValidationErrorCode,
    identity: CapturedIdentity? = null,
    detail: String? = null
) = CaptureValidationFailure(code, identity, detail)

internal fun List<CaptureValidationFailure>.toValidationResult(): CaptureValidationResult =
    if (isEmpty()) CaptureValidationResult.Valid else CaptureValidationResult.Invalid(this)
