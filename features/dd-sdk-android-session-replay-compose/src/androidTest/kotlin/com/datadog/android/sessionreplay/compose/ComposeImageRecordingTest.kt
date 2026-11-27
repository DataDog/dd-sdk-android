/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import android.graphics.Bitmap
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import android.graphics.Color as AndroidColor

internal class ComposeImageRecordingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val forge = ForgeRule()

    @Test
    fun `M_recordRootFillingPhotosAtScreenCoordinates_W_map_beforeAndAfterNativeScroll`() {
        // Given
        val fakePhotoColor = forge.anInt() or AndroidColor.BLACK
        val bitmap = Bitmap.createBitmap(
            forge.anInt(min = 16, max = 64),
            forge.anInt(min = 16, max = 64),
            Bitmap.Config.ARGB_8888
        ).apply { eraseColor(fakePhotoColor) }.asImageBitmap()
        val cards = mutableListOf<ComposeView>()
        lateinit var scrollView: ScrollView
        val testedRecorder = ComposeRecordingTestFixture(InstrumentationRegistry.getInstrumentation().targetContext)
        composeTestRule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                factory = { context ->
                    ScrollView(context).apply {
                        scrollView = this
                        addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                repeat(10) { index ->
                                    val card = ComposeView(context).apply {
                                        setContent {
                                            Image(
                                                bitmap,
                                                "photo-$index",
                                                Modifier.fillMaxSize(),
                                                contentScale = ContentScale.FillBounds
                                            )
                                        }
                                    }
                                    cards.add(card)
                                    addView(
                                        card,
                                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400)
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }
        composeTestRule.waitForIdle()
        val recordedPositions = mutableListOf<Long>()

        listOf(0, 120).forEach { scrollY ->
            composeTestRule.runOnUiThread { scrollView.scrollTo(0, scrollY) }
            composeTestRule.waitForIdle()
            composeTestRule.runOnIdle {
                assertEquals(scrollY, scrollView.scrollY)
                val card = cards[1]
                val location = IntArray(2).also { card.getLocationOnScreen(it) }
                val density = card.resources.displayMetrics.density

                // When
                val images = testedRecorder.map(card)

                // Then
                assertEquals("A photo filling its own ComposeView must be recorded", 1, images.size)
                val image = images.single()
                assertEquals((location[0] / density).toLong(), image.x)
                assertEquals((location[1] / density).toLong(), image.y)
                assertEquals((card.width / density).toLong(), image.width)
                assertEquals((card.height / density).toLong(), image.height)
                assertEquals(false, image.isEmpty)
                assertEquals(0, testedRecorder.pendingJobs)
                val recorded = testedRecorder.decode(image)
                val pixel = recorded.getPixel(recorded.width / 2, recorded.height / 2)
                assertEquals(AndroidColor.red(fakePhotoColor).toDouble(), AndroidColor.red(pixel).toDouble(), 5.0)
                assertEquals(AndroidColor.green(fakePhotoColor).toDouble(), AndroidColor.green(pixel).toDouble(), 5.0)
                assertEquals(AndroidColor.blue(fakePhotoColor).toDouble(), AndroidColor.blue(pixel).toDouble(), 5.0)
                assertEquals(255, AndroidColor.alpha(pixel))
                recordedPositions.add(image.y)
            }
        }
        assertTrue(recordedPositions[1] < recordedPositions[0])
    }
}
