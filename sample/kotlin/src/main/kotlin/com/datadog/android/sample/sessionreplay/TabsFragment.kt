/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.sessionreplay

import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.datadog.android.sample.R
import com.google.android.material.bottomnavigation.BottomNavigationView

internal class TabsFragment : Fragment(R.layout.fragment_tabs) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bottomNavigationView = view.findViewById<BottomNavigationView>(R.id.main_nav_tab_layout)
        bottomNavigationView.menu.clear()
        NavTab.values().forEachIndexed { index, tab ->
            bottomNavigationView.menu.add(Menu.NONE, tab.id, index, tab.text).setIcon(tab.icon)
        }
    }

    enum class NavTab(
        @StringRes val text: Int,
        val id: Int,
        @DrawableRes val icon: Int
    ) {

        READING_LISTS(
            R.string.nav_item_saved,
            R.id.nav_tab_reading_lists,
            R.drawable.selector_nav_saved
        ),

        SEARCH(
            R.string.nav_item_search,
            R.id.nav_tab_search,
            R.drawable.selector_nav_search
        ),
        EDITS(
            R.string.nav_item_suggested_edits,
            R.id.nav_tab_edits,
            R.drawable.selector_nav_edits
        ),
        MORE(
            R.string.nav_item_more,
            R.id.nav_tab_more,
            R.drawable.ic_menu_white_24dp
        )
    }
}
