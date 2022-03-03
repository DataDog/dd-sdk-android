/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import androidx.fragment.app.Fragment

/**
 * Special ComponentPredicate which can be used when defining tracking strategy for
 * Android TV applications. By using this predicate, by default the extra
 * view events created by `android.leanback` package helper fragments are excluded:
 * `androidx.leanback.app.HeadersSupportFragment`, `androidx.leanback.app.RowsSupportFragment`.
 * In case you also want to use your own implementation of a ComponentPredicate you can wrap
 * it inside the `AndroidTvComponentPredicate`:
 * ```
 * val predicate = AndroidTvComponentPredicate(yourOwnPredicate)
 * ```
 */
class AndroidTvFragmentPredicate(
    internal val wrappedPredicate: ComponentPredicate<Fragment>
) : ComponentPredicate<Fragment> by wrappedPredicate {

    constructor() : this(AcceptAllSupportFragments())

    override fun accept(component: Fragment): Boolean {
        if (isLeanbackApiHelperFragment(component)) return false
        return wrappedPredicate.accept(component)
    }

    private fun isLeanbackApiHelperFragment(fragment: Fragment): Boolean {
        return fragment.javaClass.name in LEANBACK_FRAGMENTS
    }

    companion object {
        private const val LEANBACK_HEADERS_SUPPORT_FRAGMENT_NAME =
            "androidx.leanback.app.HeadersSupportFragment"
        private val LEANBACK_ROWS_SUPPORT_FRAGMENT_NAME =
            "androidx.leanback.app.RowsSupportFragment"
        private val LEANBACK_FRAGMENTS = setOf(
            LEANBACK_HEADERS_SUPPORT_FRAGMENT_NAME,
            LEANBACK_ROWS_SUPPORT_FRAGMENT_NAME
        )
    }
}
