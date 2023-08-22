/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tv

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import androidx.leanback.widget.Action
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.recyclerview.widget.RecyclerView
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class LeanbackViewAttributesProviderTest : ObjectTest<LeanbackViewAttributesProvider>() {

    lateinit var testedAttributesProvider: LeanbackViewAttributesProvider

    @Mock
    lateinit var mockRecyclerView: RecyclerView

    @Mock
    lateinit var mockTarget: View

    @LongForgery
    var fakeActionId: Long = 0

    @StringForgery
    lateinit var fakeActionLabel1: String

    @StringForgery
    lateinit var fakeActionLabel2: String

    lateinit var fakeAction: Action

    @Mock
    lateinit var mockViewHolder: ItemBridgeAdapter.ViewHolder

    lateinit var fakeAttributes: MutableMap<String, Any?>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeAttributes = forge.exhaustiveAttributes().toMutableMap()
        fakeAction = Action(fakeActionId, fakeActionLabel1, fakeActionLabel2)
        whenever(mockViewHolder.item).thenReturn(fakeAction)
        testedAttributesProvider =
            LeanbackViewAttributesProvider()
    }

    override fun createInstance(forge: Forge): LeanbackViewAttributesProvider {
        return LeanbackViewAttributesProvider()
    }

    override fun createEqualInstance(
        source: LeanbackViewAttributesProvider,
        forge: Forge
    ): LeanbackViewAttributesProvider {
        return LeanbackViewAttributesProvider()
    }

    override fun createUnequalInstance(
        source: LeanbackViewAttributesProvider,
        forge: Forge
    ): LeanbackViewAttributesProvider? = null

    @Test
    fun `M add action details W extractAttributes`() {
        // Given
        whenever(mockRecyclerView.findContainingViewHolder(mockTarget)).thenReturn(
            mockViewHolder
        )
        whenever(mockTarget.parent).thenReturn(mockRecyclerView)

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).containsAllEntriesOf(
            mapOf(
                LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID to fakeAction.id,
                LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1 to fakeAction.label1,
                LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2 to fakeAction.label2
            )
        )
    }

    @Test
    fun `M not add action label1 W extractAttributes {Action Label 1 is null}`() {
        // Given
        fakeAction.label1 = null
        whenever(mockRecyclerView.findContainingViewHolder(mockTarget)).thenReturn(
            mockViewHolder
        )
        whenever(mockTarget.parent).thenReturn(mockRecyclerView)

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).containsAllEntriesOf(
            mapOf(
                LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID to fakeAction.id,
                LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2 to fakeAction.label2
            )
        )
        assertThat(fakeAttributes).doesNotContainKey(
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1
        )
    }

    @Test
    fun `M not add action label2 W extractAttributes {Action Label 2 is null}`() {
        // Given
        fakeAction.label2 = null
        whenever(mockRecyclerView.findContainingViewHolder(mockTarget)).thenReturn(
            mockViewHolder
        )
        whenever(mockTarget.parent).thenReturn(mockRecyclerView)

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).containsAllEntriesOf(
            mapOf(
                LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID to fakeAction.id,
                LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1 to fakeAction.label1
            )
        )
        assertThat(fakeAttributes).doesNotContainKey(
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2
        )
    }

    @Test
    fun `M not add action labels W extractAttributes {both labels are null}`() {
        // Given
        fakeAction.label2 = null
        fakeAction.label1 = null
        whenever(mockRecyclerView.findContainingViewHolder(mockTarget)).thenReturn(
            mockViewHolder
        )
        whenever(mockTarget.parent).thenReturn(mockRecyclerView)

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).containsAllEntriesOf(
            mapOf(
                LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID to fakeAction.id
            )
        )
        assertThat(fakeAttributes).doesNotContainKeys(
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2
        )
    }

    @Test
    fun `M add action details W extractAttributes {target is a RecyclerView nested child}`() {
        // Given
        val mockParent: ViewGroup = mock {
            whenever(it.parent).thenReturn(mockRecyclerView)
        }
        whenever(mockRecyclerView.findContainingViewHolder(mockParent)).thenReturn(
            mockViewHolder
        )
        whenever(mockTarget.parent).thenReturn(mockParent)

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).containsAllEntriesOf(
            mapOf(
                LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID to fakeAction.id,
                LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1 to fakeAction.label1,
                LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2 to fakeAction.label2
            )
        )
    }

    @Test
    fun `M do nothing W extractAttributes {target is not a RecyclerView descendant}`() {
        // Given
        val mockParent: ViewParent = mock()
        whenever(mockTarget.parent).thenReturn(mockParent)

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).doesNotContainKeys(
            LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2
        )
    }

    @Test
    fun `M do nothing W extractAttributes {target parent is null}`() {
        // Given
        whenever(mockTarget.parent).thenReturn(null)

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).doesNotContainKeys(
            LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2
        )
    }

    @Test
    fun `M do nothing W extractAttributes {target viewHolder not ItemBridgeAdapterViewHolder}`() {
        // Given
        whenever(mockTarget.parent).thenReturn(mockRecyclerView)
        whenever(mockRecyclerView.findContainingViewHolder(mockTarget)).thenReturn(mock())

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).doesNotContainKeys(
            LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2
        )
    }

    @Test
    fun `M do nothing W extractAttributes {target viewHolder item not of type Action}`() {
        // Given
        whenever(mockTarget.parent).thenReturn(mockRecyclerView)
        whenever(mockRecyclerView.findContainingViewHolder(mockTarget)).thenReturn(mockViewHolder)
        whenever(mockViewHolder.item).thenReturn(Any())

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).doesNotContainKeys(
            LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2
        )
    }

    @Test
    fun `M do nothing W extractAttributes {target viewHolder is null}`() {
        // Given
        whenever(mockTarget.parent).thenReturn(mockRecyclerView)
        whenever(mockRecyclerView.findContainingViewHolder(mockTarget)).thenReturn(null)

        // When
        testedAttributesProvider.extractAttributes(mockTarget, fakeAttributes)

        // Then
        assertThat(fakeAttributes).containsAllEntriesOf(fakeAttributes)
        assertThat(fakeAttributes).doesNotContainKeys(
            LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1,
            LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2
        )
    }

    // region Internal

    private fun Forge.exhaustiveAttributes(): Map<String, Any?> {
        return listOf(
            aBool(),
            anInt(),
            aLong(),
            aFloat(),
            aDouble(),
            anAsciiString(),
            aList { anAlphabeticalString() },
            null
        ).associateBy { anAlphabeticalString() }.removeActionAttributes()
    }

    private fun Map<String, Any?>.removeActionAttributes(): Map<String, Any?> {
        return filter {
            it.key !in setOf(
                LeanbackViewAttributesProvider.ACTION_TARGET_ACTION_ID,
                LeanbackViewAttributesProvider.ACTION_TARGET_LABEL2,
                LeanbackViewAttributesProvider.ACTION_TARGET_LABEL1
            )
        }
    }

    // endregion
}
