/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.api.InternalLogger
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.util.ArrayList
import java.util.LinkedList
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MutationResolverTest {

    private lateinit var testedMutationResolver: MutationResolver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedMutationResolver = MutationResolver(mockInternalLogger)
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

    // region property update mutations

    @ParameterizedTest
    @MethodSource("positionMutationData")
    fun `M identify the updated wireframes W resolveMutations {position}`(
        mutationTestData: MutationTestData
    ) {
        // When
        val mutations = testedMutationResolver.resolveMutations(
            mutationTestData.prevSnapshot,
            mutationTestData.newSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(mutationTestData.expectedMutation)
    }

    @ParameterizedTest
    @MethodSource("dimensionMutationData")
    fun `M identify the updated wireframes W resolveMutations {dimension}`(
        mutationTestData: MutationTestData
    ) {
        // When
        val mutations = testedMutationResolver.resolveMutations(
            mutationTestData.prevSnapshot,
            mutationTestData.newSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(mutationTestData.expectedMutation)
    }

    @ParameterizedTest
    @MethodSource("clipMutationData")
    fun `M identify the updated wireframes W resolveMutations {clip}`(
        mutationTestData: MutationTestData
    ) {
        // When
        val mutations = testedMutationResolver.resolveMutations(
            mutationTestData.prevSnapshot,
            mutationTestData.newSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(mutationTestData.expectedMutation)
    }

    @ParameterizedTest
    @MethodSource("clipNullMutationData")
    fun `M identify the updated wireframes W resolveMutations {clip null}`(
        mutationTestData: MutationTestData
    ) {
        // When
        val mutations = testedMutationResolver.resolveMutations(
            mutationTestData.prevSnapshot,
            mutationTestData.newSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(mutationTestData.expectedMutation)
    }

    @ParameterizedTest
    @MethodSource("borderMutationData")
    fun `M identify the updated wireframes W resolveMutations {border}`(
        mutationTestData: MutationTestData
    ) {
        // When
        val mutations = testedMutationResolver.resolveMutations(
            mutationTestData.prevSnapshot,
            mutationTestData.newSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(mutationTestData.expectedMutation)
    }

    @ParameterizedTest
    @MethodSource("shapeStyleMutationData")
    fun `M identify the updated wireframes W resolveMutations {shapeStyle}`(
        mutationTestData: MutationTestData
    ) {
        // When
        val mutations = testedMutationResolver.resolveMutations(
            mutationTestData.prevSnapshot,
            mutationTestData.newSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isEqualTo(mutationTestData.expectedMutation)
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

    // region TextWireframe update mutations

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

    // endregion

    // region ImageWireframe update mutations

    @Test
    fun `M identify the updated wireframes W resolveMutations {Image, valid resourceId}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.ImageWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map { it.copy(resourceId = forge.aString()) }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.ImageWireframeUpdate(
                id = it.id(),
                resourceId = it.resourceId
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

    // endregion

    // region PlaceholderWireframe update mutations

    @Test
    fun `M identify the updated wireframes W resolveMutations {Placeholder, label}`(forge: Forge) {
        // Given
        val fakePrevSnapshot = forge.aList(size = forge.anInt(min = 2, max = 10)) {
            forge.getForgery(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
        }
        val fakeUpdateSize = forge.anInt(min = 1, max = fakePrevSnapshot.size)
        val fakeUpdatedWireframes = fakePrevSnapshot
            .take(fakeUpdateSize)
            .map { it.copy(label = forge.aString()) }
        val fakeCurrentSnapshot = fakeUpdatedWireframes + fakePrevSnapshot.drop(fakeUpdateSize)
        val expectedUpdates = fakeUpdatedWireframes.map {
            MobileSegment.WireframeUpdateMutation.PlaceholderWireframeUpdate(
                id = it.id(),
                label = it.label
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

    @ParameterizedTest
    @MethodSource("typeMutationData")
    fun `M do nothing W resolveMutations {wrong type}`(
        mutationTestData: MutationTestData
    ) {
        // When
        val mutations = testedMutationResolver.resolveMutations(
            mutationTestData.prevSnapshot,
            mutationTestData.newSnapshot
        )

        // Then
        assertThat(mutations?.adds).isNullOrEmpty()
        assertThat(mutations?.removes).isNullOrEmpty()
        assertThat(mutations?.updates).isNullOrEmpty()
        val captor = argumentCaptor<() -> String> {
            verify(mockInternalLogger, times(mutationTestData.prevSnapshot.size)).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.MAINTAINER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
        }
        mutationTestData.prevSnapshot.forEachIndexed { index, wireframe ->
            assertThat(captor.allValues[index].invoke())
                .isEqualTo(
                    MutationResolver.MISS_MATCHING_TYPES_IN_SNAPSHOTS_ERROR_MESSAGE_FORMAT
                        .format(
                            Locale.ENGLISH,
                            wireframe.javaClass.name,
                            mutationTestData.newSnapshot[index].javaClass.name
                        )
                )
        }
    }

    // endregion

    // region Internal

    private fun Forge.forgeDifferent(wireframeClip: MobileSegment.WireframeClip?): MobileSegment.WireframeClip {
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
            is MobileSegment.Wireframe.PlaceholderWireframe -> this.id
        }
    }

    // endregion

    data class MutationTestData(
        val prevSnapshot: List<MobileSegment.Wireframe>,
        val newSnapshot: List<MobileSegment.Wireframe>,
        val expectedMutation: List<MobileSegment.WireframeUpdateMutation>
    )

    companion object {
        val forge = Forge()

        private fun MobileSegment.Wireframe.id(): Long {
            return when (this) {
                is MobileSegment.Wireframe.ShapeWireframe -> this.id
                is MobileSegment.Wireframe.TextWireframe -> this.id
                is MobileSegment.Wireframe.ImageWireframe -> this.id
                is MobileSegment.Wireframe.PlaceholderWireframe -> this.id
            }
        }

        @JvmStatic
        fun positionMutationData(): List<MutationTestData> {
            ForgeConfigurator().configure(forge)

            val fakePrevShapeSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ShapeWireframe>()
            val fakeShapeUpdatedWireframes = forgeMutatedWireframes(fakePrevShapeSnapshot) {
                it.copy(x = forge.aLong(), y = forge.aLong())
            }
            val fakeCurrentShapeSnapshot = fakeShapeUpdatedWireframes +
                fakePrevShapeSnapshot.drop(fakeShapeUpdatedWireframes.size)
            val expectedShapeUpdates = fakeShapeUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                    id = it.id(),
                    x = it.x,
                    y = it.y
                )
            }

            val fakePrevTextSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.TextWireframe>()
            val fakeTextUpdatedWireframes = forgeMutatedWireframes(fakePrevTextSnapshot) {
                it.copy(x = forge.aLong(), y = forge.aLong())
            }
            val fakeCurrentTextSnapshot = fakeTextUpdatedWireframes +
                fakePrevTextSnapshot.drop(fakeTextUpdatedWireframes.size)
            val expectedTextUpdates = fakeTextUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                    id = it.id(),
                    x = it.x,
                    y = it.y
                )
            }

            val fakePrevImageSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ImageWireframe>()
            val fakeImageUpdatedWireframes = forgeMutatedWireframes(fakePrevImageSnapshot) {
                it.copy(x = forge.aLong(), y = forge.aLong())
            }
            val fakeCurrentImageSnapshot = fakeImageUpdatedWireframes +
                fakePrevImageSnapshot.drop(fakeImageUpdatedWireframes.size)
            val expectedImageUpdates = fakeImageUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ImageWireframeUpdate(
                    id = it.id(),
                    x = it.x,
                    y = it.y
                )
            }

            val fakePrevPlaceholderSnapshot =
                forgePrevSnapshot<MobileSegment.Wireframe.PlaceholderWireframe>()
            val fakePlaceholderUpdateWireframes =
                forgeMutatedWireframes(fakePrevPlaceholderSnapshot) {
                    it.copy(x = forge.aLong(), y = forge.aLong())
                }
            val fakeCurrentPlaceholderSnapshot = fakePlaceholderUpdateWireframes +
                fakePrevPlaceholderSnapshot.drop(fakePlaceholderUpdateWireframes.size)
            val expectedPlaceholderUpdates = fakePlaceholderUpdateWireframes.map {
                MobileSegment.WireframeUpdateMutation.PlaceholderWireframeUpdate(
                    id = it.id(),
                    x = it.x,
                    y = it.y
                )
            }

            return listOf(
                MutationTestData(
                    prevSnapshot = fakePrevShapeSnapshot,
                    newSnapshot = fakeCurrentShapeSnapshot,
                    expectedMutation = expectedShapeUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevTextSnapshot,
                    newSnapshot = fakeCurrentTextSnapshot,
                    expectedMutation = expectedTextUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevImageSnapshot,
                    newSnapshot = fakeCurrentImageSnapshot,
                    expectedMutation = expectedImageUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevPlaceholderSnapshot,
                    newSnapshot = fakeCurrentPlaceholderSnapshot,
                    expectedMutation = expectedPlaceholderUpdates
                )
            )
        }

        @JvmStatic
        fun dimensionMutationData(): List<MutationTestData> {
            ForgeConfigurator().configure(forge)

            val fakePrevShapeSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ShapeWireframe>()
            val fakeShapeUpdatedWireframes = forgeMutatedWireframes(fakePrevShapeSnapshot) {
                it.copy(width = forge.aLong(), height = forge.aLong())
            }
            val fakeCurrentShapeSnapshot = fakeShapeUpdatedWireframes +
                fakePrevShapeSnapshot.drop(fakeShapeUpdatedWireframes.size)
            val expectedShapeUpdates = fakeShapeUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                    id = it.id(),
                    width = it.width,
                    height = it.height
                )
            }

            val fakePrevTextSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.TextWireframe>()
            val fakeTextUpdatedWireframes = forgeMutatedWireframes(fakePrevTextSnapshot) {
                it.copy(width = forge.aLong(), height = forge.aLong())
            }
            val fakeCurrentTextSnapshot = fakeTextUpdatedWireframes +
                fakePrevTextSnapshot.drop(fakeTextUpdatedWireframes.size)
            val expectedTextUpdates = fakeTextUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                    id = it.id(),
                    width = it.width,
                    height = it.height
                )
            }

            val fakePrevImageSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ImageWireframe>()
            val fakeImageUpdatedWireframes = forgeMutatedWireframes(fakePrevImageSnapshot) {
                it.copy(width = forge.aLong(), height = forge.aLong())
            }
            val fakeCurrentImageSnapshot = fakeImageUpdatedWireframes +
                fakePrevImageSnapshot.drop(fakeImageUpdatedWireframes.size)
            val expectedImageUpdates = fakeImageUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ImageWireframeUpdate(
                    id = it.id(),
                    width = it.width,
                    height = it.height
                )
            }

            val fakePrevPlaceholderSnapshot =
                forgePrevSnapshot<MobileSegment.Wireframe.PlaceholderWireframe>()
            val fakePlaceholderUpdateWireframes =
                forgeMutatedWireframes(fakePrevPlaceholderSnapshot) {
                    it.copy(width = forge.aLong(), height = forge.aLong())
                }
            val fakeCurrentPlaceholderSnapshot = fakePlaceholderUpdateWireframes +
                fakePrevPlaceholderSnapshot.drop(fakePlaceholderUpdateWireframes.size)
            val expectedPlaceholderUpdates = fakePlaceholderUpdateWireframes.map {
                MobileSegment.WireframeUpdateMutation.PlaceholderWireframeUpdate(
                    id = it.id(),
                    width = it.width,
                    height = it.height
                )
            }

            return listOf(
                MutationTestData(
                    prevSnapshot = fakePrevShapeSnapshot,
                    newSnapshot = fakeCurrentShapeSnapshot,
                    expectedMutation = expectedShapeUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevTextSnapshot,
                    newSnapshot = fakeCurrentTextSnapshot,
                    expectedMutation = expectedTextUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevImageSnapshot,
                    newSnapshot = fakeCurrentImageSnapshot,
                    expectedMutation = expectedImageUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevPlaceholderSnapshot,
                    newSnapshot = fakeCurrentPlaceholderSnapshot,
                    expectedMutation = expectedPlaceholderUpdates
                )
            )
        }

        @JvmStatic
        fun clipMutationData(): List<MutationTestData> {
            ForgeConfigurator().configure(forge)

            val fakePrevShapeSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ShapeWireframe>()
            val fakeShapeUpdatedWireframes = forgeMutatedWireframes(fakePrevShapeSnapshot) {
                it.copy(clip = forge.forgeDifferent(it.clip))
            }
            val fakeCurrentShapeSnapshot = fakeShapeUpdatedWireframes +
                fakePrevShapeSnapshot.drop(fakeShapeUpdatedWireframes.size)
            val expectedShapeUpdates = fakeShapeUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                    id = it.id(),
                    clip = it.clip
                )
            }

            val fakePrevTextSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.TextWireframe>()
            val fakeTextUpdatedWireframes = forgeMutatedWireframes(fakePrevTextSnapshot) {
                it.copy(clip = forge.forgeDifferent(it.clip))
            }
            val fakeCurrentTextSnapshot = fakeTextUpdatedWireframes +
                fakePrevTextSnapshot.drop(fakeTextUpdatedWireframes.size)
            val expectedTextUpdates = fakeTextUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                    id = it.id(),
                    clip = it.clip
                )
            }

            val fakePrevImageSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ImageWireframe>()
            val fakeImageUpdatedWireframes = forgeMutatedWireframes(fakePrevImageSnapshot) {
                it.copy(clip = forge.forgeDifferent(it.clip))
            }
            val fakeCurrentImageSnapshot = fakeImageUpdatedWireframes +
                fakePrevImageSnapshot.drop(fakeImageUpdatedWireframes.size)
            val expectedImageUpdates = fakeImageUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ImageWireframeUpdate(
                    id = it.id(),
                    clip = it.clip
                )
            }

            val fakePrevPlaceholderSnapshot =
                forgePrevSnapshot<MobileSegment.Wireframe.PlaceholderWireframe>()
            val fakePlaceholderUpdateWireframes =
                forgeMutatedWireframes(fakePrevPlaceholderSnapshot) {
                    it.copy(clip = forge.forgeDifferent(it.clip))
                }
            val fakeCurrentPlaceholderSnapshot = fakePlaceholderUpdateWireframes +
                fakePrevPlaceholderSnapshot.drop(fakePlaceholderUpdateWireframes.size)
            val expectedPlaceholderUpdates = fakePlaceholderUpdateWireframes.map {
                MobileSegment.WireframeUpdateMutation.PlaceholderWireframeUpdate(
                    id = it.id(),
                    clip = it.clip
                )
            }

            return listOf(
                MutationTestData(
                    prevSnapshot = fakePrevShapeSnapshot,
                    newSnapshot = fakeCurrentShapeSnapshot,
                    expectedMutation = expectedShapeUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevTextSnapshot,
                    newSnapshot = fakeCurrentTextSnapshot,
                    expectedMutation = expectedTextUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevImageSnapshot,
                    newSnapshot = fakeCurrentImageSnapshot,
                    expectedMutation = expectedImageUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevPlaceholderSnapshot,
                    newSnapshot = fakeCurrentPlaceholderSnapshot,
                    expectedMutation = expectedPlaceholderUpdates
                )
            )
        }

        @JvmStatic
        fun clipNullMutationData(): List<MutationTestData> {
            ForgeConfigurator().configure(forge)

            val fakePrevShapeSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ShapeWireframe>()
                .map { it.copy(clip = forge.getForgery()) }
            val fakeShapeUpdatedWireframes = forgeMutatedWireframes(fakePrevShapeSnapshot) {
                it.copy(clip = null)
            }
            val fakeCurrentShapeSnapshot = fakeShapeUpdatedWireframes +
                fakePrevShapeSnapshot.drop(fakeShapeUpdatedWireframes.size)
            val nullClipWireframe = MobileSegment.WireframeClip(0, 0, 0, 0)
            val expectedShapeUpdates = fakeShapeUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                    id = it.id(),
                    clip = nullClipWireframe
                )
            }

            val fakePrevTextSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.TextWireframe>()
                .map { it.copy(clip = forge.getForgery()) }
            val fakeTextUpdatedWireframes = forgeMutatedWireframes(fakePrevTextSnapshot) {
                it.copy(clip = null)
            }
            val fakeCurrentTextSnapshot = fakeTextUpdatedWireframes +
                fakePrevTextSnapshot.drop(fakeTextUpdatedWireframes.size)
            val expectedTextUpdates = fakeTextUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                    id = it.id(),
                    clip = nullClipWireframe
                )
            }

            val fakePrevImageSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ImageWireframe>()
                .map { it.copy(clip = forge.getForgery()) }
            val fakeImageUpdatedWireframes = forgeMutatedWireframes(fakePrevImageSnapshot) {
                it.copy(clip = null)
            }
            val fakeCurrentImageSnapshot = fakeImageUpdatedWireframes +
                fakePrevImageSnapshot.drop(fakeImageUpdatedWireframes.size)
            val expectedImageUpdates = fakeImageUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ImageWireframeUpdate(
                    id = it.id(),
                    clip = nullClipWireframe
                )
            }

            val fakePrevPlaceholderSnapshot =
                forgePrevSnapshot<MobileSegment.Wireframe.PlaceholderWireframe>()
                    .map { it.copy(clip = forge.getForgery()) }
            val fakePlaceholderUpdateWireframes = forgeMutatedWireframes(fakePrevPlaceholderSnapshot) {
                it.copy(clip = null)
            }
            val fakeCurrentPlaceholderSnapshot = fakePlaceholderUpdateWireframes +
                fakePrevPlaceholderSnapshot.drop(fakePlaceholderUpdateWireframes.size)
            val expectedPlaceholderUpdates = fakePlaceholderUpdateWireframes.map {
                MobileSegment.WireframeUpdateMutation.PlaceholderWireframeUpdate(
                    id = it.id(),
                    clip = nullClipWireframe
                )
            }

            return listOf(
                MutationTestData(
                    prevSnapshot = fakePrevShapeSnapshot,
                    newSnapshot = fakeCurrentShapeSnapshot,
                    expectedMutation = expectedShapeUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevTextSnapshot,
                    newSnapshot = fakeCurrentTextSnapshot,
                    expectedMutation = expectedTextUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevImageSnapshot,
                    newSnapshot = fakeCurrentImageSnapshot,
                    expectedMutation = expectedImageUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevPlaceholderSnapshot,
                    newSnapshot = fakeCurrentPlaceholderSnapshot,
                    expectedMutation = expectedPlaceholderUpdates
                )
            )
        }

        @JvmStatic
        fun borderMutationData(): List<MutationTestData> {
            ForgeConfigurator().configure(forge)

            val fakePrevShapeSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ShapeWireframe>()
            val fakeShapeUpdatedWireframes = forgeMutatedWireframes(fakePrevShapeSnapshot) {
                it.copy(border = forge.getForgery())
            }
            val fakeCurrentShapeSnapshot = fakeShapeUpdatedWireframes +
                fakePrevShapeSnapshot.drop(fakeShapeUpdatedWireframes.size)
            val expectedShapeUpdates = fakeShapeUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                    id = it.id(),
                    border = it.border
                )
            }

            val fakePrevTextSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.TextWireframe>()
            val fakeTextUpdatedWireframes = forgeMutatedWireframes(fakePrevTextSnapshot) {
                it.copy(border = forge.getForgery())
            }
            val fakeCurrentTextSnapshot = fakeTextUpdatedWireframes +
                fakePrevTextSnapshot.drop(fakeTextUpdatedWireframes.size)
            val expectedTextUpdates = fakeTextUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                    id = it.id(),
                    border = it.border
                )
            }

            val fakePrevImageSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ImageWireframe>()
            val fakeImageUpdatedWireframes = forgeMutatedWireframes(fakePrevImageSnapshot) {
                it.copy(border = forge.getForgery())
            }
            val fakeCurrentImageSnapshot = fakeImageUpdatedWireframes +
                fakePrevImageSnapshot.drop(fakeImageUpdatedWireframes.size)
            val expectedImageUpdates = fakeImageUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ImageWireframeUpdate(
                    id = it.id(),
                    border = it.border
                )
            }

            return listOf(
                MutationTestData(
                    prevSnapshot = fakePrevShapeSnapshot,
                    newSnapshot = fakeCurrentShapeSnapshot,
                    expectedMutation = expectedShapeUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevTextSnapshot,
                    newSnapshot = fakeCurrentTextSnapshot,
                    expectedMutation = expectedTextUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevImageSnapshot,
                    newSnapshot = fakeCurrentImageSnapshot,
                    expectedMutation = expectedImageUpdates
                )
            )
        }

        @JvmStatic
        fun shapeStyleMutationData(): List<MutationTestData> {
            ForgeConfigurator().configure(forge)

            val fakePrevShapeSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ShapeWireframe>()
            val fakeShapeUpdatedWireframes = forgeMutatedWireframes(fakePrevShapeSnapshot) {
                it.copy(shapeStyle = forge.getForgery())
            }
            val fakeCurrentShapeSnapshot = fakeShapeUpdatedWireframes +
                fakePrevShapeSnapshot.drop(fakeShapeUpdatedWireframes.size)
            val expectedShapeUpdates = fakeShapeUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate(
                    id = it.id(),
                    shapeStyle = it.shapeStyle
                )
            }

            val fakePrevTextSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.TextWireframe>()
            val fakeTextUpdatedWireframes = forgeMutatedWireframes(fakePrevTextSnapshot) {
                it.copy(shapeStyle = forge.getForgery())
            }
            val fakeCurrentTextSnapshot = fakeTextUpdatedWireframes +
                fakePrevTextSnapshot.drop(fakeTextUpdatedWireframes.size)
            val expectedTextUpdates = fakeTextUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
                    id = it.id(),
                    shapeStyle = it.shapeStyle
                )
            }

            val fakePrevImageSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ImageWireframe>()
            val fakeImageUpdatedWireframes = forgeMutatedWireframes(fakePrevImageSnapshot) {
                it.copy(shapeStyle = forge.getForgery())
            }
            val fakeCurrentImageSnapshot = fakeImageUpdatedWireframes +
                fakePrevImageSnapshot.drop(fakeImageUpdatedWireframes.size)
            val expectedImageUpdates = fakeImageUpdatedWireframes.map {
                MobileSegment.WireframeUpdateMutation.ImageWireframeUpdate(
                    id = it.id(),
                    shapeStyle = it.shapeStyle
                )
            }

            return listOf(
                MutationTestData(
                    prevSnapshot = fakePrevShapeSnapshot,
                    newSnapshot = fakeCurrentShapeSnapshot,
                    expectedMutation = expectedShapeUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevTextSnapshot,
                    newSnapshot = fakeCurrentTextSnapshot,
                    expectedMutation = expectedTextUpdates
                ),
                MutationTestData(
                    prevSnapshot = fakePrevImageSnapshot,
                    newSnapshot = fakeCurrentImageSnapshot,
                    expectedMutation = expectedImageUpdates
                )
            )
        }

        @JvmStatic
        fun typeMutationData(): List<MutationTestData> {
            ForgeConfigurator().configure(forge)

            val fakePrevShapeSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ShapeWireframe>()
            val fakeCurrentShapeSnapshot = fakePrevShapeSnapshot.map {
                forge.getForgery<MobileSegment.Wireframe.ImageWireframe>().copy(id = it.id)
            }

            val fakePrevTextSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.TextWireframe>()
            val fakeCurrentTextSnapshot = fakePrevTextSnapshot.map {
                forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>().copy(id = it.id)
            }

            val fakePrevImageSnapshot = forgePrevSnapshot<MobileSegment.Wireframe.ImageWireframe>()
            val fakeCurrentImageSnapshot = fakePrevImageSnapshot.map {
                forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>().copy(id = it.id)
            }

            val fakePrevPlaceholderSnapshot =
                forgePrevSnapshot<MobileSegment.Wireframe.PlaceholderWireframe>()
            val fakeCurrentPlaceholderSnapshot = fakePrevPlaceholderSnapshot.map {
                forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>().copy(id = it.id)
            }

            return listOf(
                MutationTestData(
                    prevSnapshot = fakePrevShapeSnapshot,
                    newSnapshot = fakeCurrentShapeSnapshot,
                    expectedMutation = emptyList()
                ),
                MutationTestData(
                    prevSnapshot = fakePrevTextSnapshot,
                    newSnapshot = fakeCurrentTextSnapshot,
                    expectedMutation = emptyList()
                ),
                MutationTestData(
                    prevSnapshot = fakePrevImageSnapshot,
                    newSnapshot = fakeCurrentImageSnapshot,
                    expectedMutation = emptyList()
                ),
                MutationTestData(
                    prevSnapshot = fakePrevPlaceholderSnapshot,
                    newSnapshot = fakeCurrentPlaceholderSnapshot,
                    expectedMutation = emptyList()
                )
            )
        }

        private fun Forge.forgeDifferent(wireframeClip: MobileSegment.WireframeClip?): MobileSegment.WireframeClip {
            while (true) {
                val differentClip: MobileSegment.WireframeClip = getForgery()
                if (differentClip != wireframeClip) {
                    return differentClip
                }
            }
        }

        private inline fun <reified T : MobileSegment.Wireframe> forgePrevSnapshot() =
            forge.aList(size = forge.anInt(min = 2, max = 10)) {
                forge.getForgery(T::class.java)
            }

        private inline fun <reified T : MobileSegment.Wireframe> forgeMutatedWireframes(
            prevSnapshot: List<T>,
            mutation: (T) -> T
        ): List<T> {
            val fakeUpdateSize = forge.anInt(min = 1, max = prevSnapshot.size)
            return prevSnapshot
                .take(fakeUpdateSize)
                .map { mutation(it) }
        }
    }
}
