/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.core.internal.utils.toHexString
import com.datadog.android.rum.RumAttributes
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
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
internal class JetpackViewAttributesProviderTest : ObjectTest<JetpackViewAttributesProvider>() {

    lateinit var testedAttributesProvider: JetpackViewAttributesProvider

    @Mock
    lateinit var mockRecyclerView: RecyclerView

    @Mock
    lateinit var mockTarget: View

    @BeforeEach
    fun `set up`() {
        testedAttributesProvider =
            JetpackViewAttributesProvider()
    }

    override fun createInstance(forge: Forge): JetpackViewAttributesProvider {
        return JetpackViewAttributesProvider()
    }

    override fun createEqualInstance(
        source: JetpackViewAttributesProvider,
        forge: Forge
    ): JetpackViewAttributesProvider {
        return JetpackViewAttributesProvider()
    }

    override fun createUnequalInstance(
        source: JetpackViewAttributesProvider,
        forge: Forge
    ): JetpackViewAttributesProvider? = null

    @RepeatedTest(4)
    fun `will add the adapter position event if the target is a RecyclerView nested child`(
        forge: Forge
    ) {
        // Given
        val expectedAdapterPosition = forge.anInt(
            min = 0,
            max = 20
        )
        val mockParent: ViewGroup = mock {
            whenever(it.parent).thenReturn(mockRecyclerView)
            whenever(it.layoutParams).thenReturn(mock<RecyclerView.LayoutParams>())
        }
        whenever(mockRecyclerView.getChildAdapterPosition(mockParent)).thenReturn(
            expectedAdapterPosition
        )
        whenever(mockTarget.parent).thenReturn(mockParent)
        val attributes: MutableMap<String, Any?> = mutableMapOf()
        val parentId = forge.anInt()
        whenever(mockRecyclerView.id).thenReturn(parentId)

        val resourceIdName = forge.anAlphabeticalString()
        val mockResources = mock<Resources>()
        val hasResourceId = forge.aBool()
        whenever(mockRecyclerView.resources).thenReturn(mockResources)
        val expectedParentResourceId = if (hasResourceId) {
            whenever(mockResources.getResourceEntryName(mockRecyclerView.id))
                .thenReturn(resourceIdName)

            resourceIdName
        } else {
            whenever(mockResources.getResourceEntryName(mockRecyclerView.id))
                .thenThrow(Resources.NotFoundException(forge.aString()))
            "0x${parentId.toHexString()}"
        }

        // When
        testedAttributesProvider.extractAttributes(mockTarget, attributes)

        // Then
        assertThat(attributes).containsAllEntriesOf(
            mapOf(
                RumAttributes.ACTION_TARGET_PARENT_INDEX to expectedAdapterPosition,
                RumAttributes.ACTION_TARGET_PARENT_CLASSNAME to
                    mockRecyclerView.javaClass.canonicalName,
                RumAttributes.ACTION_TARGET_PARENT_RESOURCE_ID to expectedParentResourceId
            )
        )
    }

    @RepeatedTest(4)
    fun `will add the adapter position if the target is a RecyclerView direct child`(
        forge: Forge
    ) {
        // Given
        val expectedAdapterPosition = forge.anInt(
            min = 0,
            max = 20
        )
        whenever(mockTarget.layoutParams).thenReturn(mock<RecyclerView.LayoutParams>())
        whenever(mockRecyclerView.getChildAdapterPosition(mockTarget)).thenReturn(
            expectedAdapterPosition
        )
        whenever(mockTarget.parent).thenReturn(mockRecyclerView)
        val attributes: MutableMap<String, Any?> = mutableMapOf()
        val parentId = forge.anInt()
        whenever(mockRecyclerView.id).thenReturn(parentId)

        val resourceIdName = forge.anAlphabeticalString()
        val mockResources = mock<Resources>()
        val hasResourceId = forge.aBool()
        whenever(mockRecyclerView.resources).thenReturn(mockResources)
        val expectedParentResourceId = if (hasResourceId) {
            whenever(mockResources.getResourceEntryName(mockRecyclerView.id))
                .thenReturn(resourceIdName)

            resourceIdName
        } else {
            whenever(mockResources.getResourceEntryName(mockRecyclerView.id))
                .thenThrow(Resources.NotFoundException(forge.aString()))
            "0x${parentId.toHexString()}"
        }

        // When
        testedAttributesProvider.extractAttributes(mockTarget, attributes)

        // Then
        assertThat(attributes).containsAllEntriesOf(
            mapOf(
                RumAttributes.ACTION_TARGET_PARENT_INDEX to expectedAdapterPosition,
                RumAttributes.ACTION_TARGET_PARENT_CLASSNAME to
                    mockRecyclerView.javaClass.canonicalName,
                RumAttributes.ACTION_TARGET_PARENT_RESOURCE_ID to expectedParentResourceId
            )
        )
    }

    @Test
    fun `will do nothing if the target is not following the RecyclerView child protocol`(
        forge: Forge
    ) {
        // Given
        whenever(mockTarget.layoutParams).thenReturn(mock<LinearLayout.LayoutParams>())
        val adapterPosition = forge.anInt(
            min = 0,
            max = 20
        )
        whenever(mockRecyclerView.getChildAdapterPosition(mockTarget)).thenReturn(
            adapterPosition
        )
        whenever(mockTarget.parent).thenReturn(mockRecyclerView)
        val attributes: MutableMap<String, Any?> = mutableMapOf()

        // When
        testedAttributesProvider.extractAttributes(mockTarget, attributes)

        // Then
        assertThat(attributes).isEmpty()
    }

    @Test
    fun `will do nothing if the direct child is not following the RecyclerView child protocol`(
        forge: Forge
    ) {
        // Given
        val mockParent: ViewGroup = mock {
            whenever(it.parent).thenReturn(mockRecyclerView)
            whenever(it.layoutParams).thenReturn(mock<LinearLayout.LayoutParams>())
        }
        val adapterPosition = forge.anInt(
            min = 0,
            max = 20
        )
        whenever(mockRecyclerView.getChildAdapterPosition(mockParent)).thenReturn(
            adapterPosition
        )
        whenever(mockTarget.parent).thenReturn(mockParent)
        val attributes: MutableMap<String, Any?> = mutableMapOf()

        // When
        testedAttributesProvider.extractAttributes(mockTarget, attributes)

        // Then
        assertThat(attributes).isEmpty()
    }

    @Test
    fun `will do nothing if the target is not a RecyclerView descendant`() {
        // Given
        val mockParent: ViewParent = mock()
        whenever(mockTarget.parent).thenReturn(mockParent)
        val attributes: MutableMap<String, Any?> = mutableMapOf()

        // When
        testedAttributesProvider.extractAttributes(mockTarget, attributes)

        // Then
        assertThat(attributes).isEmpty()
    }
}
