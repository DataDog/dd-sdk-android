/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class CapturedSnapshotIdentityValidationTest {

    private val tree = compositionTestTree()
    private val first = tree.layer.identity
    private val second = tree.factory.layer(tree.window, "second")

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M report first wire collisions in encounter order W validate`(validateWireframes: Boolean) {
        // Given
        val secondCollision = second.copy(localId = "second-collision")
        val firstCollision = first.copy(localId = "first-collision")
        val thirdCollision = first.copy(localId = "third-collision")
        val snapshot = snapshotWithLayers(listOf(first, second, secondCollision, firstCollision, thirdCollision))

        // When
        val result = CapturedSnapshotValidation(snapshot, validateWireframes).validate()

        // Then
        assertThat(result.duplicateFailures()).containsExactly(duplicate(first), duplicate(second))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M report first definition collisions in encounter order W validate`(validateWireframes: Boolean) {
        // Given
        val secondCollision = second.copy(wireId = second.wireId + 1_000_000)
        val firstCollision = first.copy(wireId = first.wireId + 2_000_000)
        val thirdCollision = first.copy(wireId = first.wireId + 3_000_000)
        val snapshot = snapshotWithLayers(listOf(first, second, secondCollision, firstCollision, thirdCollision))

        // When
        val result = CapturedSnapshotValidation(snapshot, validateWireframes).validate()

        // Then
        assertThat(result.duplicateFailures()).containsExactly(duplicate(first), duplicate(second))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M report wire collisions before definition collisions W validate`(validateWireframes: Boolean) {
        // Given
        val snapshot = snapshotWithLayers(listOf(first, second, second, first, first))

        // When
        val result = CapturedSnapshotValidation(snapshot, validateWireframes).validate()

        // Then
        assertThat(result.duplicateFailures()).containsExactly(
            duplicate(first),
            duplicate(second),
            duplicate(first),
            duplicate(second)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M report scopes before ordered duplicates W validate`(validateWireframes: Boolean) {
        // Given
        val wrongFirst = first.copy(scope = RumViewIdentityScope("other-view"))
        val wrongSecond = second.copy(scope = RumViewIdentityScope("other-view"))
        val snapshot = snapshotWithLayers(listOf(wrongFirst, wrongSecond, wrongSecond, wrongFirst))

        // When
        val result = CapturedSnapshotValidation(snapshot, validateWireframes).validate()

        // Then
        assertThat((result as CaptureValidationResult.Invalid).failures.take(8)).containsExactly(
            validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, wrongFirst),
            validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, wrongSecond),
            validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, wrongSecond),
            validationFailure(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE, wrongFirst),
            duplicate(wrongFirst),
            duplicate(wrongSecond),
            duplicate(wrongFirst),
            duplicate(wrongSecond)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M distinguish definition keys with equal hashes W validate`(validateWireframes: Boolean) {
        // Given
        val firstKey = first.copy(namespace = listOf("Aa"), localId = "same")
        val secondKey = second.copy(namespace = listOf("BB"), localId = "same")
        val secondCollision = secondKey.copy(wireId = secondKey.wireId + 1_000_000)
        val firstCollision = firstKey.copy(wireId = firstKey.wireId + 2_000_000)
        val snapshot = snapshotWithLayers(listOf(firstKey, secondKey, secondCollision, firstCollision))
        assertThat(firstKey.namespace.hashCode()).isEqualTo(secondKey.namespace.hashCode())

        // When
        val result = CapturedSnapshotValidation(snapshot, validateWireframes).validate()

        // Then
        assertThat(result.duplicateFailures()).containsExactly(duplicate(firstKey), duplicate(secondKey))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M report root as first identity on wire collision W validate`(validateWireframes: Boolean) {
        // Given
        val rootIdentity = tree.root.identity
        val firstCollision = first.copy(wireId = rootIdentity.wireId)
        val secondCollision = second.copy(wireId = rootIdentity.wireId)
        val snapshot = snapshotWithLayers(listOf(firstCollision, secondCollision))

        // When
        val result = CapturedSnapshotValidation(snapshot, validateWireframes).validate()

        // Then
        assertThat(result.duplicateFailures()).containsExactly(duplicate(rootIdentity))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M validate wireframe definitions only when required W validate`(validateWireframes: Boolean) {
        // Given
        val original = tree.wireframe as CapturedWireframe.Shape
        val collision = original.copy(identity = original.identity.copy(wireId = original.identity.wireId + 1_000_000))
        val snapshot = tree.snapshot.copy(wireframes = listOf(original, collision))

        // When
        val result = CapturedSnapshotValidation(snapshot, validateWireframes).validate()

        // Then
        if (validateWireframes) {
            assertThat(result.duplicateFailures()).containsExactly(duplicate(original.identity))
        } else {
            assertThat(result).isEqualTo(CaptureValidationResult.Valid)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M accept root without children W validate`(validateWireframes: Boolean) {
        // Given
        val snapshot = snapshotWithLayers(emptyList())

        // When
        val result = CapturedSnapshotValidation(snapshot, validateWireframes).validate()

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    private fun snapshotWithLayers(identities: List<CapturedIdentity>): CapturedFullSnapshot = tree.snapshot.copy(
        root = tree.root.copy(children = identities.map(CapturedChild::Layer)),
        layers = identities.map { tree.layer.copy(identity = it, children = emptyList()) },
        wireframes = emptyList()
    )

    private fun duplicate(identity: CapturedIdentity): CaptureValidationFailure =
        validationFailure(CaptureValidationErrorCode.DUPLICATE_IDENTITY, identity)

    private fun CaptureValidationResult.duplicateFailures(): List<CaptureValidationFailure> =
        (this as CaptureValidationResult.Invalid).failures.filter {
            it.code == CaptureValidationErrorCode.DUPLICATE_IDENTITY
        }
}
