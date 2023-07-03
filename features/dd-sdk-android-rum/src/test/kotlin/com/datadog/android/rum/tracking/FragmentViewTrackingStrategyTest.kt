/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.tracking.OreoFragmentLifecycleCallbacks
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.resolveViewUrl
import com.datadog.tools.unit.ObjectTest
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
@Suppress("DEPRECATION")
internal class FragmentViewTrackingStrategyTest : ObjectTest<FragmentViewTrackingStrategy>() {

    lateinit var testedStrategy: FragmentViewTrackingStrategy

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockAndroidxActivity: FragmentActivity

    @Mock
    lateinit var mockAndroidxFragmentManager: FragmentManager

    @Mock
    lateinit var mockDefaultFragmentManager: android.app.FragmentManager

    @Mock
    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockBadContext: Context

    // region Strategy tests

    @BeforeEach
    fun `set up`() {
        whenever(mockAndroidxActivity.supportFragmentManager)
            .thenReturn(mockAndroidxFragmentManager)
        whenever(mockActivity.fragmentManager)
            .thenReturn(mockDefaultFragmentManager)

        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockRumFeatureScope.unwrap<RumFeature>()) doReturn mock()
        whenever(
            rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedStrategy = FragmentViewTrackingStrategy(true)
    }

    @Test
    fun `when register it will register as lifecycle callback`() {
        // When
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        // verify
        verify(mockAppContext).registerActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when unregister it will remove itself  as lifecycle callback`() {
        // When
        testedStrategy.unregister(mockAppContext)

        // verify
        verify(mockAppContext).unregisterActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when register called with non application context will do nothing`() {
        // When
        testedStrategy.register(rumMonitor.mockSdkCore, mockBadContext)

        // verify
        verifyNoInteractions(mockBadContext)
    }

    @Test
    fun `when unregister called with non application context will do nothing`() {
        // When
        testedStrategy.unregister(mockBadContext)

        // verify
        verifyNoInteractions(mockBadContext)
    }

    @Test
    fun `will start and stop a RumViewEvent when fragment resumes and pauses in a FragmentActivity`(
        forge: Forge
    ) {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val mockFragment: Fragment = mockFragmentWithArguments(forge)
        val expectedAttrs = mockFragment.arguments!!.toRumAttributes()
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()

        // When
        testedStrategy.onActivityStarted(mockAndroidxActivity)

        // Then
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyNoInteractions(mockDefaultFragmentManager)

        // When
        argumentCaptor.firstValue.onFragmentResumed(mockAndroidxFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockAndroidxFragmentManager, mockFragment)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startView(
                eq(mockFragment),
                eq(mockFragment.resolveViewUrl()),
                eq(expectedAttrs)
            )
            verify(rumMonitor.mockInstance).stopView(
                mockFragment
            )
        }
    }

    @Test
    fun `it will do nothing if fragment is not accepted`(
        forge: Forge
    ) {
        // Given
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()
        val mockFragment: Fragment = mockFragmentWithArguments(forge)
        testedStrategy = FragmentViewTrackingStrategy(
            trackArguments = true,
            supportFragmentComponentPredicate = object : ComponentPredicate<Fragment> {
                override fun accept(component: Fragment): Boolean {
                    return false
                }

                override fun getViewName(component: Fragment): String? {
                    return null
                }
            }
        )
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        // When
        testedStrategy.onActivityStarted(mockAndroidxActivity)

        // Then
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyNoInteractions(mockDefaultFragmentManager)

        // When
        argumentCaptor.firstValue.onFragmentResumed(mockAndroidxFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockAndroidxFragmentManager, mockFragment)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `will not attach fragment arguments as attributes if required so in a FragmentActivity`(
        forge: Forge
    ) {
        // Given
        testedStrategy = FragmentViewTrackingStrategy(false)
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val mockFragment: Fragment = mockFragmentWithArguments(forge)
        val expectedAttrs = emptyMap<String, Any?>()
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()

        // When
        testedStrategy.onActivityStarted(mockAndroidxActivity)

        // Then
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyNoInteractions(mockDefaultFragmentManager)

        // When
        argumentCaptor.firstValue.onFragmentResumed(mockAndroidxFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockAndroidxFragmentManager, mockFragment)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startView(
                eq(mockFragment),
                eq(mockFragment.resolveViewUrl()),
                eq(expectedAttrs)
            )
            verify(rumMonitor.mockInstance).stopView(
                mockFragment
            )
        }
    }

