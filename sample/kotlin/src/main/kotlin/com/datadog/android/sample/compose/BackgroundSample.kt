/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("LongMethod", "MagicNumber")

package com.datadog.android.sample.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Sample screen demonstrating how background colors and brushes are captured in Compose
 * session replay. Covers solid colors, gradients, Material2/Material3 containers,
 * modifier ordering, corner radii, and nested/stacked backgrounds.
 */
@Composable
internal fun BackgroundSample() {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionLabel("A — Color literal variants (baseline)")

        CaseLabel("A1 — bare Column · expect RED")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(Color.Red)
                .padding(8.dp)
        ) { Text("A1", color = Color.White) }

        CaseLabel("A2 — Column · MaterialTheme.colors.primary")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(MaterialTheme.colors.primary)
                .padding(8.dp)
        ) { Text("A2", color = Color.White) }

        CaseLabel("A3 — Column · Color.Red.copy(alpha = 0.5f)")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(Color.Red.copy(alpha = 0.5f))
                .padding(8.dp)
        ) { Text("A3", color = Color.Black) }

        SectionLabel("B — Brush backgrounds")

        CaseLabel("B1 — Modifier.background(Brush.linearGradient(Red→Blue))")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(Brush.linearGradient(listOf(Color.Red, Color.Blue)))
                .padding(8.dp)
        ) { Text("B1", color = Color.White) }

        CaseLabel("B2 — Modifier.background(Brush.horizontalGradient(Yellow→Green))")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(Brush.horizontalGradient(listOf(Color.Yellow, Color.Green)))
                .padding(8.dp)
        ) { Text("B2", color = Color.Black) }

        SectionLabel("C — Material containers")

        CaseLabel("C1 — Material2 Surface(color = Yellow)")
        Surface(
            color = Color.Yellow,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
        ) { Text("C1", modifier = Modifier.padding(8.dp), color = Color.Black) }

        CaseLabel("C2 — Material2 Card(backgroundColor = Magenta)")
        Card(
            backgroundColor = Color.Magenta,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
        ) { Text("C2", modifier = Modifier.padding(8.dp), color = Color.White) }

        CaseLabel("C3 — Material3 Surface(color = Cyan)")
        androidx.compose.material3.Surface(
            color = Color.Cyan,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
        ) { Text("C3", modifier = Modifier.padding(8.dp), color = Color.Black) }

        CaseLabel("C4 — Material3 Card containing Text")
        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
        ) { Text("C4", modifier = Modifier.padding(8.dp), color = Color.Black) }

        SectionLabel("D — Modifier order")

        CaseLabel("D1 — background BEFORE padding · expect CYAN edge-to-edge")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(Color.Cyan)
                .padding(16.dp)
        ) { Text("D1", color = Color.Black) }

        CaseLabel("D2 — background AFTER padding · expect YELLOW inset")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .padding(16.dp)
                .background(Color.Yellow)
        ) { Text("D2", color = Color.Black) }

        CaseLabel("D3 — fillMaxWidth → background → no padding · expect RED")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(Color.Red)
        ) { Text("D3", color = Color.White) }

        CaseLabel("D4 — size(80) + background · expect 80dp RED square (left-aligned)")
        Row(modifier = Modifier.fillMaxWidth().height(BAND)) {
            Column(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Red)
            ) { Text("D4", color = Color.White) }
        }

        SectionLabel("E — Shape / corner radius")

        CaseLabel("E1 — background(Magenta, RoundedCornerShape(16))")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(Color.Magenta, RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) { Text("E1", color = Color.White) }

        CaseLabel("E2 — clip(RoundedCornerShape(16)) → background(Green)")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Green)
                .padding(8.dp)
        ) { Text("E2", color = Color.Black) }

        SectionLabel("F — Stacked & nested")

        CaseLabel("F1 — background(Red).background(Blue) · expect BLUE on top of RED")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(Color.Red)
                .background(Color.Blue)
                .padding(8.dp)
        ) { Text("F1", color = Color.White) }

        CaseLabel("F2 — Outer Column(Red) wrapping Inner Column(Green)")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND * 2)
                .background(Color.Red)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAND)
                    .background(Color.Green)
                    .padding(8.dp)
            ) { Text("F2 inner (parent should be RED)", color = Color.Black) }
        }

        CaseLabel("F3 — Surface(Yellow) wrapping Column(Cyan)")
        Surface(
            color = Color.Yellow,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND * 2)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .height(BAND)
                    .background(Color.Cyan)
                    .padding(8.dp)
            ) { Text("F3 inner (parent should be YELLOW)", color = Color.Black) }
        }

        SectionLabel("G — Edge cases")

        CaseLabel("G1 — Column with no children · expect 80dp RED square")
        Row(modifier = Modifier.fillMaxWidth().height(BAND)) {
            Column(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Red)
                    .semantics { contentDescription = "G1 empty column" }
            ) { /* no children */ }
        }

        CaseLabel("G2 — Column wrapping a Box (no semantic descendants)")
        Row(modifier = Modifier.fillMaxWidth().height(BAND)) {
            Column(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Red)
                    .semantics { contentDescription = "G2 column with box" }
            ) {
                Box(modifier = Modifier.size(20.dp).background(Color.White))
            }
        }

        CaseLabel("G3 — Column with .semantics{} (control · should always work)")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(Color.Green)
                .semantics { contentDescription = "G3 control" }
                .padding(8.dp)
        ) { Text("G3 control", color = Color.Black) }

        SectionLabel("H — Modifier.background alpha parameter (bug-fix verification)")

        // The brush overload of Modifier.background has an explicit alpha parameter:
        //   Modifier.background(brush, shape, alpha)
        // This is what BackgroundElement.alpha captures and what was previously ignored.

        CaseLabel("H1 — background(SolidColor(Red), alpha=0.5f) · expect semi-transparent red")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(brush = SolidColor(Color.Red), alpha = 0.5f)
                .padding(8.dp)
        ) { Text("H1 SolidColor alpha=0.5", color = Color.Black) }

        CaseLabel("H2 — background(SolidColor(Blue), alpha=0.25f) · expect 25% blue")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(brush = SolidColor(Color.Blue), alpha = 0.25f)
                .padding(8.dp)
        ) { Text("H2 SolidColor alpha=0.25", color = Color.Black) }

        CaseLabel("H3 — background(linearGradient(Red→Blue), alpha=0.5f) · expect semi-transparent gradient")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(
                    brush = Brush.linearGradient(listOf(Color.Red, Color.Blue)),
                    alpha = 0.5f
                )
                .padding(8.dp)
        ) { Text("H3 gradient alpha=0.5", color = Color.Black) }

        CaseLabel("H4 — background(linearGradient(Yellow→Green), alpha=1f) · expect fully opaque (baseline)")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND)
                .background(
                    brush = Brush.linearGradient(listOf(Color.Yellow, Color.Green)),
                    alpha = 1f
                )
                .padding(8.dp)
        ) { Text("H4 gradient alpha=1.0 (opaque)", color = Color.Black) }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.onBackground,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun CaseLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onBackground,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp)
    )
}

private val BAND = 48.dp

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewBackgroundSample() {
    BackgroundSample()
}
