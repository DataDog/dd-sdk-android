/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.activity
import androidx.navigation.compose.composable

@Composable
internal fun SampleSelectionScreen(
    onTypographyClicked: () -> Unit,
    onLegacyClicked: () -> Unit,
    onImageClicked: () -> Unit,
    onInputClicked: () -> Unit,
    onToggleClicked: () -> Unit,
    onSelectorsClicked: () -> Unit,
    onFgmClicked: () -> Unit,
    onTabsClicked: () -> Unit,
    onInteropViewClicked: () -> Unit,
    onNav3Clicked: () -> Unit,
    onBackgroundClicked: () -> Unit,
    onNestedDialogsClicked: () -> Unit
) {
    val samples = listOf(
        "Typography Sample" to onTypographyClicked,
        "Image Sample" to onImageClicked,
        "Input Sample" to onInputClicked,
        "Toggle Buttons Sample" to onToggleClicked,
        "Tabs Sample" to onTabsClicked,
        "Selectors Sample" to onSelectorsClicked,
        "Fine Grained Masking Privacy Sample" to onFgmClicked,
        "Legacy Sample" to onLegacyClicked,
        "InteropView" to onInteropViewClicked,
        "Navigation 3" to onNav3Clicked,
        "Backgrounds" to onBackgroundClicked,
        "Nested Dialogs Sample" to onNestedDialogsClicked
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier.padding(DefaultPadding),
            text = "Jetpack Compose Sample",
            style = MaterialTheme.typography.h6
        )
        samples.forEach { (text, onClick) ->
            StyledButton(text = text, onClick = onClick)
        }
    }
}

@Composable
private fun StyledButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String
) {
    Button(
        modifier = modifier.fillMaxWidth().padding(DefaultPadding),
        content = {
            Text(text)
        },
        onClick = onClick
    )
}

@Suppress("LongMethod")
internal fun NavGraphBuilder.selectionNavigation(navController: NavHostController) {
    composable(SampleScreen.Root.navigationRoute) {
        SampleSelectionScreen(
            onTypographyClicked = {
                navController.navigate(SampleScreen.Typography.navigationRoute)
            },
            onImageClicked = {
                navController.navigate(SampleScreen.Image.navigationRoute)
            },
            onInputClicked = {
                navController.navigate(SampleScreen.Input.navigationRoute)
            },
            onToggleClicked = {
                navController.navigate(SampleScreen.Toggle.navigationRoute)
            },
            onTabsClicked = {
                navController.navigate(SampleScreen.Tabs.navigationRoute)
            },
            onSelectorsClicked = {
                navController.navigate(SampleScreen.Selectors.navigationRoute)
            },
            onFgmClicked = {
                navController.navigate(SampleScreen.FGM.navigationRoute)
            },
            onLegacyClicked = {
                navController.navigate(SampleScreen.Legacy.navigationRoute)
            },
            onInteropViewClicked = {
                navController.navigate(SampleScreen.InteropView.navigationRoute)
            },
            onNav3Clicked = {
                navController.navigate(SampleScreen.Navigation3.navigationRoute)
            },
            onBackgroundClicked = {
                navController.navigate(SampleScreen.Background.navigationRoute)
            },
            onNestedDialogsClicked = {
                navController.navigate(SampleScreen.NestedDialogs.navigationRoute)
            }
        )
    }

    composable(SampleScreen.Typography.navigationRoute) {
        TypographySample()
    }

    composable(SampleScreen.Image.navigationRoute) {
        ImageSample()
    }

    composable(SampleScreen.Input.navigationRoute) {
        InputSample()
    }

    composable(SampleScreen.Toggle.navigationRoute) {
        ToggleSample()
    }

    composable(SampleScreen.Selectors.navigationRoute) {
        SelectorSample()
    }

    composable(SampleScreen.FGM.navigationRoute) {
        FineGrainedMaskingSample()
    }

    composable(SampleScreen.Tabs.navigationRoute) {
        TabsSample()
    }

    composable(SampleScreen.InteropView.navigationRoute) {
        InteropViewSample()
    }

    composable(SampleScreen.Background.navigationRoute) {
        BackgroundSample()
    }

    composable(SampleScreen.NestedDialogs.navigationRoute) {
        NestedDialogsSample()
    }

    activity(SampleScreen.Legacy.navigationRoute) {
        activityClass = LegacyComposeActivity::class
    }

    activity(SampleScreen.Navigation3.navigationRoute) {
        activityClass = Navigation3Activity::class
    }
}

internal sealed class SampleScreen(
    val navigationRoute: String
) {

    object Root : SampleScreen(COMPOSE_ROOT)
    object Typography : SampleScreen("$COMPOSE_ROOT/typography")
    object Image : SampleScreen("$COMPOSE_ROOT/image")
    object Input : SampleScreen("$COMPOSE_ROOT/input")
    object Toggle : SampleScreen("$COMPOSE_ROOT/toggle")
    object Tabs : SampleScreen("$COMPOSE_ROOT/tabs")
    object Selectors : SampleScreen("$COMPOSE_ROOT/selectors")
    object FGM : SampleScreen("$COMPOSE_ROOT/fgm")
    object Legacy : SampleScreen("$COMPOSE_ROOT/legacy")
    object InteropView : SampleScreen("$COMPOSE_ROOT/interop_view")
    object Navigation3 : SampleScreen("$COMPOSE_ROOT/nav3")
    object Background : SampleScreen("$COMPOSE_ROOT/background")
    object NestedDialogs : SampleScreen("$COMPOSE_ROOT/nested_dialogs")

    companion object {
        private const val COMPOSE_ROOT = "compose"
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewSampleSelectionScreen() {
    SampleSelectionScreen(
        onLegacyClicked = {
        },
        onImageClicked = {
        },
        onInputClicked = {
        },
        onToggleClicked = {
        },
        onTypographyClicked = {
        },
        onSelectorsClicked = {
        },
        onFgmClicked = {
        },
        onTabsClicked = {
        },
        onInteropViewClicked = {
        },
        onNav3Clicked = {
        },
        onBackgroundClicked = {
        },
        onNestedDialogsClicked = {
        }
    )
}
