/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.navigation.NavDestination
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.R
import com.datadog.android.nightly.activities.ViewTrackingActivity
import com.datadog.android.nightly.activities.ViewTrackingFragmentActivity
import com.datadog.android.nightly.activities.ViewTrackingMixedFragmentActivity
import com.datadog.android.nightly.activities.ViewTrackingMixedNoFragmentActivity
import com.datadog.android.nightly.activities.ViewTrackingNavigationActivity
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.defaultConfigurationBuilder
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy
import com.datadog.android.rum.tracking.MixedViewTrackingStrategy
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class RumViewTrackingE2ETests {

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    @get:Rule
    val forge = ForgeRule()

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     */
    @Test
    fun rum_activity_view_tracking_strategy() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                        .build()
                },
                config = config
            )
        }
        launch(ViewTrackingActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     */
    @Test
    fun rum_activity_view_tracking_strategy_all_views_dropped() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(
                        ActivityViewTrackingStrategy(
                            true,
                            componentPredicate = object : ComponentPredicate<Activity> {
                                override fun accept(component: Activity): Boolean {
                                    return false
                                }

                                override fun getViewName(component: Activity): String {
                                    return "ViewTrackingActivityAllViewsDropped"
                                }
                            }
                        )
                    ).build()
                },
                config = config
            )
        }
        launch(ViewTrackingActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     */
    @Test
    fun rum_activity_view_tracking_strategy_custom_view_name() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(
                        ActivityViewTrackingStrategy(
                            true,
                            componentPredicate = object : ComponentPredicate<Activity> {
                                override fun accept(component: Activity): Boolean {
                                    return true
                                }

                                override fun getViewName(component: Activity): String {
                                    return "ViewTrackingActivityCustomView"
                                }
                            }
                        )
                    ).build()
                },
                config = config
            )
        }
        launch(ViewTrackingActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.FragmentViewTrackingStrategy#constructor(Boolean, ComponentPredicate<androidx.fragment.app.Fragment> = AcceptAllSupportFragments(), ComponentPredicate<android.app.Fragment> = AcceptAllDefaultFragment())
     */
    @Test
    fun rum_fragment_view_tracking_strategy() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(FragmentViewTrackingStrategy(true))
                        .build()
                },
                config = config
            )
        }
        launch(ViewTrackingFragmentActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.FragmentViewTrackingStrategy#constructor(Boolean, ComponentPredicate<androidx.fragment.app.Fragment> = AcceptAllSupportFragments(), ComponentPredicate<android.app.Fragment> = AcceptAllDefaultFragment())
     */
    @Test
    fun rum_fragment_view_tracking_strategy_all_views_dropped() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(
                        FragmentViewTrackingStrategy(
                            true,
                            supportFragmentComponentPredicate = object :
                                ComponentPredicate<Fragment> {
                                override fun accept(component: Fragment): Boolean {
                                    return false
                                }

                                override fun getViewName(component: Fragment): String? {
                                    return "ViewTrackingFragmentAllViewsDropped"
                                }
                            }
                        )
                    ).build()
                },
                config = config
            )
        }
        launch(ViewTrackingFragmentActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.FragmentViewTrackingStrategy#constructor(Boolean, ComponentPredicate<androidx.fragment.app.Fragment> = AcceptAllSupportFragments(), ComponentPredicate<android.app.Fragment> = AcceptAllDefaultFragment())
     */
    @Test
    fun rum_fragment_view_tracking_strategy_custom_view_name() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(
                        FragmentViewTrackingStrategy(
                            true,
                            supportFragmentComponentPredicate = object :
                                ComponentPredicate<Fragment> {
                                override fun accept(component: Fragment): Boolean {
                                    return true
                                }

                                override fun getViewName(component: Fragment): String? {
                                    return "ViewTrackingFragmentCustomView"
                                }
                            }
                        )
                    ).build()
                },
                config = config
            )
        }
        launch(ViewTrackingFragmentActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.NavigationViewTrackingStrategy#constructor(Int, Boolean, ComponentPredicate<androidx.navigation.NavDestination> = AcceptAllNavDestinations())
     */
    @Test
    fun rum_navigation_view_tracking_strategy() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(
                        NavigationViewTrackingStrategy(
                            R.id.nav_host_fragment,
                            true
                        )
                    )
                        .build()
                },
                config = config
            )
        }
        launch(ViewTrackingNavigationActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.NavigationViewTrackingStrategy#constructor(Int, Boolean, ComponentPredicate<androidx.navigation.NavDestination> = AcceptAllNavDestinations())
     */
    @Test
    fun rum_navigation_view_tracking_strategy_all_views_dropped() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(
                        NavigationViewTrackingStrategy(
                            R.id.nav_host_fragment,
                            true,
                            object : ComponentPredicate<NavDestination> {
                                override fun accept(component: NavDestination): Boolean {
                                    return false
                                }

                                override fun getViewName(component: NavDestination): String {
                                    return "ViewTrackingNavigationAllViewsDropped"
                                }
                            }
                        )
                    ).build()
                },
                config = config
            )
        }
        launch(ViewTrackingNavigationActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.NavigationViewTrackingStrategy#constructor(Int, Boolean, ComponentPredicate<androidx.navigation.NavDestination> = AcceptAllNavDestinations())
     */
    @Test
    fun rum_navigation_view_tracking_strategy_custom_view_name() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(
                        NavigationViewTrackingStrategy(
                            R.id.nav_host_fragment,
                            true,
                            object : ComponentPredicate<NavDestination> {
                                override fun accept(component: NavDestination): Boolean {
                                    return true
                                }

                                override fun getViewName(component: NavDestination): String {
                                    return component.label.toString()
                                }
                            }
                        )
                    ).build()
                },
                config = config
            )
        }
        launch(ViewTrackingNavigationActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.MixedViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities(), ComponentPredicate<androidx.fragment.app.Fragment> = AcceptAllSupportFragments(), ComponentPredicate<android.app.Fragment> = AcceptAllDefaultFragment())
     */
    @Test
    fun rum_mixed_view_tracking_strategy_no_fragment_activity() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(MixedViewTrackingStrategy(true))
                        .build()
                },
                config = config
            )
        }
        launch(ViewTrackingMixedNoFragmentActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.MixedViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities(), ComponentPredicate<androidx.fragment.app.Fragment> = AcceptAllSupportFragments(), ComponentPredicate<android.app.Fragment> = AcceptAllDefaultFragment())
     */
    @Test
    fun rum_mixed_view_tracking_strategy_fragment_activity() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true
            ).build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.useViewTrackingStrategy(MixedViewTrackingStrategy(true))
                        .build()
                },
                config = config
            )
        }
        launch(ViewTrackingMixedFragmentActivity::class.java)
    }
}