    @Test
    fun `when FragmentActivity started it will reuse same callback`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        // When
        testedStrategy.onActivityStarted(mockAndroidxActivity)
        testedStrategy.onActivityStarted(mockAndroidxActivity)

        // Then
        argumentCaptor<FragmentManager.FragmentLifecycleCallbacks> {
            verify(
                mockAndroidxFragmentManager,
                times(2)
            ).registerFragmentLifecycleCallbacks(capture(), eq(true))
            assertThat(firstValue).isEqualTo(secondValue)
        }
    }

    @Test
    fun `when FragmentActivity stopped will unregister the right callback`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        testedStrategy.onActivityStarted(mockAndroidxActivity)
        val argumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()
        verify(mockAndroidxFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )

        // When
        testedStrategy.onActivityStopped(mockAndroidxActivity)

        // Then
        verify(mockAndroidxFragmentManager)
            .unregisterFragmentLifecycleCallbacks(argumentCaptor.firstValue)
        verifyNoInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity started will register the right callback`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        // When
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(isA<OreoFragmentLifecycleCallbacks>(), eq(true))
        verifyNoInteractions(mockAndroidxFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity started will register the same callback`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        // When
        testedStrategy.onActivityStarted(mockActivity)
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks> {
            verify(
                mockDefaultFragmentManager,
                times(2)
            ).registerFragmentLifecycleCallbacks(capture(), eq(true))
            assertThat(firstValue).isEqualTo(secondValue)
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `will start and stop a RumViewEvent when fragment resumes and pauses in a base activity`(
        forge: Forge
    ) {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val mockFragment: android.app.Fragment = mockDeprecatedFragmentWithArguments(forge)
        val expectedAttrs = mockFragment.arguments.toRumAttributes()
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()

        // When
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyNoInteractions(mockAndroidxFragmentManager)

        // When
        argumentCaptor.firstValue.onFragmentResumed(mockDefaultFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockDefaultFragmentManager, mockFragment)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startView(
                eq(mockFragment),
                eq(mockFragment.resolveViewUrl()),
                eq(expectedAttrs)
            )
            verify(rumMonitor.mockInstance).stopView(
                mockFragment
            )
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `it will do nothing when fragment is not accepted`(
        forge: Forge
    ) {
        // Given
        val mockFragment: android.app.Fragment = mockDeprecatedFragmentWithArguments(forge)
        testedStrategy = FragmentViewTrackingStrategy(
            trackArguments = true,
            defaultFragmentComponentPredicate = object : ComponentPredicate<android.app.Fragment> {
                override fun accept(component: android.app.Fragment): Boolean {
                    return false
                }

                override fun getViewName(component: android.app.Fragment): String? {
                    return null
                }
            }
        )
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()

        // When
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyNoInteractions(mockAndroidxFragmentManager)

        // When
        argumentCaptor.firstValue.onFragmentResumed(mockDefaultFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockDefaultFragmentManager, mockFragment)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `will not attach fragment arguments as attributes if required so in a base activity`(
        forge: Forge
    ) {
        // Given
        testedStrategy = FragmentViewTrackingStrategy(false)
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val expectedAttrs = emptyMap<String, Any?>()
        val mockFragment: android.app.Fragment = mockDeprecatedFragmentWithArguments(forge)
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()

        // When
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )
        verifyNoInteractions(mockAndroidxFragmentManager)

        // When
        argumentCaptor.firstValue.onFragmentResumed(mockDefaultFragmentManager, mockFragment)
        argumentCaptor.firstValue.onFragmentPaused(mockDefaultFragmentManager, mockFragment)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startView(
                eq(mockFragment),
                eq(mockFragment.resolveViewUrl()),
                eq(expectedAttrs)
            )
            verify(rumMonitor.mockInstance).stopView(
                mockFragment
            )
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `when base activity stopped will unregister the right callback`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        testedStrategy.onActivityStarted(mockActivity)
        val argumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()
        verify(mockDefaultFragmentManager)
            .registerFragmentLifecycleCallbacks(
                argumentCaptor.capture(),
                eq(true)
            )

        // When
        testedStrategy.onActivityStopped(mockActivity)

        // Then
        verify(mockDefaultFragmentManager)
            .unregisterFragmentLifecycleCallbacks(argumentCaptor.firstValue)
        verifyNoInteractions(mockAndroidxFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `when base activity started API below O will do nothing`() {
        // When
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verifyNoInteractions(mockAndroidxFragmentManager)
        verifyNoInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `when base activity stopped API below O will do nothing`() {
        // When
        testedStrategy.onActivityStopped(mockActivity)

        // Then
        verifyNoInteractions(mockAndroidxFragmentManager)
        verifyNoInteractions(mockDefaultFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `it will handle well a FragmentActivity and a base Activity resumed in the same time`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        val androidXArgumentCaptor = argumentCaptor<FragmentManager.FragmentLifecycleCallbacks>()
        val baseArgumentCaptor =
            argumentCaptor<android.app.FragmentManager.FragmentLifecycleCallbacks>()

        // When
        testedStrategy.onActivityStarted(mockAndroidxActivity)
        testedStrategy.onActivityStarted(mockActivity)
        testedStrategy.onActivityStopped(mockAndroidxActivity)
        testedStrategy.onActivityStopped(mockActivity)

        // Then
        inOrder(mockAndroidxFragmentManager, mockDefaultFragmentManager) {
            verify(mockAndroidxFragmentManager)
                .registerFragmentLifecycleCallbacks(
                    androidXArgumentCaptor.capture(),
                    eq(true)
                )
            verify(mockDefaultFragmentManager)
                .registerFragmentLifecycleCallbacks(
                    baseArgumentCaptor.capture(),
                    eq(true)
                )
            verify(mockAndroidxFragmentManager)
                .unregisterFragmentLifecycleCallbacks(
                    androidXArgumentCaptor.firstValue
                )
            verify(mockDefaultFragmentManager)
                .unregisterFragmentLifecycleCallbacks(
                    baseArgumentCaptor.firstValue
                )
        }
    }

    // endregion

    // region ObjectTest

    override fun createInstance(forge: Forge): FragmentViewTrackingStrategy {
        return FragmentViewTrackingStrategy(
            forge.aBool(),
            StubComponentPredicate(forge),
            StubComponentPredicate(forge)
        )
    }

    override fun createEqualInstance(
        source: FragmentViewTrackingStrategy,
        forge: Forge
    ): FragmentViewTrackingStrategy {
        val trackExtras = source.trackArguments
        val supportCP = source.supportFragmentComponentPredicate
        val defaultCP = source.defaultFragmentComponentPredicate
        return FragmentViewTrackingStrategy(trackExtras, supportCP, defaultCP)
    }

    override fun createUnequalInstance(
        source: FragmentViewTrackingStrategy,
        forge: Forge
    ): FragmentViewTrackingStrategy? {
        return FragmentViewTrackingStrategy(
            if (forge.aBool()) {
                !source.trackArguments
            } else {
                source.trackArguments
            },
            if (forge.aBool()) {
                StubComponentPredicate(forge, useAlpha = false)
            } else {
                source.supportFragmentComponentPredicate
            },
            StubComponentPredicate(forge, useAlpha = false)
        )
    }

    // endregion

    // region Internal

    private fun mockFragmentWithArguments(forge: Forge): Fragment {
        val arguments = forgeFragmentArguments(forge)
        val fragment: Fragment = mock()
        whenever(fragment.arguments).thenReturn(arguments)
        return fragment
    }

    private fun mockDeprecatedFragmentWithArguments(forge: Forge): android.app.Fragment {
        val arguments = forgeFragmentArguments(forge)
        val fragment: android.app.Fragment = mock()
        whenever(fragment.arguments).thenReturn(arguments)
        return fragment
    }

    private fun forgeFragmentArguments(forge: Forge): Bundle {
        val arguments = Bundle()
        repeat(10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
        }
        return arguments
    }

    private fun Bundle.toRumAttributes(): Map<String, Any?> {
        val attributes = mutableMapOf<String, Any?>()
        keySet().forEach {
            attributes["view.arguments.$it"] = get(it)
        }
        return attributes
    }

    // endregion

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
