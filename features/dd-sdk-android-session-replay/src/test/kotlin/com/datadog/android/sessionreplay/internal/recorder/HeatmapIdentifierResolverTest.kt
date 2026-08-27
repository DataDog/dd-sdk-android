/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.HeatmapIdentifierResolver.Companion.LOCAL_KEY_CLASS_PREFIX
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class HeatmapIdentifierResolverTest {

    private lateinit var testedResolver: HeatmapIdentifierResolver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRegistry: HeatmapIdentifierRegistry

    @StringForgery
    lateinit var fakeAppPackageName: String

    @BeforeEach
    fun `set up`() {
        testedResolver = HeatmapIdentifierResolver(
            appPackageName = fakeAppPackageName,
            registry = mockRegistry,
            internalLogger = mockInternalLogger
        )
    }

    // region pathComponentFor

    @Test
    fun `M use class fallback path component W pathComponentFor() { no resource id }`() {
        val mockView: View = mock {
            whenever(it.id).thenReturn(View.NO_ID)
        }
        val fakeTypeIndex = 7

        val component = testedResolver.pathComponentFor(mockView, fakeTypeIndex)

        assertThat(component).startsWith(LOCAL_KEY_CLASS_PREFIX)
        assertThat(component).contains(mockView.javaClass.name)
        assertThat(component).endsWith("#$fakeTypeIndex")
    }

    @Test
    fun `M use fully-qualified resource name with typeIndex W pathComponentFor() { id resolves to name }`(
        forge: Forge
    ) {
        // Uses getResourceName (NOT getResourceEntryName) — the fully-qualified form
        // pkg:type/entry is required to disambiguate resources across packages.
        val fakeFullyQualifiedName = "com.example.app:id/${forge.anAlphabeticalString()}"
        val fakeTypeIndex = forge.anInt(min = 0, max = 20)
        val viewId = forge.anInt(min = 1, max = Int.MAX_VALUE)
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)).thenReturn(fakeFullyQualifiedName)
        }
        val mockView: View = mock {
            whenever(it.id).thenReturn(viewId)
            whenever(it.resources).thenReturn(mockResources)
        }

        val component = testedResolver.pathComponentFor(mockView, fakeTypeIndex)

        assertThat(component).isEqualTo("$fakeFullyQualifiedName#$fakeTypeIndex")
    }

    @Test
    fun `M fall back to class W pathComponentFor() { Resources_NotFoundException }`(
        forge: Forge
    ) {
        val viewId = forge.anInt(min = 1, max = Int.MAX_VALUE)
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)).thenThrow(Resources.NotFoundException())
        }
        val mockView: View = mock {
            whenever(it.id).thenReturn(viewId)
            whenever(it.resources).thenReturn(mockResources)
        }

        val component = testedResolver.pathComponentFor(mockView, 3)

        assertThat(component).startsWith(LOCAL_KEY_CLASS_PREFIX)
        assertThat(component).endsWith("#3")
    }

    @Test
    fun `M fall back to class W pathComponentFor() { view resources is null }`() {
        // Detached views return null from view.resources. The null-safe ?. must route to the
        // class-based fallback rather than crashing or returning an empty component.
        val mockView: View = mock {
            whenever(it.id).thenReturn(1)
            whenever(it.resources).thenReturn(null)
        }

        val component = testedResolver.pathComponentFor(mockView, 0)

        assertThat(component).startsWith(LOCAL_KEY_CLASS_PREFIX)
        assertThat(component).endsWith("#0")
    }

    @Test
    fun `M call getResourceName only once W pathComponentFor() { same viewId called multiple times }`(
        forge: Forge
    ) {
        val fakeFullyQualifiedName = "com.example.app:id/${forge.anAlphabeticalString()}"
        val viewId = forge.anInt(min = 1, max = Int.MAX_VALUE)
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)).thenReturn(fakeFullyQualifiedName)
        }
        val mockView: View = mock {
            whenever(it.id).thenReturn(viewId)
            whenever(it.resources).thenReturn(mockResources)
        }

        repeat(5) { typeIndex -> testedResolver.pathComponentFor(mockView, typeIndex) }

        verify(mockResources, times(1)).getResourceName(viewId)
    }

    // endregion

    // region same-resource-ID sibling disambiguation

    @Test
    fun `M produce distinct path components W pathComponentFor() { same resource id, different typeIndex }`(
        forge: Forge
    ) {
        // Without the typeIndex suffix, same-resource siblings get identical paths and
        // therefore identical permanentIds — taps on one row would attribute to the wrong one.
        val sharedResourceName = "com.example.app:id/${forge.anAlphabeticalString()}"
        val viewId = forge.anInt(min = 1, max = Int.MAX_VALUE)
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)).thenReturn(sharedResourceName)
        }
        val mockView0: View = mock {
            whenever(it.id).thenReturn(viewId)
            whenever(it.resources).thenReturn(mockResources)
        }
        val mockView1: View = mock {
            whenever(it.id).thenReturn(viewId)
            whenever(it.resources).thenReturn(mockResources)
        }

        val component0 = testedResolver.pathComponentFor(mockView0, 0)
        val component1 = testedResolver.pathComponentFor(mockView1, 1)

        assertThat(component0).isNotEqualTo(component1)
        assertThat(component0).isEqualTo("$sharedResourceName#0")
        assertThat(component1).isEqualTo("$sharedResourceName#1")
    }

    // endregion

    // region computeChildTypeIndices

    @Test
    fun `M number siblings of the same type W computeChildTypeIndices() { mixed types }`() {
        // Mockito (ByteBuddy) reuses the same proxy subclass for all mocks of the same type,
        // so View mocks are class-equal and the ViewGroup mock is class-distinct. Verify this
        // assumption explicitly so the test fails fast rather than silently producing wrong
        // indices if Mockito ever changes this behaviour.
        val mockTextChild1: View = mock()
        val mockTextChild2: View = mock()
        val mockGroupChild: ViewGroup = mock()
        val mockTextChild3: View = mock()
        assertThat(mockTextChild1.javaClass).isSameAs(mockTextChild2.javaClass)
        assertThat(mockTextChild1.javaClass).isSameAs(mockTextChild3.javaClass)
        assertThat(mockGroupChild.javaClass).isNotSameAs(mockTextChild1.javaClass)
        val parent: ViewGroup = mock {
            whenever(it.childCount).thenReturn(4)
            whenever(it.getChildAt(0)).thenReturn(mockTextChild1)
            whenever(it.getChildAt(1)).thenReturn(mockTextChild2)
            whenever(it.getChildAt(2)).thenReturn(mockGroupChild)
            whenever(it.getChildAt(3)).thenReturn(mockTextChild3)
        }

        val indices = testedResolver.computeChildTypeIndices(parent)

        // View: 0, 1, 2 (counter persists across the intervening ViewGroup); ViewGroup: 0.
        assertThat(indices).hasSize(4)
        assertThat(indices[0]).isEqualTo(0)
        assertThat(indices[1]).isEqualTo(1)
        assertThat(indices[2]).isEqualTo(0)
        assertThat(indices[3]).isEqualTo(2)
    }

    @Test
    fun `M not collide on siblings after null child W computeChildTypeIndices() { null child mid-iteration }`() {
        // The null slot stays at default index 0 in the result array; the next same-type
        // sibling must still receive index 1, not 0 again.
        val mockChild0: View = mock()
        val mockChild2: View = mock() // same type as mockChild0
        val parent: ViewGroup = mock {
            whenever(it.childCount).thenReturn(3)
            whenever(it.getChildAt(0)).thenReturn(mockChild0)
            whenever(it.getChildAt(1)).thenReturn(null) // null slot
            whenever(it.getChildAt(2)).thenReturn(mockChild2)
        }

        val indices = testedResolver.computeChildTypeIndices(parent)

        // child 0 → index 0; child 1 → null (slot stays 0); child 2 → index 1 (second View sibling).
        assertThat(indices).hasSize(3)
        assertThat(indices[0]).isEqualTo(0)
        assertThat(indices[1]).isEqualTo(0) // slot left at default; not a real child
        assertThat(indices[2]).isEqualTo(1)
    }

    // endregion

    // region concurrency

    @Test
    fun `M not throw W concurrent traversals from multiple windows`(forge: Forge) {
        // Given
        // Each recorded window runs its own traversal on the Looper thread its view hierarchy is
        // attached to, so two windows' traversals — including the resolveIdentity/publish
        // read-then-write sequence over the shared lastPublished* maps — can interleave on
        // different threads.
        //
        // lastPublishedEntries is only ever touched via get()/clear()/putAll(), never an
        // external iterator, so an unsynchronized race here does not reliably throw
        // ConcurrentModificationException the way an iterated HashMap does. Verified: this
        // exact test (300 entries/pass, 20 reps/thread) still passes 5/5 with synchronization
        // stripped out -- it is kept as a smoke check on the happy path, not as proof this race
        // is caught. The `synchronized` blocks in the production code are the actual defense;
        // don't rely on this test to validate them. Scale is capped below ~15k total
        // mock-intercepted calls because this environment's Mockito setup gets measurably,
        // sometimes catastrophically (OOM/livelock-like CPU spin), slower beyond that.
        val repetitions = 20
        val entriesPerPass = 300
        val errors = CopyOnWriteArrayList<Throwable>()
        val baseViewId = forge.anInt(min = 1, max = 100_000)
        val views = (0 until entriesPerPass).map { offset ->
            val viewId = baseViewId + offset
            val resourceName = "com.example.app:id/${forge.anAlphabeticalString()}"
            val mockResources: Resources = mock {
                whenever(it.getResourceName(viewId)).thenReturn(resourceName)
            }
            val mockView: View = mock {
                whenever(it.id).thenReturn(viewId)
                whenever(it.resources).thenReturn(mockResources)
                whenever(it.isClickable).thenReturn(true)
                whenever(it.visibility).thenReturn(View.VISIBLE)
            }
            mockView
        }
        val screenA = "screen-${forge.anAlphabeticalString()}"
        val screenB = "screen-${forge.anAlphabeticalString()}"

        fun runTraversal(screenName: String) {
            val context = testedResolver.beginTraversal(screenName)
            views.forEachIndexed { index, view -> context.resolveIdentity(view, emptyList(), index) }
            context.publish()
        }

        // When
        val threads = listOf(
            Thread { repeat(repetitions) { try { runTraversal(screenA) } catch (e: Throwable) { errors.add(e) } } },
            Thread { repeat(repetitions) { try { runTraversal(screenB) } catch (e: Throwable) { errors.add(e) } } }
        ).shuffled(Random(forge.seed))
        threads.forEach { it.start() }
        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(30)) }
        threads.filter { it.isAlive }.forEach { it.interrupt() }
        assertThat(threads.none { it.isAlive })
            .describedAs("a thread failed to complete within the timeout -- treat as a failure, not a hang")
            .isTrue()

        // Then
        assertThat(errors).isEmpty()
    }

    // endregion
}
