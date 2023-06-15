/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.ArrayList
import java.util.LinkedList

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MutationResolverTest {

    lateinit var testedMutationResolver: MutationResolver

    @BeforeEach
    fun `set up`() {
        testedMutationResolver = MutationResolver()
    }

    // region adds mutations

    @Test
    fun `M identify the newly added wireframes W resolveMutations { end }`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList {
            forge.getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeAddedWireframes = forge.aList { forge.getForgery<MobileSegment.Wireframe>() }
        val fakeCurrentSnapshot = fakePrevSnapshot.toMutableList() + fakeAddedWireframes
        var lastPrevWireframe = fakePrevSnapshot.last()
        val expectedAdditions = LinkedList<MobileSegment.Add>()
        fakeAddedWireframes.forEach {
            expectedAdditions.add(MobileSegment.Add(lastPrevWireframe.id(), it))
            lastPrevWireframe = it
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isEqualTo(expectedAdditions)
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isNullOrEmpty()
    }

    @Test
    fun `M identify the newly added wireframes W resolveMutations { middle }`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(forge.anInt(min = 4, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeAddedWireframes = forge.aList { forge.getForgery<MobileSegment.Wireframe>() }
        val middle = fakePrevSnapshot.size / 2
        val remaining = Math.max(0, fakePrevSnapshot.size - middle)
        val fakeCurrentSnapshot = fakePrevSnapshot.take(middle) + fakeAddedWireframes +
            fakePrevSnapshot.takeLast(remaining)
        var lastPrevWireframe = if (middle > 0) fakePrevSnapshot[middle - 1] else null
        val expectedAdditions = LinkedList<MobileSegment.Add>()
        fakeAddedWireframes.forEach {
            expectedAdditions.add(MobileSegment.Add(lastPrevWireframe?.id(), it))
            lastPrevWireframe = it
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isEqualTo(expectedAdditions)
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isNullOrEmpty()
    }

    @Test
    fun `M identify the newly added wireframes W resolveMutations { beginning }`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList {
            forge.getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeAddedWireframes = forge.aList { forge.getForgery<MobileSegment.Wireframe>() }
        val fakeCurrentSnapshot = fakeAddedWireframes + fakePrevSnapshot.toMutableList()
        var lastPrevWireframe: MobileSegment.Wireframe? = null
        val expectedAdditions = LinkedList<MobileSegment.Add>()
        fakeAddedWireframes.forEach {
            expectedAdditions.add(MobileSegment.Add(lastPrevWireframe?.id(), it))
            lastPrevWireframe = it
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isEqualTo(expectedAdditions)
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isNullOrEmpty()
    }

    @Test
    fun `M identify the newly added wireframes W resolveMutations {prevSnapshot is empty}`(
        forge: Forge
    ) {
        // Given
        val fakePrevSnapshot = emptyList<MobileSegment.Wireframe>()
        val fakeAddedWireframes = forge.aList { forge.getForgery<MobileSegment.Wireframe>() }
        val fakeCurrentSnapshot = fakePrevSnapshot.toMutableList() + fakeAddedWireframes
        var lastPrevWireframe: MobileSegment.Wireframe? = null
        val expectedAdds = LinkedList<MobileSegment.Add>()
        fakeAddedWireframes.forEach {
            expectedAdds.add(MobileSegment.Add(lastPrevWireframe?.id(), it))
            lastPrevWireframe = it
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isEqualTo(expectedAdds)
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isNullOrEmpty()
    }

    // endregion

    // region removes mutations

    @Test
    fun `M identify the removed wireframes W resolveMutations`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeRemovedSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeCurrentSnapshot = fakePrevSnapshot.drop(fakeRemovedSize)
        val expectedRemovals = fakePrevSnapshot
            .take(fakeRemovedSize)
            .map { MobileSegment.Remove(it.id()) }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isEqualTo(expectedRemovals)
        assertThat(mutations?.updates).isNullOrEmpty()
    }

    // endregion

    // region order changed mutations

    @Test
    fun `M identify the newly added and remove wireframe W resolveMutations { order changed }`(
        forge: Forge
    ) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 3)) {
            forge.getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeCurrentSnapshot = fakePrevSnapshot.reversed()
        val expectedRemovals = fakePrevSnapshot
            .map { MobileSegment.Remove(it.id()) }
        val expectedAdds = mutableListOf<MobileSegment.Add>()
        fakeCurrentSnapshot.forEachIndexed { index, wireframe ->
            val previousId = if (index > 0) fakeCurrentSnapshot[index - 1].id() else null
            expectedAdds.add(
                com.datadog.android.sessionreplay.model.MobileSegment.Add
                (previousId, wireframe)
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).containsExactlyInAnyOrder(*expectedAdds.toTypedArray())
        assertThat(mutations?.removes).containsExactlyInAnyOrder(*expectedRemovals.toTypedArray())
        assertThat(mutations?.updates).isNullOrEmpty()
    }
    // endregion

    // region ShapeWireframe update mutations

    @Test
    fun `M identify the updated wireframes W resolveMutations {Shape, position}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ShapeWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map { it.copy(x = forge.aLong(), y = forge.aLong()) }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                id = it.id(),
                x = it.x,
                y = it.y
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Shape, dimensions}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ShapeWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map { it.copy(width = forge.aLong(), height = forge.aLong()) }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                id = it.id(),
                width = it.width,
                height = it.height
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Shape, border}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ShapeWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    border = MobileSegment.ShapeBorder(
                        forge.aStringMatching("#[0-9A-F]{6}FF"),
                        forge.aPositiveLong(strict = true)
                    )
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                id = it.id(),
                border = it.border
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Shape, shapeStyle}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ShapeWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    shapeStyle = MobileSegment.ShapeStyle(
                        backgroundColor = forge.aStringMatching("#[0-9A-F]{6}FF"),
                        opacity = forge.aFloat(min = 0f, max = 1f),
                        cornerRadius = forge.aPositiveLong()
                    )
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                id = it.id(),
                shapeStyle = it.shapeStyle
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Shape, clip}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ShapeWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    clip = forge.forgeDifferent(it.clip)
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                id = it.id(),
                clip = it.clip
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M provide an empty clip W resolveMutations {Shape, new clip is null}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ShapeWireframe::class.java)
                .copy(clip = forge.getForgery())
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    clip = null
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                id = it.id(),
                clip = MobileSegment.WireframeClip(0, 0, 0, 0)
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M return empty mutations W resolveMutation {Shape wireframes types are not matching}`(
        forge: Forge
    ) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ShapeWireframe::class.java)
        }

        val fakeCurrentSnapshot = fakePrevSnapshot.map {
            forge.getForgery(
                MobileSegment.Wireframe
                    .TextWireframe::class.java
            ).copy(id = it.id)
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isNullOrEmpty()
    }

    // endregion

    // region TextWireframe update mutations

    @Test
    fun `M identify the updated wireframes W resolveMutations {Text, position}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map { it.copy(x = forge.aLong(), y = forge.aLong()) }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                id = it.id(),
                x = it.x,
                y = it.y
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Text, dimensions}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map { it.copy(width = forge.aLong(), height = forge.aLong()) }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                id = it.id(),
                width = it.width,
                height = it.height
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Text, border}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    border = MobileSegment.ShapeBorder(
                        forge.aStringMatching("#[0-9A-F]{6}FF"),
                        forge.aPositiveLong(strict = true)
                    )
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                id = it.id(),
                border = it.border
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Text, shapeStyle}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    shapeStyle = MobileSegment.ShapeStyle(
                        backgroundColor = forge.aStringMatching("#[0-9A-F]{6}FF"),
                        opacity = forge.aFloat(min = 0f, max = 1f),
                        cornerRadius = forge.aPositiveLong()
                    )
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                id = it.id(),
                shapeStyle = it.shapeStyle
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Text, text}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map { it.copy(text = forge.aString()) }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                id = it.id(),
                text = it.text
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Text, textStyle}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    textStyle = MobileSegment.TextStyle(
                        family = forge.aString(),
                        size = forge.aPositiveLong(strict = true),
                        color = forge.aStringMatching("#[0-9A-F]{6}FF")
                    )
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                id = it.id(),
                textStyle = it.textStyle
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Text, textPosition}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    textPosition = MobileSegment.TextPosition(
                        padding = MobileSegment.Padding(
                            forge.aPositiveLong(),
                            forge.aPositiveLong(),
                            forge.aPositiveLong(),
                            forge.aPositiveLong()
                        ),
                        alignment = MobileSegment.Alignment(
                            horizontal = forge.aValueFrom(
                                MobileSegment.Horizontal::class.java
                            ),
                            vertical = forge.aValueFrom(
                                MobileSegment.Vertical::class.java
                            )
                        )
                    )
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                id = it.id(),
                textPosition = it.textPosition
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M identify the updated wireframes W resolveMutations {Text, clip}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    clip = forge.forgeDifferent(it.clip)
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                id = it.id(),
                clip = it.clip
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M provide an empty clip W resolveMutations {Text, new clip is null}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
                .copy(clip = forge.getForgery())
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map {
                it.copy(
                    clip = null
                )
            }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                id = it.id(),
                clip = MobileSegment.WireframeClip(0, 0, 0, 0)
            )
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(expectedUpdates)
    }

    @Test
    fun `M return null W resolveMutation {Text wireframes types are not matching }`(
        forge: Forge
    ) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ShapeWireframe::class.java)
        }

        val fakeCurrentSnapshot = fakePrevSnapshot.map {
            forge.getForgery(
                MobileSegment.Wireframe
                    .TextWireframe::class.java
            ).copy(id = it.id)
        }

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations).isNull()
    }

    // endregion

    // region no mutation

    @Test
    fun `M return null W resolveMutation { Text wireframes, no mutation }`(
        forge: Forge
    ) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.TextWireframe::class.java)
        }

        val fakeCurrentSnapshot = ArrayList(fakePrevSnapshot)

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations).isNull()
    }

    @Test
    fun `M return null W resolveMutation { Shape wireframes, no mutation }`(
        forge: Forge
    ) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ShapeWireframe::class.java)
        }

        val fakeCurrentSnapshot = ArrayList(fakePrevSnapshot)

        // When
        val mutations = testedMutationResolver.resolveMutations(
            fakePrevSnapshot,
            fakeCurrentSnapshot
        )

        // Then
        assertThat(mutations).isNull()
    }

    // endregion

    // region Internal

    private fun Forge.forgeDifferent(wireframeClip: MobileSegment.WireframeClip?):
        MobileSegment.WireframeClip {
        while (true) {
            val differentClip: MobileSegment.WireframeClip = getForgery()
            if (differentClip != wireframeClip) {
                return differentClip
            }
        }
    }

    private fun MobileSegment.Wireframe.id(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.id
            is MobileSegment.Wireframe.TextWireframe -> this.id
            is MobileSegment.Wireframe.ImageWireframe -> this.id
        }
    }

    // endregion
}
