/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.datadog.android.compose.NavigationViewTrackingEffect
import com.datadog.android.compose.trackClick
import com.datadog.android.rum.tracking.AcceptAllNavDestinations
import kotlin.random.Random

@Composable
internal fun NavigationSampleView() {
    val navController = rememberNavController().apply {
        NavigationViewTrackingEffect(
            navController = this,
            trackArguments = true,
            destinationPredicate = AcceptAllNavDestinations()
        )
    }
    ViewNavigation(navController = navController)
}

internal class SimpleViewIdPreviewProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String>
        get() = sequenceOf("one", "two", "three")
}

@Preview
@Composable
internal fun SimpleView(
    @PreviewParameter(provider = SimpleViewIdPreviewProvider::class) viewId: String,
    onNavigate: () -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            "View $viewId",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        val onClick = trackClick(
            targetName = "Open View",
            onClick = { onNavigate.invoke() }
        )
        Row(modifier = Modifier.padding(32.dp)) {
            Button(
                onClick = onClick,
                modifier = Modifier.weight(1.0f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Open Next Random View",
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.weight(1.0f)
                )
            }
        }
    }
}

@Suppress("ThrowingInternalException") // not an issue in the sample
@Composable
internal fun ViewNavigation(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.One.compositionRoute
    ) {
        composable(Screen.One.compositionRoute) {
            SimpleView(viewId = "one") {
                navController.navigate(oneOf(Screen.Two, Screen.Other).navigationRoute)
            }
        }
        composable(Screen.Two.compositionRoute) {
            SimpleView(viewId = "two") {
                navController.navigate(oneOf(Screen.One, Screen.Other).navigationRoute)
            }
        }
        composable(
            Screen.Other.compositionRoute,
            arguments = listOf(navArgument(Screen.SCREEN_ID) { type = NavType.StringType })
        ) {
            val screenId = it.arguments?.getString(Screen.SCREEN_ID)
                ?: throw IllegalArgumentException("Cannot start screen without Id")
            SimpleView(viewId = screenId) {
                navController.navigate(oneOf(Screen.One, Screen.Two).navigationRoute)
            }
        }
    }
}

internal sealed class Screen(
    val compositionRoute: String,
    open val navigationRoute: String = compositionRoute
) {
    object One : Screen("$ROOT/one")
    object Two : Screen("$ROOT/two")
    object Other : Screen("$ROOT/{$SCREEN_ID}") {
        override val navigationRoute: String
            get() = "$ROOT/${oneOf("three", "four", "five")}"
    }

    companion object {
        const val ROOT = "compose/screen"
        const val SCREEN_ID = "screenId"
    }
}

internal fun <T> oneOf(vararg items: T): T {
    return items[Random.nextInt(items.size)]
}
