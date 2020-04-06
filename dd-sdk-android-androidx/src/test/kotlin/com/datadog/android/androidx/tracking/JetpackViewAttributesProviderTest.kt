package com.datadog.android.androidx.tracking

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.rum.RumAttributes
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class JetpackViewAttributesProviderTest {

    @Mock
    lateinit var mockedRecyclerView: RecyclerView

    @Mock
    lateinit var mockedTarget: View

    lateinit var underTest: JetpackViewAttributesProvider

    // region Unit tests

    @BeforeEach
    fun `set up`() {
        underTest = JetpackViewAttributesProvider()
    }

    @Test
    fun `will add the adapter position event if the target is a RecyclerView nested child`(
        forge: Forge
    ) {
        // given
        val expectedAdapterPosition = forge.anInt(
            min = 0, max = 20
        )
        val mockedParent: ViewGroup = mock {
            whenever(it.parent).thenReturn(mockedRecyclerView)
            whenever(it.layoutParams).thenReturn(mock<RecyclerView.LayoutParams>())
        }
        whenever(mockedRecyclerView.getChildAdapterPosition(mockedParent)).thenReturn(
            expectedAdapterPosition
        )
        whenever(mockedTarget.parent).thenReturn(mockedParent)
        val attributes: MutableMap<String, Any?> = mutableMapOf()
        val parentId = forge.anInt()
        whenever(mockedRecyclerView.id).thenReturn(parentId)

        val resourceIdName = forge.anAlphabeticalString()
        val mockResources = mock<Resources>()
        val hasResourceId = forge.aBool()
        whenever(mockedRecyclerView.resources).thenReturn(mockResources)
        val expectedParentResourceId = if (hasResourceId) {
            whenever(mockResources.getResourceEntryName(mockedRecyclerView.id))
                .thenReturn(resourceIdName)

            resourceIdName
        } else {
            whenever(mockResources.getResourceEntryName(mockedRecyclerView.id))
                .thenThrow(Resources.NotFoundException(forge.aString()))
            "0x${parentId.toString(16)}"
        }

        // when
        underTest.extractAttributes(mockedTarget, attributes)

        // then
        assertThat(attributes).containsAllEntriesOf(
            mapOf(
                RumAttributes.TAG_TARGET_POSITION_IN_SCROLLABLE_CONTAINER to
                        expectedAdapterPosition,
                RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_CLASS_NAME to
                        mockedRecyclerView.javaClass.canonicalName,
                RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_RESOURCE_ID to
                        expectedParentResourceId
            )
        )
    }

    @Test
    fun `will add the adapter position if the target is a RecyclerView direct child`(
        forge: Forge
    ) {
        // given
        val expectedAdapterPosition = forge.anInt(
            min = 0, max = 20
        )
        whenever(mockedTarget.layoutParams).thenReturn(mock<RecyclerView.LayoutParams>())
        whenever(mockedRecyclerView.getChildAdapterPosition(mockedTarget)).thenReturn(
            expectedAdapterPosition
        )
        whenever(mockedTarget.parent).thenReturn(mockedRecyclerView)
        val attributes: MutableMap<String, Any?> = mutableMapOf()
        val parentId = forge.anInt()
        whenever(mockedRecyclerView.id).thenReturn(parentId)

        val resourceIdName = forge.anAlphabeticalString()
        val mockResources = mock<Resources>()
        val hasResourceId = forge.aBool()
        whenever(mockedRecyclerView.resources).thenReturn(mockResources)
        val expectedParentResourceId = if (hasResourceId) {
            whenever(mockResources.getResourceEntryName(mockedRecyclerView.id))
                .thenReturn(resourceIdName)

            resourceIdName
        } else {
            whenever(mockResources.getResourceEntryName(mockedRecyclerView.id))
                .thenThrow(Resources.NotFoundException(forge.aString()))
            "0x${parentId.toString(16)}"
        }

        // when
        underTest.extractAttributes(mockedTarget, attributes)

        // then
        assertThat(attributes).containsAllEntriesOf(
            mapOf(
                RumAttributes.TAG_TARGET_POSITION_IN_SCROLLABLE_CONTAINER to
                        expectedAdapterPosition,
                RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_CLASS_NAME to
                        mockedRecyclerView.javaClass.canonicalName,
                RumAttributes.TAG_TARGET_SCROLLABLE_CONTAINER_RESOURCE_ID to
                        expectedParentResourceId
            )
        )
    }

    @Test
    fun `will do nothing if the target is not following the RecyclerView child protocol`(
        forge: Forge
    ) {
        // given
        whenever(mockedTarget.layoutParams).thenReturn(mock<LinearLayout.LayoutParams>())
        val adapterPosition = forge.anInt(
            min = 0, max = 20
        )
        whenever(mockedRecyclerView.getChildAdapterPosition(mockedTarget)).thenReturn(
            adapterPosition
        )
        whenever(mockedTarget.parent).thenReturn(mockedRecyclerView)
        val attributes: MutableMap<String, Any?> = mutableMapOf()

        // when
        underTest.extractAttributes(mockedTarget, attributes)

        // then
        assertThat(attributes).isEmpty()
    }

    @Test
    fun `will do nothing if the direct child is not following the RecyclerView child protocol`(
        forge: Forge
    ) {
        // given
        val mockedParent: ViewGroup = mock {
            whenever(it.parent).thenReturn(mockedRecyclerView)
            whenever(it.layoutParams).thenReturn(mock<LinearLayout.LayoutParams>())
        }
        val adapterPosition = forge.anInt(
            min = 0, max = 20
        )
        whenever(mockedRecyclerView.getChildAdapterPosition(mockedParent)).thenReturn(
            adapterPosition
        )
        whenever(mockedTarget.parent).thenReturn(mockedParent)
        val attributes: MutableMap<String, Any?> = mutableMapOf()

        // when
        underTest.extractAttributes(mockedTarget, attributes)

        // then
        assertThat(attributes).isEmpty()
    }

    @Test
    fun `will do nothing if the target is not a RecyclerView descendant`() {
        // given
        val mockedParent: ViewParent = mock()
        whenever(mockedTarget.parent).thenReturn(mockedParent)
        val attributes: MutableMap<String, Any?> = mutableMapOf()

        // when
        underTest.extractAttributes(mockedTarget, attributes)

        // then
        assertThat(attributes).isEmpty()
    }

    // endregion
}
