/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ComponentIdManagerTest {

    private lateinit var testedManager: ComponentIdManagerImpl

    @Mock
    lateinit var mockContext: Context

    @StringForgery
    lateinit var fakePackageName: String

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.applicationContext) doReturn mockContext
        whenever(mockContext.packageName) doReturn fakePackageName
        testedManager = ComponentIdManagerImpl(fakePackageName)
    }

    // region getComponentId

    @Test
    fun `M return consistent id W getComponentId { same view }`() {
        // Given
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId1 = testedManager.getComponentId(mockView)
        val componentId2 = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId1).isEqualTo(componentId2)
    }

    @Test
    fun `M return different ids W getComponentId { different views }`() {
        // Given
        val mockView1 = mockSimpleView(viewId = 100)
        val mockView2 = mockSimpleView(viewId = 200)
        val mockRoot = mockViewGroupWithChildren(mockView1, mockView2)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId1 = testedManager.getComponentId(mockView1)
        val componentId2 = testedManager.getComponentId(mockView2)

        // Then
        assertThat(componentId1).isNotEqualTo(componentId2)
    }

    @Test
    fun `M return 32 hex chars W getComponentId`() {
        // Given
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).hasSize(32)
        assertThat(componentId).matches("[0-9a-f]{32}")
    }

    @Test
    fun `M compute path on demand W getComponentId { view not indexed }`() {
        // Given
        val mockChild = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockChild)
        mockActivityContext(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockChild)

        // Then
        assertThat(componentId).isNotNull()
    }

    @Test
    fun `M return valid id W getComponentId { root view }`() {
        // Given
        val mockRoot = mockViewGroupWithChildren()
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockRoot)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).matches("[0-9a-f]{32}")
    }

    @Test
    fun `M return same ids W onWindowRefreshed { called multiple times }`() {
        // Given
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockActivityContext(mockRoot)

        testedManager.onWindowRefreshed(mockRoot)
        val firstId = testedManager.getComponentId(mockView)

        // When
        testedManager.onWindowRefreshed(mockRoot)
        val secondId = testedManager.getComponentId(mockView)

        // Then
        assertThat(secondId).isEqualTo(firstId)
    }

    @Test
    fun `M return valid id W getComponentId { empty ViewGroup }`() {
        // Given
        val mockRoot = mockViewGroupWithChildren()
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockRoot)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).matches("[0-9a-f]{32}")
    }

    // endregion

    // region setCurrentScreen

    @Test
    fun `M return valid id W getComponentId { screen identifier set }`(forge: Forge) {
        // Given
        val screenIdentifier = forge.aString()
        testedManager.setCurrentScreen(screenIdentifier)

        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).matches("[0-9a-f]{32}")
    }

    @Test
    fun `M clear cache W setCurrentScreen { new identifier }`(forge: Forge) {
        // Given
        val firstScreen = forge.aString()
        testedManager.setCurrentScreen(firstScreen)

        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        testedManager.onWindowRefreshed(mockRoot)
        val firstComponentId = testedManager.getComponentId(mockView)

        // When
        val secondScreen = forge.aString()
        testedManager.setCurrentScreen(secondScreen)
        testedManager.onWindowRefreshed(mockRoot)
        val secondComponentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(secondComponentId).isNotEqualTo(firstComponentId)
    }

    @Test
    fun `M not clear cache W setCurrentScreen { same identifier }`(forge: Forge) {
        // Given
        val screenIdentifier = forge.aString()
        testedManager.setCurrentScreen(screenIdentifier)

        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        testedManager.onWindowRefreshed(mockRoot)
        val firstComponentId = testedManager.getComponentId(mockView)

        // When
        testedManager.setCurrentScreen(screenIdentifier)
        val secondComponentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(secondComponentId).isEqualTo(firstComponentId)
    }

    @Test
    fun `M fall back to activity namespace W setCurrentScreen { null after identifier }`(forge: Forge) {
        // Given
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockActivityContext(mockRoot)

        testedManager.onWindowRefreshed(mockRoot)
        val idWithActivity = testedManager.getComponentId(mockView)

        testedManager.setCurrentScreen(forge.aString())
        testedManager.onWindowRefreshed(mockRoot)
        val idWithScreen = testedManager.getComponentId(mockView)

        // When
        testedManager.setCurrentScreen(null)
        testedManager.onWindowRefreshed(mockRoot)
        val idAfterClear = testedManager.getComponentId(mockView)

        // Then
        assertThat(idWithScreen).isNotEqualTo(idWithActivity)
        assertThat(idAfterClear).isEqualTo(idWithActivity)
    }

    // endregion

    // region Screen Namespace Priority

    @Test
    fun `M return valid id W getComponentId { no screen identifier, has activity }`() {
        // Given
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).matches("[0-9a-f]{32}")
    }

    @Test
    fun `M return valid id W getComponentId { no activity, has root id }`() {
        // Given
        val rootResourceName = "com.example:id/root_container"
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockRootWithResourceId(mockRoot, rootResourceName)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).matches("[0-9a-f]{32}")
    }

    @Test
    fun `M return valid id W getComponentId { no activity, no root id }`() {
        // Given
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).matches("[0-9a-f]{32}")
    }

    @Test
    fun `M produce different id W getComponentId { screen identifier vs activity namespace }`(forge: Forge) {
        // Given
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)
        val idWithActivity = testedManager.getComponentId(mockView)

        // When
        testedManager.setCurrentScreen(forge.aString())
        val idWithScreenId = testedManager.getComponentId(mockView)

        // Then
        assertThat(idWithScreenId).isNotEqualTo(idWithActivity)
    }

    @Test
    fun `M return valid id W getComponentId { screen identifier contains slashes }`() {
        // Given
        val screenIdentifier = "home/settings/profile"
        testedManager.setCurrentScreen(screenIdentifier)

        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).matches("[0-9a-f]{32}")
        assertThat(testedManager.getComponentId(mockView)).isEqualTo(componentId)
    }

    @Test
    fun `M return valid id W getComponentId { screen identifier contains percent }`() {
        // Given
        val screenIdentifier = "discount%20offer"
        testedManager.setCurrentScreen(screenIdentifier)
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).matches("[0-9a-f]{32}")
        assertThat(testedManager.getComponentId(mockView)).isEqualTo(componentId)
    }

    // endregion

    // region Local Key Resolution

    @Test
    fun `M use resource id name W getComponentId { view has resource id }`() {
        // Given
        val resourceName = "com.example:id/my_button"
        val mockView = mockViewWithResourceId(resourceName)
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
    }

    @Test
    fun `M use class and index W getComponentId { view has no resource id }`() {
        // Given
        val mockView = mockSimpleView(viewId = View.NO_ID)
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
    }

    @Test
    fun `M differentiate by index W getComponentId { siblings without resource ids }`() {
        // Given
        val mockView1 = mockSimpleView(viewId = View.NO_ID)
        val mockView2 = mockSimpleView(viewId = View.NO_ID)
        val mockRoot = mockViewGroupWithChildren(mockView1, mockView2)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId1 = testedManager.getComponentId(mockView1)
        val componentId2 = testedManager.getComponentId(mockView2)

        // Then
        assertThat(componentId1).isNotEqualTo(componentId2)
    }

    // endregion

    // region Nested Hierarchy

    @Test
    fun `M handle nested hierarchy W getComponentId`() {
        // Given
        val deepChild = mockSimpleView()
        val middleGroup = mockViewGroupWithChildren(deepChild)
        val mockRoot = mockViewGroupWithChildren(middleGroup)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(deepChild)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).hasSize(32)
    }

    @Test
    fun `M produce different ids for same depth different parents W getComponentId`() {
        // Given
        val child1 = mockSimpleView()
        val child2 = mockSimpleView()
        val group1 = mockViewGroupWithChildren(child1)
        val group2 = mockViewGroupWithChildren(child2)
        val mockRoot = mockViewGroupWithChildren(group1, group2)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId1 = testedManager.getComponentId(child1)
        val componentId2 = testedManager.getComponentId(child2)

        // Then
        assertThat(componentId1).isNotEqualTo(componentId2)
    }

    // endregion

    // region Edge Cases

    @Test
    fun `M handle view with null context W getComponentId`() {
        // Given
        val mockView: View = mock {
            whenever(it.id) doReturn View.NO_ID
            whenever(it.resources) doReturn null
            whenever(it.context) doReturn null
            whenever(it.parent) doReturn null
        }
        val mockRoot = mockViewGroupWithChildren(mockView)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
    }

    @Test
    fun `M handle Resources NotFoundException W getComponentId`() {
        // Given
        val viewId = 12345
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)).thenThrow(Resources.NotFoundException())
        }
        val mockView: View = mock {
            whenever(it.id) doReturn viewId
            whenever(it.resources) doReturn mockResources
            whenever(it.context) doReturn mockContext
            whenever(it.parent) doReturn null
        }
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
    }

    @Test
    fun `M handle detached view W getComponentId { view not in hierarchy }`() {
        // Given
        val mockView: View = mock {
            whenever(it.id) doReturn View.NO_ID
            whenever(it.resources) doReturn null
            whenever(it.context) doReturn mockContext
            whenever(it.parent) doReturn null
        }

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNull()
    }

    @Test
    fun `M use cached ancestor path as base W getComponentId { ancestor cached but child not }`() {
        // Given
        val deepChild = mockSimpleView()
        val middleGroup = mockViewGroupWithChildren(deepChild)
        val mockRoot = mockViewGroupWithChildren(middleGroup)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)
        val rootId = testedManager.getComponentId(mockRoot)
        val middleId = testedManager.getComponentId(middleGroup)

        // When
        val deepChildId = testedManager.getComponentId(deepChild)

        // Then
        assertThat(deepChildId).isNotNull()
        assertThat(deepChildId).isNotEqualTo(rootId)
        assertThat(deepChildId).isNotEqualTo(middleId)
        assertThat(testedManager.getComponentId(deepChild)).isEqualTo(deepChildId)
    }

    @Test
    fun `M compute correct path W getComponentId { child added after initial indexing }`() {
        // Given
        val existingChild = mockSimpleView(viewId = 100)
        val mockRoot = mockViewGroupWithChildren(existingChild)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)
        val existingChildId = testedManager.getComponentId(existingChild)

        val newChild = mockSimpleView(viewId = 200)
        whenever(mockRoot.childCount) doReturn 2
        whenever(mockRoot.getChildAt(1)) doReturn newChild
        whenever(newChild.parent) doReturn mockRoot

        // When
        val newChildId = testedManager.getComponentId(newChild)

        // Then
        assertThat(newChildId).isNotNull()
        assertThat(newChildId).isNotEqualTo(existingChildId)
    }

    @Test
    fun `M find activity W getComponentId { context wrapped in ContextWrapper }`() {
        // Given
        val mockView = mockSimpleView()
        val mockRoot = mockViewGroupWithChildren(mockView)
        mockWrappedActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val componentId = testedManager.getComponentId(mockView)

        // Then
        assertThat(componentId).isNotNull()
        assertThat(componentId).matches("[0-9a-f]{32}")
    }

    @Test
    fun `M return different ids W getComponentId { different root views }`() {
        // Given
        val view1 = mockSimpleView()
        val root1 = mockViewGroupWithChildren(view1)
        mockActivityContext(root1)

        val view2 = mockSimpleView()
        val root2 = mockViewGroupWithChildren(view2)
        mockRootWithResourceId(root2, "com.example:id/second_root")

        testedManager.onWindowRefreshed(root1)
        testedManager.onWindowRefreshed(root2)

        // When
        val id1 = testedManager.getComponentId(view1)
        val id2 = testedManager.getComponentId(view2)

        // Then
        assertThat(id1).isNotNull()
        assertThat(id2).isNotNull()
        assertThat(id1).isNotEqualTo(id2)
    }

    @Test
    fun `M use cached resource name W getComponentId { same resource id queried twice }`() {
        // Given
        val resourceName = "com.example:id/shared_button"
        val viewId = resourceName.hashCode()
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)) doReturn resourceName
        }

        val view1: View = mock {
            whenever(it.id) doReturn viewId
            whenever(it.resources) doReturn mockResources
            whenever(it.context) doReturn mockContext
            whenever(it.parent) doReturn null
        }
        val view2: View = mock {
            whenever(it.id) doReturn viewId
            whenever(it.resources) doReturn mockResources
            whenever(it.context) doReturn mockContext
            whenever(it.parent) doReturn null
        }

        val mockRoot = mockViewGroupWithChildren(view1, view2)
        mockActivityContext(mockRoot)
        testedManager.onWindowRefreshed(mockRoot)

        // When
        val id1 = testedManager.getComponentId(view1)
        val id2 = testedManager.getComponentId(view2)

        // Then
        assertThat(id1).isNotNull()
        assertThat(id2).isNotNull()
    }

    // endregion

    // region Helper Methods

    private fun mockSimpleView(viewId: Int = View.NO_ID): View {
        return mock {
            whenever(it.id) doReturn viewId
            whenever(it.resources) doReturn null
            whenever(it.context) doReturn mockContext
            whenever(it.parent) doReturn null
        }
    }

    private fun mockViewWithResourceId(resourceName: String): View {
        val viewId = resourceName.hashCode()
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)) doReturn resourceName
        }
        return mock {
            whenever(it.id) doReturn viewId
            whenever(it.resources) doReturn mockResources
            whenever(it.context) doReturn mockContext
            whenever(it.parent) doReturn null
        }
    }

    private fun mockViewGroupWithChildren(vararg children: View): ViewGroup {
        val mockGroup: ViewGroup = mock {
            whenever(it.id) doReturn View.NO_ID
            whenever(it.resources) doReturn null
            whenever(it.context) doReturn mockContext
            whenever(it.parent) doReturn null
            whenever(it.childCount) doReturn children.size
            children.forEachIndexed { index, child ->
                whenever(it.getChildAt(index)) doReturn child
                whenever(child.parent) doReturn it
            }
        }
        return mockGroup
    }

    private fun mockActivityContext(view: View): Activity {
        val mockActivity: Activity = mock()
        whenever(view.context) doReturn mockActivity
        return mockActivity
    }

    private fun mockRootWithResourceId(root: ViewGroup, resourceName: String) {
        val viewId = resourceName.hashCode()
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)) doReturn resourceName
        }
        whenever(root.id) doReturn viewId
        whenever(root.resources) doReturn mockResources
    }

    private fun mockWrappedActivityContext(view: View): Activity {
        val mockActivity: Activity = mock()
        val mockWrapper: ContextWrapper = mock {
            whenever(it.baseContext) doReturn mockActivity
        }
        whenever(view.context) doReturn mockWrapper
        return mockActivity
    }

    // endregion
}
