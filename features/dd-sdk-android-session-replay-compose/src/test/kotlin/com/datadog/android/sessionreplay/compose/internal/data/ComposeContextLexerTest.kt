/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.runners.Parameterized
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ComposeContextLexerTest {

    @ParameterizedTest(name = "M parse the source info W parse {0}")
    @MethodSource("provideTestArguments")
    fun `M parse the source info W parse()`(
        fakeSource: String,
        expectedOutput: ComposeContext
    ) {
        // When
        val value = ComposeContextLexer.parse(fakeSource)

        // Then
        assertThat(value).isEqualTo(expectedOutput)
    }

    @Test
    fun `M return null W parse() { invalid sourceInfo}`(
        @StringForgery fakeSource: String
    ) {
        // When
        val value = ComposeContextLexer.parse(fakeSource)

        // Then
        assertThat(value).isNull()
    }

    // suppress max line length to keep our fixtures as-is
    @Suppress("ktlint:standard:max-line-length", "MaxLineLength", "MagicNumber")
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun provideTestArguments(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "*101@4372L24",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 102, offset = 4372, length = 24)),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "*43@1845L7",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 44, offset = 1845, length = 7)),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "*43@1883L23,44@1923L170",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 44, offset = 1883, length = 23),
                            SourceLocationInfo(lineNumber = 45, offset = 1923, length = 170)
                        ),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "*49@2098L23,50@2138L204",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 50, offset = 2098, length = 23),
                            SourceLocationInfo(lineNumber = 51, offset = 2138, length = 204)
                        ),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "*55@2139L7",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 56, offset = 2139, length = 7)),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "*68@2709L140",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 69, offset = 2709, length = 140)),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "*75@3334L23",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 76, offset = 3334, length = 23)),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "*83@3284L59,86@3364L335",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 84, offset = 3284, length = 59),
                            SourceLocationInfo(lineNumber = 87, offset = 3364, length = 335)
                        ),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "111@4841L21",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 112, offset = 4841, length = 21)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "114@4913L22",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 115, offset = 4913, length = 22)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "114@5392L37",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 115, offset = 5392, length = 37)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "115@4972L23",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 116, offset = 4972, length = 23)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "121@5525L14",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 122, offset = 5525, length = 14)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "130@5988L7",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 131, offset = 5988, length = 7)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "141@5871L8",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 142, offset = 5871, length = 8)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "145@5578L1266",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 146, offset = 5578, length = 1266)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "256@10908L15",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 257, offset = 10908, length = 15)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "307@12925L123",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 308, offset = 12925, length = 123)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "312@13111L41",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 313, offset = 13111, length = 41)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "340@17650L65",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 341, offset = 17650, length = 65)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "51@2127L7",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 52, offset = 2127, length = 7)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "556@22499L546",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 557, offset = 22499, length = 546)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "67@2658L133",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(SourceLocationInfo(lineNumber = 68, offset = 2658, length = 133)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "96@4735L7,97@4767L190",
                    ComposeContext(
                        name = null,
                        sourceFile = null,
                        packageHash = -1,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 97, offset = 4735, length = 7),
                            SourceLocationInfo(lineNumber = 98, offset = 4767, length = 190)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = false,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(AppCompatTheme)P(1,2,3,4)",
                    ComposeContext(
                        name = "AppCompatTheme",
                        sourceFile = null,
                        packageHash = -1,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(BasicText)P(8,3,7,4,5:c#ui.text.style.TextOverflow,6,1,2)94@4599L7,138@6175L41:BasicText.kt#423gt5",
                    ComposeContext(
                        name = "BasicText",
                        sourceFile = "BasicText.kt",
                        packageHash = 245385689,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 95, offset = 4599, length = 7),
                            SourceLocationInfo(lineNumber = 139, offset = 6175, length = 41)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 8, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = "androidx.compose.ui.text.style.TextOverflow"),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Box)199@7940L70:Box.kt#2w3rfo",
                    ComposeContext(
                        name = "Box",
                        sourceFile = "Box.kt",
                        packageHash = 174855588,
                        locations = listOf(SourceLocationInfo(lineNumber = 200, offset = 7940, length = 70)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Box)P(2,1,3)70@3267L67,71@3339L130:Box.kt#2w3rfo",
                    ComposeContext(
                        name = "Box",
                        sourceFile = "Box.kt",
                        packageHash = 174855588,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 71, offset = 3267, length = 67),
                            SourceLocationInfo(lineNumber = 72, offset = 3339, length = 130)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Button)P(8,7,5,6,4,9!2,3)97@4664L39,98@4754L11,99@4800L6,101@4890L14,105@5053L21,111@5250L24,106@5079L1119:Button.kt#jmzs0o",
                    ComposeContext(
                        name = "Button",
                        sourceFile = "Button.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 98, offset = 4664, length = 39),
                            SourceLocationInfo(lineNumber = 99, offset = 4754, length = 11),
                            SourceLocationInfo(lineNumber = 100, offset = 4800, length = 6),
                            SourceLocationInfo(lineNumber = 102, offset = 4890, length = 14),
                            SourceLocationInfo(lineNumber = 106, offset = 5053, length = 21),
                            SourceLocationInfo(lineNumber = 112, offset = 5250, length = 24),
                            SourceLocationInfo(lineNumber = 107, offset = 5079, length = 1119)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 8, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 9, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 10, inlineClass = null),
                            Parameter(sortedIndex = 11, inlineClass = null),
                            Parameter(sortedIndex = 12, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(CompositionLocalProvider)P(1)227@10002L9:CompositionLocal.kt#9igjgp",
                    ComposeContext(
                        name = "CompositionLocalProvider",
                        sourceFile = "CompositionLocal.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 228, offset = 10002, length = 9)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Content)427@17045L8:ComposeView.android.kt#itgzvw",
                    ComposeContext(
                        name = "Content",
                        sourceFile = "ComposeView.android.kt",
                        packageHash = 1137893036,
                        locations = listOf(SourceLocationInfo(lineNumber = 428, offset = 17045, length = 8)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Crossfade)P(3!1,2)103@4375L64,104@4461L66,138@5750L159:Crossfade.kt#xbi5r1",
                    ComposeContext(
                        name = "Crossfade",
                        sourceFile = "Crossfade.kt",
                        packageHash = 2014706845,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 104, offset = 4375, length = 64),
                            SourceLocationInfo(lineNumber = 105, offset = 4461, length = 66),
                            SourceLocationInfo(lineNumber = 139, offset = 5750, length = 159)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Crossfade)P(3,2)71@2743L29,72@2788L53:Crossfade.kt#xbi5r1",
                    ComposeContext(
                        name = "Crossfade",
                        sourceFile = "Crossfade.kt",
                        packageHash = 2014706845,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 72, offset = 2743, length = 29),
                            SourceLocationInfo(lineNumber = 73, offset = 2788, length = 53)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(DialogHost)40@1621L29,41@1704L16,42@1748L36,43@1806L36,*47@1969L623:DialogHost.kt#opm8kd",
                    ComposeContext(
                        name = "DialogHost",
                        sourceFile = "DialogHost.kt",
                        packageHash = 1494216157,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 41, offset = 1621, length = 29),
                            SourceLocationInfo(lineNumber = 42, offset = 1704, length = 16),
                            SourceLocationInfo(lineNumber = 43, offset = 1748, length = 36),
                            SourceLocationInfo(lineNumber = 44, offset = 1806, length = 36),
                            SourceLocationInfo(lineNumber = 48, offset = 1969, length = 623)
                        ),
                        repeatOffset = 4,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(DisposableEffect)P(1)155@6219L47:Effects.kt#9igjgp",
                    ComposeContext(
                        name = "DisposableEffect",
                        sourceFile = "Effects.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 156, offset = 6219, length = 47)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(DisposableEffect)P(1,2)195@8105L53:Effects.kt#9igjgp",
                    ComposeContext(
                        name = "DisposableEffect",
                        sourceFile = "Effects.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 196, offset = 8105, length = 53)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Divider)P(1,0:c#ui.graphics.Color,3:c#ui.unit.Dp,2:c#ui.unit.Dp)45@1819L6,59@2200L147:Divider.kt#jmzs0o",
                    ComposeContext(
                        name = "Divider",
                        sourceFile = "Divider.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 46, offset = 1819, length = 6),
                            SourceLocationInfo(lineNumber = 60, offset = 2200, length = 147)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 3, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 2, inlineClass = "androidx.compose.ui.unit.Dp")
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Divider)P(1,2:c#ui.unit.Dp,0:c#ui.graphics.Color)366@16242L7,368@16321L66:TabRow.kt#jmzs0o",
                    ComposeContext(
                        name = "Divider",
                        sourceFile = "TabRow.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 367, offset = 16242, length = 7),
                            SourceLocationInfo(lineNumber = 369, offset = 16321, length = 66)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 0, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(HorizontalPager)P(2,6,8,7,4:c#ui.unit.Dp,1,9,3,5)",
                    ComposeContext(
                        name = "HorizontalPager",
                        sourceFile = null,
                        packageHash = -1,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 8, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 9, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 10, inlineClass = null),
                            Parameter(sortedIndex = 11, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Indicator)P(2,1:c#ui.unit.Dp,0:c#ui.graphics.Color)383@16830L7,385@16854L142:TabRow.kt#jmzs0o",
                    ComposeContext(
                        name = "Indicator",
                        sourceFile = "TabRow.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 384, offset = 16830, length = 7),
                            SourceLocationInfo(lineNumber = 386, offset = 16854, length = 142)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 0, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(InteractionSampleView)47@2011L63,57@2436L147,57@2354L229:InteractionSample.kt#d7l2vq",
                    ComposeContext(
                        name = "InteractionSampleView",
                        sourceFile = "InteractionSample.kt",
                        packageHash = 798801110,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 48, offset = 2011, length = 63),
                            SourceLocationInfo(lineNumber = 58, offset = 2436, length = 147),
                            SourceLocationInfo(lineNumber = 58, offset = 2354, length = 229)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Item)76@2951L204:LazyListItemProvider.kt#428nma",
                    ComposeContext(
                        name = "Item",
                        sourceFile = "LazyListItemProvider.kt",
                        packageHash = 245627794,
                        locations = listOf(SourceLocationInfo(lineNumber = 77, offset = 2951, length = 204)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(ItemRow)68@2748L42,*76@3020L7,79@3139L7,106@4096L58,97@3719L744:InteractionSample.kt#d7l2vq",
                    ComposeContext(
                        name = "ItemRow",
                        sourceFile = "InteractionSample.kt",
                        packageHash = 798801110,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 69, offset = 2748, length = 42),
                            SourceLocationInfo(lineNumber = 77, offset = 3020, length = 7),
                            SourceLocationInfo(lineNumber = 80, offset = 3139, length = 7),
                            SourceLocationInfo(lineNumber = 107, offset = 4096, length = 58),
                            SourceLocationInfo(lineNumber = 98, offset = 3719, length = 744)
                        ),
                        repeatOffset = 1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LaunchedEffect)P(1)338@14289L58:Effects.kt#9igjgp",
                    ComposeContext(
                        name = "LaunchedEffect",
                        sourceFile = "Effects.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 339, offset = 14289, length = 58)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LaunchedEffect)P(1,2)361@15297L64:Effects.kt#9igjgp",
                    ComposeContext(
                        name = "LaunchedEffect",
                        sourceFile = "Effects.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 362, offset = 15297, length = 64)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LaunchedEffect)P(1,2,3)385@16335L70:Effects.kt#9igjgp",
                    ComposeContext(
                        name = "LaunchedEffect",
                        sourceFile = "Effects.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 386, offset = 16335, length = 70)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Layout)P(!1,2)71@2788L7,72@2843L7,73@2855L389:Layout.kt#80mrfh",
                    ComposeContext(
                        name = "Layout",
                        sourceFile = "Layout.kt",
                        packageHash = 484791389,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 72, offset = 2788, length = 7),
                            SourceLocationInfo(lineNumber = 73, offset = 2843, length = 7),
                            SourceLocationInfo(lineNumber = 74, offset = 2855, length = 389)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LazyColumn)P(4,6,1,5,8,3,2,7)347@16950L23,353@17304L15,357@17406L388:LazyDsl.kt#428nma",
                    ComposeContext(
                        name = "LazyColumn",
                        sourceFile = "LazyDsl.kt",
                        packageHash = 245627794,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 348, offset = 16950, length = 23),
                            SourceLocationInfo(lineNumber = 354, offset = 17304, length = 15),
                            SourceLocationInfo(lineNumber = 358, offset = 17406, length = 388)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 8, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 9, inlineClass = null),
                            Parameter(sortedIndex = 10, inlineClass = null),
                            Parameter(sortedIndex = 11, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LazyLayout)P(!1,2,3)58@2285L34,60@2325L1039:LazyLayout.kt#wow0x6",
                    ComposeContext(
                        name = "LazyLayout",
                        sourceFile = "LazyLayout.kt",
                        packageHash = 1976722602,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 59, offset = 2285, length = 34),
                            SourceLocationInfo(lineNumber = 61, offset = 2325, length = 1039)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LazyLayoutPinnableItem)P(2,1,3)49@1914L77,51@2089L7,52@2132L43,52@2101L74,53@2180L103:LazyLayoutPinnableItem.kt#wow0x6",
                    ComposeContext(
                        name = "LazyLayoutPinnableItem",
                        sourceFile = "LazyLayoutPinnableItem.kt",
                        packageHash = 1976722602,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 50, offset = 1914, length = 77),
                            SourceLocationInfo(lineNumber = 52, offset = 2089, length = 7),
                            SourceLocationInfo(lineNumber = 53, offset = 2132, length = 43),
                            SourceLocationInfo(lineNumber = 53, offset = 2101, length = 74),
                            SourceLocationInfo(lineNumber = 54, offset = 2180, length = 103)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LazyLayoutPrefetcher)P(1)40@1563L7,41@1575L211:LazyLayoutPrefetcher.android.kt#wow0x6",
                    ComposeContext(
                        name = "LazyLayoutPrefetcher",
                        sourceFile = "LazyLayoutPrefetcher.android.kt",
                        packageHash = 1976722602,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 41, offset = 1563, length = 7),
                            SourceLocationInfo(lineNumber = 42, offset = 1575, length = 211)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LazyList)P(7,9,2,8,6,3,10!1,4,12,11,5)78@3680L50,80@3756L48,82@3830L292,95@4128L48,97@4224L18,103@4479L277,111@4820L164,121@5208L7,99@4334L1359:LazyList.kt#428nma",
                    ComposeContext(
                        name = "LazyList",
                        sourceFile = "LazyList.kt",
                        packageHash = 245627794,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 79, offset = 3680, length = 50),
                            SourceLocationInfo(lineNumber = 81, offset = 3756, length = 48),
                            SourceLocationInfo(lineNumber = 83, offset = 3830, length = 292),
                            SourceLocationInfo(lineNumber = 96, offset = 4128, length = 48),
                            SourceLocationInfo(lineNumber = 98, offset = 4224, length = 18),
                            SourceLocationInfo(lineNumber = 104, offset = 4479, length = 277),
                            SourceLocationInfo(lineNumber = 112, offset = 4820, length = 164),
                            SourceLocationInfo(lineNumber = 122, offset = 5208, length = 7),
                            SourceLocationInfo(lineNumber = 100, offset = 4334, length = 1359)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 9, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 8, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 10, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 12, inlineClass = null),
                            Parameter(sortedIndex = 11, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 13, inlineClass = null),
                            Parameter(sortedIndex = 14, inlineClass = null),
                            Parameter(sortedIndex = 15, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LazyRow)P(4,6,1,5,3,7,2)401@18906L23,407@19257L15,410@19320L347:LazyDsl.kt#428nma",
                    ComposeContext(
                        name = "LazyRow",
                        sourceFile = "LazyDsl.kt",
                        packageHash = 245627794,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 402, offset = 18906, length = 23),
                            SourceLocationInfo(lineNumber = 408, offset = 19257, length = 15),
                            SourceLocationInfo(lineNumber = 411, offset = 19320, length = 347)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LazyRow)P(4,6,1,5,3,8,2,7)291@14018L23,297@14369L15,301@14471L389:LazyDsl.kt#428nma",
                    ComposeContext(
                        name = "LazyRow",
                        sourceFile = "LazyDsl.kt",
                        packageHash = 245627794,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 292, offset = 14018, length = 23),
                            SourceLocationInfo(lineNumber = 298, offset = 14369, length = 15),
                            SourceLocationInfo(lineNumber = 302, offset = 14471, length = 389)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 8, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 9, inlineClass = null),
                            Parameter(sortedIndex = 10, inlineClass = null),
                            Parameter(sortedIndex = 11, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LazySaveableStateHolderProvider)42@2089L7,43@2114L172,48@2291L161:LazySaveableStateHolder.kt#wow0x6",
                    ComposeContext(
                        name = "LazySaveableStateHolderProvider",
                        sourceFile = "LazySaveableStateHolder.kt",
                        packageHash = 1976722602,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 43, offset = 2089, length = 7),
                            SourceLocationInfo(lineNumber = 44, offset = 2114, length = 172),
                            SourceLocationInfo(lineNumber = 49, offset = 2291, length = 161)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(LocalOwnersProvider)P(1)46@1896L240:NavBackStackEntryProvider.kt#opm8kd",
                    ComposeContext(
                        name = "LocalOwnersProvider",
                        sourceFile = "NavBackStackEntryProvider.kt",
                        packageHash = 1494216157,
                        locations = listOf(SourceLocationInfo(lineNumber = 47, offset = 1896, length = 240)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(MaterialTheme)P(!1,3,2)59@2947L6,60@2998L10,61@3045L6,*64@3120L184,69@3367L16,70@3410L45,73@3581L4,71@3460L492:MaterialTheme.kt#jmzs0o",
                    ComposeContext(
                        name = "MaterialTheme",
                        sourceFile = "MaterialTheme.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 60, offset = 2947, length = 6),
                            SourceLocationInfo(lineNumber = 61, offset = 2998, length = 10),
                            SourceLocationInfo(lineNumber = 62, offset = 3045, length = 6),
                            SourceLocationInfo(lineNumber = 65, offset = 3120, length = 184),
                            SourceLocationInfo(lineNumber = 70, offset = 3367, length = 16),
                            SourceLocationInfo(lineNumber = 71, offset = 3410, length = 45),
                            SourceLocationInfo(lineNumber = 74, offset = 3581, length = 4),
                            SourceLocationInfo(lineNumber = 72, offset = 3460, length = 492)
                        ),
                        repeatOffset = 3,
                        parameters = listOf(
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(NavHost)P(2)95@3519L7,*96@3595L7,99@3778L7,110@4282L170,120@4543L29,127@4892L223,133@5116L27,135@5214L7,141@5363L33,182@7052L27:NavHost.kt#opm8kd",
                    ComposeContext(
                        name = "NavHost",
                        sourceFile = "NavHost.kt",
                        packageHash = 1494216157,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 96, offset = 3519, length = 7),
                            SourceLocationInfo(lineNumber = 97, offset = 3595, length = 7),
                            SourceLocationInfo(lineNumber = 100, offset = 3778, length = 7),
                            SourceLocationInfo(lineNumber = 111, offset = 4282, length = 170),
                            SourceLocationInfo(lineNumber = 121, offset = 4543, length = 29),
                            SourceLocationInfo(lineNumber = 128, offset = 4892, length = 223),
                            SourceLocationInfo(lineNumber = 134, offset = 5116, length = 27),
                            SourceLocationInfo(lineNumber = 136, offset = 5214, length = 7),
                            SourceLocationInfo(lineNumber = 142, offset = 5363, length = 33),
                            SourceLocationInfo(lineNumber = 183, offset = 7052, length = 27)
                        ),
                        repeatOffset = 1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(NavHost)P(2,4,1,3)69@2679L126,67@2639L190:NavHost.kt#opm8kd",
                    ComposeContext(
                        name = "NavHost",
                        sourceFile = "NavHost.kt",
                        packageHash = 1494216157,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 70, offset = 2679, length = 126),
                            SourceLocationInfo(lineNumber = 68, offset = 2639, length = 190)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(NavigationSampleView)50@2104L45:NavigationSample.kt#d7l2vq",
                    ComposeContext(
                        name = "NavigationSampleView",
                        sourceFile = "NavigationSample.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 51, offset = 2104, length = 45)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(NavigationViewTrackingEffect)P(1,3)123@4384L47,124@4471L53,126@4571L7,127@4583L616:Navigation.kt#ga31me",
                    ComposeContext(
                        name = "NavigationViewTrackingEffect",
                        sourceFile = "Navigation.kt",
                        packageHash = 984397046,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 124, offset = 4384, length = 47),
                            SourceLocationInfo(lineNumber = 125, offset = 4471, length = 53),
                            SourceLocationInfo(lineNumber = 127, offset = 4571, length = 7),
                            SourceLocationInfo(lineNumber = 128, offset = 4583, length = 616)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(PlatformMaterialTheme)22@789L9:MaterialTheme.android.kt#jmzs0o",
                    ComposeContext(
                        name = "PlatformMaterialTheme",
                        sourceFile = "MaterialTheme.android.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 23, offset = 789, length = 9)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(PopulateVisibleList)70@2783L7,*72@2844L1023:DialogHost.kt#opm8kd",
                    ComposeContext(
                        name = "PopulateVisibleList",
                        sourceFile = "DialogHost.kt",
                        packageHash = 1494216157,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 71, offset = 2783, length = 7),
                            SourceLocationInfo(lineNumber = 73, offset = 2844, length = 1023)
                        ),
                        repeatOffset = 1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(ProvideAndroidCompositionLocals)P(1)88@3008L87,92@3137L37,94@3197L39,99@3437L102,102@3544L104,108@3677L46,109@3728L589:AndroidCompositionLocals.android.kt#itgzvw",
                    ComposeContext(
                        name = "ProvideAndroidCompositionLocals",
                        sourceFile = "AndroidCompositionLocals.android.kt",
                        packageHash = 1137893036,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 89, offset = 3008, length = 87),
                            SourceLocationInfo(lineNumber = 93, offset = 3137, length = 37),
                            SourceLocationInfo(lineNumber = 95, offset = 3197, length = 39),
                            SourceLocationInfo(lineNumber = 100, offset = 3437, length = 102),
                            SourceLocationInfo(lineNumber = 103, offset = 3544, length = 104),
                            SourceLocationInfo(lineNumber = 109, offset = 3677, length = 46),
                            SourceLocationInfo(lineNumber = 110, offset = 3728, length = 589)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(ProvideCommonCompositionLocals)P(1,2)194@6496L1205:CompositionLocals.kt#itgzvw",
                    ComposeContext(
                        name = "ProvideCommonCompositionLocals",
                        sourceFile = "CompositionLocals.kt",
                        packageHash = 1137893036,
                        locations = listOf(SourceLocationInfo(lineNumber = 195, offset = 6496, length = 1205)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(ProvideTextStyle)P(1)394@17586L7,395@17611L80:Text.kt#jmzs0o",
                    ComposeContext(
                        name = "ProvideTextStyle",
                        sourceFile = "Text.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 395, offset = 17586, length = 7),
                            SourceLocationInfo(lineNumber = 396, offset = 17611, length = 80)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(SaveableStateProvider)57@2261L38,64@2774L44:NavBackStackEntryProvider.kt#opm8kd",
                    ComposeContext(
                        name = "SaveableStateProvider",
                        sourceFile = "NavBackStackEntryProvider.kt",
                        packageHash = 1494216157,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 58, offset = 2261, length = 38),
                            SourceLocationInfo(lineNumber = 65, offset = 2774, length = 44)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(SaveableStateProvider)P(1)75@2967L923:SaveableStateHolder.kt#r2ddri",
                    ComposeContext(
                        name = "SaveableStateProvider",
                        sourceFile = "SaveableStateHolder.kt",
                        packageHash = 1636570350,
                        locations = listOf(SourceLocationInfo(lineNumber = 76, offset = 2967, length = 923)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(SaveableStateProvider)P(1)83@3415L35,84@3459L159:LazySaveableStateHolder.kt#wow0x6",
                    ComposeContext(
                        name = "SaveableStateProvider",
                        sourceFile = "LazySaveableStateHolder.kt",
                        packageHash = 1976722602,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 84, offset = 3415, length = 35),
                            SourceLocationInfo(lineNumber = 85, offset = 3459, length = 159)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(ScrollPositionUpdater):LazyList.kt#428nma",
                    ComposeContext(
                        name = "ScrollPositionUpdater",
                        sourceFile = "LazyList.kt",
                        packageHash = 245627794,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(SideEffect):Effects.kt#9igjgp",
                    ComposeContext(
                        name = "SideEffect",
                        sourceFile = "Effects.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(SimpleView)P(1)65@2538L1005:NavigationSample.kt#d7l2vq",
                    ComposeContext(
                        name = "SimpleView",
                        sourceFile = "NavigationSample.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 66, offset = 2538, length = 1005)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(SkippableItem)P(1,3:c#foundation.lazy.layout.StableValue!,2:c#foundation.lazy.layout.StableValue)133@4709L84:LazyLayoutItemContentFactory.kt#wow0x6",
                    ComposeContext(
                        name = "SkippableItem",
                        sourceFile = "LazyLayoutItemContentFactory.kt",
                        packageHash = 1976722602,
                        locations = listOf(SourceLocationInfo(lineNumber = 134, offset = 4709, length = 84)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(
                                sortedIndex = 3,
                                inlineClass = "androidx.compose.foundation.lazy.layout.StableValue"
                            ),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(SubcomposeLayout)P(1)77@3566L36,76@3532L144:SubcomposeLayout.kt#80mrfh",
                    ComposeContext(
                        name = "SubcomposeLayout",
                        sourceFile = "SubcomposeLayout.kt",
                        packageHash = 484791389,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 78, offset = 3566, length = 36),
                            SourceLocationInfo(lineNumber = 77, offset = 3532, length = 144)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(SubcomposeLayout)P(2,1)260@13431L80:SubcomposeLayout.kt#80mrfh",
                    ComposeContext(
                        name = "SubcomposeLayout",
                        sourceFile = "SubcomposeLayout.kt",
                        packageHash = 484791389,
                        locations = listOf(SourceLocationInfo(lineNumber = 261, offset = 13431, length = 80)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(SubcomposeLayout)P(3,2)319@16791L23,320@16844L28,323@17000L604,344@17744L27,345@17799L89,345@17776L112:SubcomposeLayout.kt#80mrfh",
                    ComposeContext(
                        name = "SubcomposeLayout",
                        sourceFile = "SubcomposeLayout.kt",
                        packageHash = 484791389,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 320, offset = 16791, length = 23),
                            SourceLocationInfo(lineNumber = 321, offset = 16844, length = 28),
                            SourceLocationInfo(lineNumber = 324, offset = 17000, length = 604),
                            SourceLocationInfo(lineNumber = 345, offset = 17744, length = 27),
                            SourceLocationInfo(lineNumber = 346, offset = 17799, length = 89),
                            SourceLocationInfo(lineNumber = 346, offset = 17776, length = 112)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Surface)P(5,6,1:c#ui.graphics.Color,3:c#ui.graphics.Color!1,4:c#ui.unit.Dp)107@5308L6,108@5350L22,*113@5525L7,114@5549L894:Surface.kt#jmzs0o",
                    ComposeContext(
                        name = "Surface",
                        sourceFile = "Surface.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 108, offset = 5308, length = 6),
                            SourceLocationInfo(lineNumber = 109, offset = 5350, length = 22),
                            SourceLocationInfo(lineNumber = 114, offset = 5525, length = 7),
                            SourceLocationInfo(lineNumber = 115, offset = 5549, length = 894)
                        ),
                        repeatOffset = 2,
                        parameters = listOf(
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 3, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Surface)P(8,7,5,9,1:c#ui.graphics.Color,3:c#ui.graphics.Color!1,4:c#ui.unit.Dp,6)216@10794L6,217@10836L22,220@10970L39,*223@11102L7,224@11126L982:Surface.kt#jmzs0o",
                    ComposeContext(
                        name = "Surface",
                        sourceFile = "Surface.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 217, offset = 10794, length = 6),
                            SourceLocationInfo(lineNumber = 218, offset = 10836, length = 22),
                            SourceLocationInfo(lineNumber = 221, offset = 10970, length = 39),
                            SourceLocationInfo(lineNumber = 224, offset = 11102, length = 7),
                            SourceLocationInfo(lineNumber = 225, offset = 11126, length = 982)
                        ),
                        repeatOffset = 3,
                        parameters = listOf(
                            Parameter(sortedIndex = 8, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 9, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 3, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 10, inlineClass = null),
                            Parameter(sortedIndex = 11, inlineClass = null),
                            Parameter(sortedIndex = 12, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Tab)P(5,4,3!1,7!2,6:c#ui.graphics.Color,8:c#ui.graphics.Color)96@4350L39,97@4443L7,98@4535L6,106@4792L234:Tab.kt#jmzs0o",
                    ComposeContext(
                        name = "Tab",
                        sourceFile = "Tab.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 97, offset = 4350, length = 39),
                            SourceLocationInfo(lineNumber = 98, offset = 4443, length = 7),
                            SourceLocationInfo(lineNumber = 99, offset = 4535, length = 6),
                            SourceLocationInfo(lineNumber = 107, offset = 4792, length = 234)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 8, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 9, inlineClass = null),
                            Parameter(sortedIndex = 10, inlineClass = null),
                            Parameter(sortedIndex = 11, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Tab)P(5,4,3,1,2,6:c#ui.graphics.Color,7:c#ui.graphics.Color)227@10083L39,228@10176L7,229@10268L6,235@10562L60,237@10628L618:Tab.kt#jmzs0o",
                    ComposeContext(
                        name = "Tab",
                        sourceFile = "Tab.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 228, offset = 10083, length = 39),
                            SourceLocationInfo(lineNumber = 229, offset = 10176, length = 7),
                            SourceLocationInfo(lineNumber = 230, offset = 10268, length = 6),
                            SourceLocationInfo(lineNumber = 236, offset = 10562, length = 60),
                            SourceLocationInfo(lineNumber = 238, offset = 10628, length = 618)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 7, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(TabBaselineLayout)P(1)304@12859L1909:Tab.kt#jmzs0o",
                    ComposeContext(
                        name = "TabBaselineLayout",
                        sourceFile = "Tab.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 305, offset = 12859, length = 1909)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(TabRow)P(5,4,0:c#ui.graphics.Color,1:c#ui.graphics.Color,3)131@6500L6,132@6549L32,145@7006L1504:TabRow.kt#jmzs0o",
                    ComposeContext(
                        name = "TabRow",
                        sourceFile = "TabRow.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 132, offset = 6500, length = 6),
                            SourceLocationInfo(lineNumber = 133, offset = 6549, length = 32),
                            SourceLocationInfo(lineNumber = 146, offset = 7006, length = 1504)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 1, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(TabTransition)P(0:c#ui.graphics.Color,2:c#ui.graphics.Color,3)268@11677L26,269@11732L550,287@12287L164:Tab.kt#jmzs0o",
                    ComposeContext(
                        name = "TabTransition",
                        sourceFile = "Tab.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 269, offset = 11677, length = 26),
                            SourceLocationInfo(lineNumber = 270, offset = 11732, length = 550),
                            SourceLocationInfo(lineNumber = 288, offset = 12287, length = 164)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(
                                sortedIndex = 0,
                                inlineClass = "androidx.compose.ui.graphics.Color"
                            ),
                            Parameter(sortedIndex = 2, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(Text)P(14,9,0:c#ui.graphics.Color,2:c#ui.unit.TextUnit,3:c#ui.text.font.FontStyle,4!1,5:c#ui.unit.TextUnit,16,15:c#ui.text.style.TextAlign,6:c#ui.unit.TextUnit,11:c#ui.text.style.TextOverflow,12)109@5711L7,128@6923L7,129@6977L7,138@7204L607:Text.kt#jmzs0o",
                    ComposeContext(
                        name = "Text",
                        sourceFile = "Text.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 110, offset = 5711, length = 7),
                            SourceLocationInfo(lineNumber = 129, offset = 6923, length = 7),
                            SourceLocationInfo(lineNumber = 130, offset = 6977, length = 7),
                            SourceLocationInfo(lineNumber = 139, offset = 7204, length = 607)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 14, inlineClass = null),
                            Parameter(sortedIndex = 9, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 2, inlineClass = "androidx.compose.ui.unit.TextUnit"),
                            Parameter(sortedIndex = 3, inlineClass = "androidx.compose.ui.text.font.FontStyle"),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = "androidx.compose.ui.unit.TextUnit"),
                            Parameter(sortedIndex = 16, inlineClass = null),
                            Parameter(sortedIndex = 15, inlineClass = "androidx.compose.ui.text.style.TextAlign"),
                            Parameter(sortedIndex = 6, inlineClass = "androidx.compose.ui.unit.TextUnit"),
                            Parameter(sortedIndex = 11, inlineClass = "androidx.compose.ui.text.style.TextOverflow"),
                            Parameter(sortedIndex = 12, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 8, inlineClass = null),
                            Parameter(sortedIndex = 10, inlineClass = null),
                            Parameter(sortedIndex = 13, inlineClass = null),
                            Parameter(sortedIndex = 17, inlineClass = null),
                            Parameter(sortedIndex = 18, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(TrackInteractionEffect)P(4,1,2)84@3605L7,86@3641L649:InteractionTracking.kt#ga31me",
                    ComposeContext(
                        name = "TrackInteractionEffect",
                        sourceFile = "InteractionTracking.kt",
                        packageHash = 984397046,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 85, offset = 3605, length = 7),
                            SourceLocationInfo(lineNumber = 87, offset = 3641, length = 649)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(ViewNavigation)103@3702L996:NavigationSample.kt#d7l2vq",
                    ComposeContext(
                        name = "ViewNavigation",
                        sourceFile = "NavigationSample.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 104, offset = 3702, length = 996)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(animateTo)427@16681L25,431@16951L655,431@16930L676:Transition.kt#pdpnli",
                    ComposeContext(
                        name = "animateTo",
                        sourceFile = "Transition.kt",
                        packageHash = 1534686390,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 428, offset = 16681, length = 25),
                            SourceLocationInfo(lineNumber = 432, offset = 16951, length = 655),
                            SourceLocationInfo(lineNumber = 432, offset = 16930, length = 676)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(backgroundColor)587@23484L79:Button.kt#jmzs0o",
                    ComposeContext(
                        name = "backgroundColor",
                        sourceFile = "Button.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 588, offset = 23484, length = 79)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(buttonColors)P(0:c#ui.graphics.Color,1:c#ui.graphics.Color,2:c#ui.graphics.Color,3:c#ui.graphics.Color)406@16865L6,407@16911L32,408@17000L6,409@17078L6,410@17147L6,411@17203L8:Button.kt#jmzs0o",
                    ComposeContext(
                        name = "buttonColors",
                        sourceFile = "Button.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 407, offset = 16865, length = 6),
                            SourceLocationInfo(lineNumber = 408, offset = 16911, length = 32),
                            SourceLocationInfo(lineNumber = 409, offset = 17000, length = 6),
                            SourceLocationInfo(lineNumber = 410, offset = 17078, length = 6),
                            SourceLocationInfo(lineNumber = 411, offset = 17147, length = 6),
                            SourceLocationInfo(lineNumber = 412, offset = 17203, length = 8)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(
                                sortedIndex = 0,
                                inlineClass = "androidx.compose.ui.graphics.Color"
                            ),
                            Parameter(sortedIndex = 1, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 2, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 3, inlineClass = "androidx.compose.ui.graphics.Color")
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(collectAsState)46@1741L30:SnapshotFlow.kt#9igjgp",
                    ComposeContext(
                        name = "collectAsState",
                        sourceFile = "SnapshotFlow.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 47, offset = 1741, length = 30)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(collectAsState)P(1)61@2283L186:SnapshotFlow.kt#9igjgp",
                    ComposeContext(
                        name = "collectAsState",
                        sourceFile = "SnapshotFlow.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 62, offset = 2283, length = 186)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(columnMeasurePolicy)P(1)102@4720L562:Column.kt#2w3rfo",
                    ComposeContext(
                        name = "columnMeasurePolicy",
                        sourceFile = "Column.kt",
                        packageHash = 174855588,
                        locations = listOf(SourceLocationInfo(lineNumber = 103, offset = 4720, length = 562)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(compositionLocalMapOf)P(1):CompositionLocalMap.kt#9igjgp",
                    ComposeContext(
                        name = "compositionLocalMapOf",
                        sourceFile = "CompositionLocalMap.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(contentAlpha)76@2623L7,77@2670L6:ContentAlpha.kt#jmzs0o",
                    ComposeContext(
                        name = "contentAlpha",
                        sourceFile = "ContentAlpha.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 77, offset = 2623, length = 7),
                            SourceLocationInfo(lineNumber = 78, offset = 2670, length = 6)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(contentColor)592@23666L73:Button.kt#jmzs0o",
                    ComposeContext(
                        name = "contentColor",
                        sourceFile = "Button.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 593, offset = 23666, length = 73)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(contentColorFor)P(0:c#ui.graphics.Color)*296@11462L6,296@11533L7:Colors.kt#jmzs0o",
                    ComposeContext(
                        name = "contentColorFor",
                        sourceFile = "Colors.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 297, offset = 11462, length = 6),
                            SourceLocationInfo(lineNumber = 297, offset = 11533, length = 7)
                        ),
                        repeatOffset = 0,
                        parameters = listOf(
                            Parameter(
                                sortedIndex = 0,
                                inlineClass = "androidx.compose.ui.graphics.Color"
                            ),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(createTransitionAnimation)P(1,3!1,4)873@34678L499,896@35561L128,896@35523L166:Transition.kt#pdpnli",
                    ComposeContext(
                        name = "createTransitionAnimation",
                        sourceFile = "Transition.kt",
                        packageHash = 1534686390,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 874, offset = 34678, length = 499),
                            SourceLocationInfo(lineNumber = 897, offset = 35561, length = 128),
                            SourceLocationInfo(lineNumber = 897, offset = 35523, length = 166)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(defaultColor)128@5172L7,129@5220L6:MaterialTheme.kt#jmzs0o",
                    ComposeContext(
                        name = "defaultColor",
                        sourceFile = "MaterialTheme.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 129, offset = 5172, length = 7),
                            SourceLocationInfo(lineNumber = 130, offset = 5220, length = 6)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(elevation)506@20624L46,507@20713L1077,507@20679L1111,548@22239L51:Button.kt#jmzs0o",
                    ComposeContext(
                        name = "elevation",
                        sourceFile = "Button.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 507, offset = 20624, length = 46),
                            SourceLocationInfo(lineNumber = 508, offset = 20713, length = 1077),
                            SourceLocationInfo(lineNumber = 508, offset = 20679, length = 1111),
                            SourceLocationInfo(lineNumber = 549, offset = 22239, length = 51)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(elevation)P(0:c#ui.unit.Dp,4:c#ui.unit.Dp,1:c#ui.unit.Dp,3:c#ui.unit.Dp,2:c#ui.unit.Dp)378@15799L497:Button.kt#jmzs0o",
                    ComposeContext(
                        name = "elevation",
                        sourceFile = "Button.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 379, offset = 15799, length = 497)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 0, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 4, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 1, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 3, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 2, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(findNearestViewGroup)105@4003L7:Ripple.android.kt#vhb33q",
                    ComposeContext(
                        name = "findNearestViewGroup",
                        sourceFile = "Ripple.android.kt",
                        packageHash = 1903522166,
                        locations = listOf(SourceLocationInfo(lineNumber = 106, offset = 4003, length = 7)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(flingBehavior)194@8766L33,195@8815L75:Scrollable.kt#8bwon0",
                    ComposeContext(
                        name = "flingBehavior",
                        sourceFile = "Scrollable.kt",
                        packageHash = 503730108,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 195, offset = 8766, length = 33),
                            SourceLocationInfo(lineNumber = 196, offset = 8815, length = 75)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(flingBehavior)P(4!1,3,2,1:c#ui.unit.Dp)",
                    ComposeContext(
                        name = "flingBehavior",
                        sourceFile = null,
                        packageHash = -1,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(lazyLayoutSemantics)P(!1,3!1,4)47@1936L24,49@1991L3667:LazyLayoutSemantics.kt#wow0x6",
                    ComposeContext(
                        name = "lazyLayoutSemantics",
                        sourceFile = "LazyLayoutSemantics.kt",
                        packageHash = 1976722602,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 48, offset = 1936, length = 24),
                            SourceLocationInfo(lineNumber = 50, offset = 1991, length = 3667)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(lazyListBeyondBoundsModifier)P(3!1,2)38@1484L7,39@1520L110,43@1702L340:LazyListBeyondBoundsModifier.kt#428nma",
                    ComposeContext(
                        name = "lazyListBeyondBoundsModifier",
                        sourceFile = "LazyListBeyondBoundsModifier.kt",
                        packageHash = 245627794,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 39, offset = 1484, length = 7),
                            SourceLocationInfo(lineNumber = 40, offset = 1520, length = 110),
                            SourceLocationInfo(lineNumber = 44, offset = 1702, length = 340)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(obtainImageVectorCache)P(1)132@4482L31,133@4560L88,136@4669L557,153@5231L224:AndroidCompositionLocals.android.kt#itgzvw",
                    ComposeContext(
                        name = "obtainImageVectorCache",
                        sourceFile = "AndroidCompositionLocals.android.kt",
                        packageHash = 1137893036,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 133, offset = 4482, length = 31),
                            SourceLocationInfo(lineNumber = 134, offset = 4560, length = 88),
                            SourceLocationInfo(lineNumber = 137, offset = 4669, length = 557),
                            SourceLocationInfo(lineNumber = 154, offset = 5231, length = 224)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(overscrollEffect)207@9135L26:Scrollable.kt#8bwon0",
                    ComposeContext(
                        name = "overscrollEffect",
                        sourceFile = "Scrollable.kt",
                        packageHash = 503730108,
                        locations = listOf(SourceLocationInfo(lineNumber = 208, offset = 9135, length = 26)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(platformScrollConfig):AndroidScrollable.android.kt#8bwon0",
                    ComposeContext(
                        name = "platformScrollConfig",
                        sourceFile = "AndroidScrollable.android.kt",
                        packageHash = 503730108,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(pointerScrollable)P(3,4,6!1,2,5)257@10957L53,258@11033L224,268@11291L88,271@11405L46,272@11475L22,281@11777L47,283@11901L176:Scrollable.kt#8bwon0",
                    ComposeContext(
                        name = "pointerScrollable",
                        sourceFile = "Scrollable.kt",
                        packageHash = 503730108,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 258, offset = 10957, length = 53),
                            SourceLocationInfo(lineNumber = 259, offset = 11033, length = 224),
                            SourceLocationInfo(lineNumber = 269, offset = 11291, length = 88),
                            SourceLocationInfo(lineNumber = 272, offset = 11405, length = 46),
                            SourceLocationInfo(lineNumber = 273, offset = 11475, length = 22),
                            SourceLocationInfo(lineNumber = 282, offset = 11777, length = 47),
                            SourceLocationInfo(lineNumber = 284, offset = 11901, length = 176)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(produceState)147@5571L41,148@5617L101:ProduceState.kt#9igjgp",
                    ComposeContext(
                        name = "produceState",
                        sourceFile = "ProduceState.kt",
                        packageHash = 575200393,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 148, offset = 5571, length = 41),
                            SourceLocationInfo(lineNumber = 149, offset = 5617, length = 101)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(provided)*125@5325L42:CompositionLocal.kt#9igjgp",
                    ComposeContext(
                        name = "provided",
                        sourceFile = "CompositionLocal.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 126, offset = 5325, length = 42)),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(provided):CompositionLocal.kt#9igjgp",
                    ComposeContext(
                        name = "provided",
                        sourceFile = "CompositionLocal.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(remember):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "remember",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(remember)P(1):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "remember",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(remember)P(1,2):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "remember",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(remember)P(1,2,3):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "remember",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberBoxMeasurePolicy)85@3660L113:Box.kt#2w3rfo",
                    ComposeContext(
                        name = "rememberBoxMeasurePolicy",
                        sourceFile = "Box.kt",
                        packageHash = 174855588,
                        locations = listOf(SourceLocationInfo(lineNumber = 86, offset = 3660, length = 113)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberCompositionContext):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "rememberCompositionContext",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberLazyListItemProviderLambda)P(1)44@1787L29,45@1828L679:LazyListItemProvider.kt#428nma",
                    ComposeContext(
                        name = "rememberLazyListItemProviderLambda",
                        sourceFile = "LazyListItemProvider.kt",
                        packageHash = 245627794,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 45, offset = 1787, length = 29),
                            SourceLocationInfo(lineNumber = 46, offset = 1828, length = 679)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberLazyListMeasurePolicy)P(5,7,1,6,4!2,8)173@7248L7052:LazyList.kt#428nma",
                    ComposeContext(
                        name = "rememberLazyListMeasurePolicy",
                        sourceFile = "LazyList.kt",
                        packageHash = 245627794,
                        locations = listOf(SourceLocationInfo(lineNumber = 174, offset = 7248, length = 7052)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 8, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 9, inlineClass = null),
                            Parameter(sortedIndex = 10, inlineClass = null),
                            Parameter(sortedIndex = 11, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberLazyListSemanticState)P(1)27@961L107:LazyListSemantics.kt#428nma",
                    ComposeContext(
                        name = "rememberLazyListSemanticState",
                        sourceFile = "LazyListSemantics.kt",
                        packageHash = 245627794,
                        locations = listOf(SourceLocationInfo(lineNumber = 28, offset = 961, length = 107)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberLazyListSnapperLayoutInfo)P(1,2,0:c#ui.unit.Dp)",
                    ComposeContext(
                        name = "rememberLazyListSnapperLayoutInfo",
                        sourceFile = null,
                        packageHash = -1,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberLazyListState)61@2758L130,61@2712L176:LazyListState.kt#428nma",
                    ComposeContext(
                        name = "rememberLazyListState",
                        sourceFile = "LazyListState.kt",
                        packageHash = 245627794,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 62, offset = 2758, length = 130),
                            SourceLocationInfo(lineNumber = 62, offset = 2712, length = 176)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberNavController)58@2312L7,*59@2331L119:NavHostController.kt#opm8kd",
                    ComposeContext(
                        name = "rememberNavController",
                        sourceFile = "NavHostController.kt",
                        packageHash = 1494216157,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 59, offset = 2312, length = 7),
                            SourceLocationInfo(lineNumber = 60, offset = 2331, length = 119)
                        ),
                        repeatOffset = 1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberOverscrollEffect)63@2804L7,64@2858L7,66@2907L80:AndroidOverscroll.kt#71ulvw",
                    ComposeContext(
                        name = "rememberOverscrollEffect",
                        sourceFile = "AndroidOverscroll.kt",
                        packageHash = 426370892,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 64, offset = 2804, length = 7),
                            SourceLocationInfo(lineNumber = 65, offset = 2858, length = 7),
                            SourceLocationInfo(lineNumber = 67, offset = 2907, length = 80)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberPagerState)",
                    ComposeContext(
                        name = "rememberPagerState",
                        sourceFile = null,
                        packageHash = -1,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberRipple)P(!1,2:c#ui.unit.Dp,1:c#ui.graphics.Color)81@3890L27,82@3929L85:Ripple.kt#vhb33q",
                    ComposeContext(
                        name = "rememberRipple",
                        sourceFile = "Ripple.kt",
                        packageHash = 1903522166,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 82, offset = 3890, length = 27),
                            SourceLocationInfo(lineNumber = 83, offset = 3929, length = 85)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 1, inlineClass = "androidx.compose.ui.graphics.Color"),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberSaveable)P(1,3,2)80@3500L7,82@3597L244,95@4209L27,96@4262L27,98@4299L441:RememberSaveable.kt#r2ddri",
                    ComposeContext(
                        name = "rememberSaveable",
                        sourceFile = "RememberSaveable.kt",
                        packageHash = 1636570350,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 81, offset = 3500, length = 7),
                            SourceLocationInfo(lineNumber = 83, offset = 3597, length = 244),
                            SourceLocationInfo(lineNumber = 96, offset = 4209, length = 27),
                            SourceLocationInfo(lineNumber = 97, offset = 4262, length = 27),
                            SourceLocationInfo(lineNumber = 99, offset = 4299, length = 441)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberSaveableStateHolder)*59@2369L111,64@2554L7:SaveableStateHolder.kt#r2ddri",
                    ComposeContext(
                        name = "rememberSaveableStateHolder",
                        sourceFile = "SaveableStateHolder.kt",
                        packageHash = 1636570350,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 60, offset = 2369, length = 111),
                            SourceLocationInfo(lineNumber = 65, offset = 2554, length = 7)
                        ),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberSnapperFlingBehavior)P(1!1,3)",
                    ComposeContext(
                        name = "rememberSnapperFlingBehavior",
                        sourceFile = null,
                        packageHash = -1,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberSnapperFlingBehavior)P(2,4,1:c#ui.unit.Dp!1,5)",
                    ComposeContext(
                        name = "rememberSnapperFlingBehavior",
                        sourceFile = null,
                        packageHash = -1,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberSplineBasedDecay)43@1746L7,44@1765L114:SplineBasedFloatDecayAnimationSpec.android.kt#xbi5r1",
                    ComposeContext(
                        name = "rememberSplineBasedDecay",
                        sourceFile = "SplineBasedFloatDecayAnimationSpec.android.kt",
                        packageHash = 2014706845,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 44, offset = 1746, length = 7),
                            SourceLocationInfo(lineNumber = 45, offset = 1765, length = 114)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberSwipeableState)P(2)472@19232L344:Swipeable.kt#jmzs0o",
                    ComposeContext(
                        name = "rememberSwipeableState",
                        sourceFile = "Swipeable.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 473, offset = 19232, length = 344)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberTextSelectionColors)45@1902L6,47@1930L384:MaterialTextSelectionColors.kt#jmzs0o",
                    ComposeContext(
                        name = "rememberTextSelectionColors",
                        sourceFile = "MaterialTextSelectionColors.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 46, offset = 1902, length = 6),
                            SourceLocationInfo(lineNumber = 48, offset = 1930, length = 384)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberUpdatedInstance)116@5361L7,117@5389L174,124@5617L13,124@5590L41,126@5656L155,134@5821L535:Ripple.kt#vhb33q",
                    ComposeContext(
                        name = "rememberUpdatedInstance",
                        sourceFile = "Ripple.kt",
                        packageHash = 1903522166,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 117, offset = 5361, length = 7),
                            SourceLocationInfo(lineNumber = 118, offset = 5389, length = 174),
                            SourceLocationInfo(lineNumber = 125, offset = 5617, length = 13),
                            SourceLocationInfo(lineNumber = 125, offset = 5590, length = 41),
                            SourceLocationInfo(lineNumber = 127, offset = 5656, length = 155),
                            SourceLocationInfo(lineNumber = 135, offset = 5821, length = 535)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberUpdatedRippleInstance)P(2!1,3:c#ui.unit.Dp)64@2484L22,90@3354L160:Ripple.android.kt#vhb33q",
                    ComposeContext(
                        name = "rememberUpdatedRippleInstance",
                        sourceFile = "Ripple.android.kt",
                        packageHash = 1903522166,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 65, offset = 2484, length = 22),
                            SourceLocationInfo(lineNumber = 91, offset = 3354, length = 160)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberUpdatedState)*303@10198L41:SnapshotState.kt#9igjgp",
                    ComposeContext(
                        name = "rememberUpdatedState",
                        sourceFile = "SnapshotState.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 304, offset = 10198, length = 41)),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rememberVisibleList)104@4095L7,105@4114L423:DialogHost.kt#opm8kd",
                    ComposeContext(
                        name = "rememberVisibleList",
                        sourceFile = "DialogHost.kt",
                        packageHash = 1494216157,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 105, offset = 4095, length = 7),
                            SourceLocationInfo(lineNumber = 106, offset = 4114, length = 423)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rippleAlpha)134@5368L7,135@5412L6:MaterialTheme.kt#jmzs0o",
                    ComposeContext(
                        name = "rippleAlpha",
                        sourceFile = "MaterialTheme.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 135, offset = 5368, length = 7),
                            SourceLocationInfo(lineNumber = 136, offset = 5412, length = 6)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(rowMeasurePolicy)106@4837L639:Row.kt#2w3rfo",
                    ComposeContext(
                        name = "rowMeasurePolicy",
                        sourceFile = "Row.kt",
                        packageHash = 174855588,
                        locations = listOf(SourceLocationInfo(lineNumber = 107, offset = 4837, length = 639)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(surfaceColorAtElevation)P(1:c#ui.graphics.Color,2,0:c#ui.unit.Dp)635@31093L6,636@31164L31:Surface.kt#jmzs0o",
                    ComposeContext(
                        name = "surfaceColorAtElevation",
                        sourceFile = "Surface.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 636, offset = 31093, length = 6),
                            SourceLocationInfo(lineNumber = 637, offset = 31164, length = 31)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(
                                sortedIndex = 1,
                                inlineClass = "androidx.compose.ui.graphics.Color"
                            ),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = "androidx.compose.ui.unit.Dp"),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(trackClick)P(3!1,2)50@2138L23,54@2270L40,55@2322L132:InteractionTracking.kt#ga31me",
                    ComposeContext(
                        name = "trackClick",
                        sourceFile = "InteractionTracking.kt",
                        packageHash = 984397046,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 51, offset = 2138, length = 23),
                            SourceLocationInfo(lineNumber = 55, offset = 2270, length = 40),
                            SourceLocationInfo(lineNumber = 56, offset = 2322, length = 132)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(updateTarget):Transition.kt#pdpnli",
                    ComposeContext(
                        name = "updateTarget",
                        sourceFile = "Transition.kt",
                        packageHash = 1534686390,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(updateTransition)P(1)71@2945L51,72@3012L22,73@3068L195,73@3039L224:Transition.kt#pdpnli",
                    ComposeContext(
                        name = "updateTransition",
                        sourceFile = "Transition.kt",
                        packageHash = 1534686390,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 72, offset = 2945, length = 51),
                            SourceLocationInfo(lineNumber = 73, offset = 3012, length = 22),
                            SourceLocationInfo(lineNumber = 74, offset = 3068, length = 195),
                            SourceLocationInfo(lineNumber = 74, offset = 3039, length = 224)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C(viewModel)P(3,4,2,1)*145@6612L7:ViewModel.kt#3tja67",
                    ComposeContext(
                        name = "viewModel",
                        sourceFile = "ViewModel.kt",
                        packageHash = 231007039,
                        locations = listOf(SourceLocationInfo(lineNumber = 146, offset = 6612, length = 7)),
                        repeatOffset = 0,
                        parameters = listOf(
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 4, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 5, inlineClass = null),
                            Parameter(sortedIndex = 6, inlineClass = null),
                            Parameter(sortedIndex = 7, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C*59@2538L28,59@2501L66:InteractionSample.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "InteractionSample.kt",
                        packageHash = 798801110,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 60, offset = 2538, length = 28),
                            SourceLocationInfo(lineNumber = 60, offset = 2501, length = 66)
                        ),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C*78@3104L27:LazyListItemProvider.kt#428nma",
                    ComposeContext(
                        name = null,
                        sourceFile = "LazyListItemProvider.kt",
                        packageHash = 245627794,
                        locations = listOf(SourceLocationInfo(lineNumber = 79, offset = 3104, length = 27)),
                        repeatOffset = 0,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C100@3751L184:LazyLayoutItemContentFactory.kt#wow0x6",
                    ComposeContext(
                        name = null,
                        sourceFile = "LazyLayoutItemContentFactory.kt",
                        packageHash = 1976722602,
                        locations = listOf(SourceLocationInfo(lineNumber = 101, offset = 3751, length = 184)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C102@4462L7:MaterialTheme.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "MaterialTheme.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 103, offset = 4462, length = 7)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C102@4667L10,103@4732L39:Tab.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Tab.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 103, offset = 4667, length = 10),
                            SourceLocationInfo(lineNumber = 104, offset = 4732, length = 39)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C108@3875L130:NavigationSample.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "NavigationSample.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 109, offset = 3875, length = 130)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C108@4179L274:InteractionSample.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "InteractionSample.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 109, offset = 4179, length = 274)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C109@4825L42,110@4876L71:Indication.kt#71ulvw",
                    ComposeContext(
                        name = null,
                        sourceFile = "Indication.kt",
                        packageHash = 426370892,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 110, offset = 4825, length = 42),
                            SourceLocationInfo(lineNumber = 111, offset = 4876, length = 71)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C112@4763L7:MaterialTheme.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "MaterialTheme.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 113, offset = 4763, length = 7)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C115@4971L49:Tab.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Tab.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 116, offset = 4971, length = 49)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C117@5509L683:Button.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Button.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 118, offset = 5509, length = 683)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C118@4176L135:AndroidCompositionLocals.android.kt#itgzvw",
                    ComposeContext(
                        name = null,
                        sourceFile = "AndroidCompositionLocals.android.kt",
                        packageHash = 1137893036,
                        locations = listOf(SourceLocationInfo(lineNumber = 119, offset = 4176, length = 135)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C119@5651L10,118@5595L587:Button.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Button.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 120, offset = 5651, length = 10),
                            SourceLocationInfo(lineNumber = 119, offset = 5595, length = 587)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C120@4971L7:MaterialTheme.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "MaterialTheme.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 121, offset = 4971, length = 7)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C121@5701L467:Button.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Button.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 122, offset = 5701, length = 467)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C124@5963L7,122@5834L221,118@5698L739:Surface.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Surface.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 125, offset = 5963, length = 7),
                            SourceLocationInfo(lineNumber = 123, offset = 5834, length = 221),
                            SourceLocationInfo(lineNumber = 119, offset = 5698, length = 739)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C128@5454L128,131@5626L22,131@5599L115:Crossfade.kt#xbi5r1",
                    ComposeContext(
                        name = null,
                        sourceFile = "Crossfade.kt",
                        packageHash = 2014706845,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 129, offset = 5454, length = 128),
                            SourceLocationInfo(lineNumber = 132, offset = 5626, length = 22),
                            SourceLocationInfo(lineNumber = 132, offset = 5599, length = 115)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C132@5672L24:Crossfade.kt#xbi5r1",
                    ComposeContext(
                        name = null,
                        sourceFile = "Crossfade.kt",
                        packageHash = 2014706845,
                        locations = listOf(SourceLocationInfo(lineNumber = 133, offset = 5672, length = 24)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C134@4765L22:LazyLayoutItemContentFactory.kt#wow0x6",
                    ComposeContext(
                        name = null,
                        sourceFile = "LazyLayoutItemContentFactory.kt",
                        packageHash = 1976722602,
                        locations = listOf(SourceLocationInfo(lineNumber = 135, offset = 4765, length = 22)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C137@6418L9:Surface.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Surface.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 138, offset = 6418, length = 9)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C141@6930L9:TabRow.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "TabRow.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 142, offset = 6930, length = 9)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C145@6530L22:LazyDsl.kt#428nma",
                    ComposeContext(
                        name = null,
                        sourceFile = "LazyDsl.kt",
                        packageHash = 245627794,
                        locations = listOf(SourceLocationInfo(lineNumber = 146, offset = 6530, length = 22)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C146@5671L7,155@6062L600,155@6039L623,171@6687L147:NavHost.kt#opm8kd",
                    ComposeContext(
                        name = null,
                        sourceFile = "NavHost.kt",
                        packageHash = 1494216157,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 147, offset = 5671, length = 7),
                            SourceLocationInfo(lineNumber = 156, offset = 6062, length = 600),
                            SourceLocationInfo(lineNumber = 156, offset = 6039, length = 623),
                            SourceLocationInfo(lineNumber = 172, offset = 6687, length = 147)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C150@7189L1315,150@7147L1357:TabRow.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "TabRow.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 151, offset = 7189, length = 1315),
                            SourceLocationInfo(lineNumber = 151, offset = 7147, length = 1357)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C154@6099L56,156@6181L176:Wrapper.android.kt#itgzvw",
                    ComposeContext(
                        name = null,
                        sourceFile = "Wrapper.android.kt",
                        packageHash = 1137893036,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 155, offset = 6099, length = 56),
                            SourceLocationInfo(lineNumber = 157, offset = 6181, length = 176)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C157@6284L47:Wrapper.android.kt#itgzvw",
                    ComposeContext(
                        name = null,
                        sourceFile = "Wrapper.android.kt",
                        packageHash = 1137893036,
                        locations = listOf(SourceLocationInfo(lineNumber = 158, offset = 6284, length = 47)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C162@7780L24,164@7862L170,171@8149L242:Scrollable.kt#8bwon0",
                    ComposeContext(
                        name = null,
                        sourceFile = "Scrollable.kt",
                        packageHash = 503730108,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 163, offset = 7780, length = 24),
                            SourceLocationInfo(lineNumber = 165, offset = 7862, length = 170),
                            SourceLocationInfo(lineNumber = 172, offset = 8149, length = 242)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C172@6802L18:NavHost.kt#opm8kd",
                    ComposeContext(
                        name = null,
                        sourceFile = "NavHost.kt",
                        packageHash = 1494216157,
                        locations = listOf(SourceLocationInfo(lineNumber = 173, offset = 6802, length = 18)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C176@8314L23:TabRow.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "TabRow.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 177, offset = 8314, length = 23)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C1995@73816L42:Composer.kt#9igjgp",
                    ComposeContext(
                        name = null,
                        sourceFile = "Composer.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 1996, offset = 73816, length = 42)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C202@7952L23:Layout.kt#80mrfh",
                    ComposeContext(
                        name = null,
                        sourceFile = "Layout.kt",
                        packageHash = 484791389,
                        locations = listOf(SourceLocationInfo(lineNumber = 203, offset = 7952, length = 23)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C225@8792L23:Layout.kt#80mrfh",
                    ComposeContext(
                        name = null,
                        sourceFile = "Layout.kt",
                        packageHash = 484791389,
                        locations = listOf(SourceLocationInfo(lineNumber = 226, offset = 8792, length = 23)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C235@11591L7,233@11462L221,243@11902L16,228@11275L827:Surface.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Surface.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 236, offset = 11591, length = 7),
                            SourceLocationInfo(lineNumber = 234, offset = 11462, length = 221),
                            SourceLocationInfo(lineNumber = 244, offset = 11902, length = 16),
                            SourceLocationInfo(lineNumber = 229, offset = 11275, length = 827)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C238@10708L532:Tab.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Tab.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 239, offset = 10708, length = 532)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C249@12083L9:Surface.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Surface.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 250, offset = 12083, length = 9)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C251@10602L9:ComposeView.android.kt#itgzvw",
                    ComposeContext(
                        name = null,
                        sourceFile = "ComposeView.android.kt",
                        packageHash = 1137893036,
                        locations = listOf(SourceLocationInfo(lineNumber = 252, offset = 10602, length = 9)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C309@13040L6:Tab.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Tab.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 310, offset = 13040, length = 6)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C34@1107L146:ContentAlpha.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "ContentAlpha.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 35, offset = 1107, length = 146)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C40@1555L7,41@1588L7:LocalViewModelStoreOwner.kt#3tja67",
                    ComposeContext(
                        name = null,
                        sourceFile = "LocalViewModelStoreOwner.kt",
                        packageHash = 231007039,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 41, offset = 1555, length = 7),
                            SourceLocationInfo(lineNumber = 42, offset = 1588, length = 7)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C45@1458L150:ContentAlpha.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "ContentAlpha.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 46, offset = 1458, length = 150)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C46@1846L3229:JetpackComposeActivity.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "JetpackComposeActivity.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 47, offset = 1846, length = 3229)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C47@1879L3182:JetpackComposeActivity.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "JetpackComposeActivity.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 48, offset = 1879, length = 3182)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C48@1920L99,52@2058L20,54@2141L7,55@2169L828,73@3019L464,83@3505L1179,109@4706L337:JetpackComposeActivity.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "JetpackComposeActivity.kt",
                        packageHash = 798801110,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 49, offset = 1920, length = 99),
                            SourceLocationInfo(lineNumber = 53, offset = 2058, length = 20),
                            SourceLocationInfo(lineNumber = 55, offset = 2141, length = 7),
                            SourceLocationInfo(lineNumber = 56, offset = 2169, length = 828),
                            SourceLocationInfo(lineNumber = 74, offset = 3019, length = 464),
                            SourceLocationInfo(lineNumber = 84, offset = 3505, length = 1179),
                            SourceLocationInfo(lineNumber = 110, offset = 4706, length = 337)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C49@2393L29,50@2431L15:LazySaveableStateHolder.kt#wow0x6",
                    ComposeContext(
                        name = null,
                        sourceFile = "LazySaveableStateHolder.kt",
                        packageHash = 1976722602,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 50, offset = 2393, length = 29),
                            SourceLocationInfo(lineNumber = 51, offset = 2431, length = 15)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C50@2094L7,*52@2246L7:BackHandler.kt#q1dkbc",
                    ComposeContext(
                        name = null,
                        sourceFile = "BackHandler.kt",
                        packageHash = 1574433048,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 51, offset = 2094, length = 7),
                            SourceLocationInfo(lineNumber = 53, offset = 2246, length = 7)
                        ),
                        repeatOffset = 1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C51@2100L30:NavBackStackEntryProvider.kt#opm8kd",
                    ComposeContext(
                        name = null,
                        sourceFile = "NavBackStackEntryProvider.kt",
                        packageHash = 1494216157,
                        locations = listOf(SourceLocationInfo(lineNumber = 52, offset = 2100, length = 30)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C55@2521L7:InteractiveComponentSize.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "InteractiveComponentSize.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 56, offset = 2521, length = 7)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C56@1805L154:ContentAlpha.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "ContentAlpha.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 57, offset = 1805, length = 154)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C592@24833L7,594@24875L502,615@25638L55:Swipeable.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Swipeable.kt",
                        packageHash = 1187478168,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 593, offset = 24833, length = 7),
                            SourceLocationInfo(lineNumber = 595, offset = 24875, length = 502),
                            SourceLocationInfo(lineNumber = 616, offset = 25638, length = 55)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C61@2415L114,64@2566L101,78@2956L392,75@2869L489:LazyLayout.kt#wow0x6",
                    ComposeContext(
                        name = null,
                        sourceFile = "LazyLayout.kt",
                        packageHash = 1976722602,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 62, offset = 2415, length = 114),
                            SourceLocationInfo(lineNumber = 65, offset = 2566, length = 101),
                            SourceLocationInfo(lineNumber = 79, offset = 2956, length = 392),
                            SourceLocationInfo(lineNumber = 76, offset = 2869, length = 489)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C701@32613L46:SubcomposeLayout.kt#80mrfh",
                    ComposeContext(
                        name = null,
                        sourceFile = "SubcomposeLayout.kt",
                        packageHash = 484791389,
                        locations = listOf(SourceLocationInfo(lineNumber = 702, offset = 32613, length = 46)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C70@2714L156,78@2965L23,76@2893L105,80@3007L530:NavigationSample.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "NavigationSample.kt",
                        packageHash = 798801110,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 71, offset = 2714, length = 156),
                            SourceLocationInfo(lineNumber = 79, offset = 2965, length = 23),
                            SourceLocationInfo(lineNumber = 77, offset = 2893, length = 105),
                            SourceLocationInfo(lineNumber = 81, offset = 3007, length = 530)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C71@3331L9:Box.kt#2w3rfo",
                    ComposeContext(
                        name = null,
                        sourceFile = "Box.kt",
                        packageHash = 174855588,
                        locations = listOf(SourceLocationInfo(lineNumber = 72, offset = 3331, length = 9)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C72@3384L9:Box.kt#2w3rfo",
                    ComposeContext(
                        name = null,
                        sourceFile = "Box.kt",
                        packageHash = 174855588,
                        locations = listOf(SourceLocationInfo(lineNumber = 73, offset = 3384, length = 9)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C76@3023L321,83@3357L150,87@3520L360:SaveableStateHolder.kt#r2ddri",
                    ComposeContext(
                        name = null,
                        sourceFile = "SaveableStateHolder.kt",
                        packageHash = 1636570350,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 77, offset = 3023, length = 321),
                            SourceLocationInfo(lineNumber = 84, offset = 3357, length = 150),
                            SourceLocationInfo(lineNumber = 88, offset = 3520, length = 360)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C77@3893L9:Column.kt#2w3rfo",
                    ComposeContext(
                        name = null,
                        sourceFile = "Column.kt",
                        packageHash = 174855588,
                        locations = listOf(SourceLocationInfo(lineNumber = 78, offset = 3893, length = 9)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C78@3887L9:Row.kt#2w3rfo",
                    ComposeContext(
                        name = null,
                        sourceFile = "Row.kt",
                        packageHash = 174855588,
                        locations = listOf(SourceLocationInfo(lineNumber = 79, offset = 3887, length = 9)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C80@3849L97:MaterialTheme.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "MaterialTheme.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 81, offset = 3849, length = 97)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C81@3061L466:NavigationSample.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "NavigationSample.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 82, offset = 3061, length = 466)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C81@3906L30:MaterialTheme.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "MaterialTheme.kt",
                        packageHash = 1187478168,
                        locations = listOf(SourceLocationInfo(lineNumber = 82, offset = 3906, length = 30)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C86@3235L278:NavigationSample.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "NavigationSample.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 87, offset = 3235, length = 278)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C86@3677L325:JetpackComposeActivity.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "JetpackComposeActivity.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 87, offset = 3677, length = 325)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C95@4098L24,*97@4213L423:JetpackComposeActivity.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "JetpackComposeActivity.kt",
                        packageHash = 798801110,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 96, offset = 4098, length = 24),
                            SourceLocationInfo(lineNumber = 98, offset = 4213, length = 423)
                        ),
                        repeatOffset = 1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C98@4259L15:JetpackComposeActivity.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "JetpackComposeActivity.kt",
                        packageHash = 798801110,
                        locations = listOf(SourceLocationInfo(lineNumber = 99, offset = 4259, length = 15)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C99@3691L258,107@3962L219:LazyLayoutItemContentFactory.kt#wow0x6",
                    ComposeContext(
                        name = null,
                        sourceFile = "LazyLayoutItemContentFactory.kt",
                        packageHash = 1976722602,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 100, offset = 3691, length = 258),
                            SourceLocationInfo(lineNumber = 108, offset = 3962, length = 219)
                        ),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C:Box.kt#2w3rfo",
                    ComposeContext(
                        name = null,
                        sourceFile = "Box.kt",
                        packageHash = 174855588,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C:CompositionLocal.kt#9igjgp",
                    ComposeContext(
                        name = null,
                        sourceFile = "CompositionLocal.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C:Crossfade.kt#xbi5r1",
                    ComposeContext(
                        name = null,
                        sourceFile = "Crossfade.kt",
                        packageHash = 2014706845,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C:JetpackComposeActivity.kt#d7l2vq",
                    ComposeContext(
                        name = null,
                        sourceFile = "JetpackComposeActivity.kt",
                        packageHash = 798801110,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "C:Tab.kt#jmzs0o",
                    ComposeContext(
                        name = null,
                        sourceFile = "Tab.kt",
                        packageHash = 1187478168,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = false
                    )
                ),
                Arguments.of(
                    "CC(Box)P(2,1,3)69@3214L67,70@3286L130:Box.kt#2w3rfo",
                    ComposeContext(
                        name = "Box",
                        sourceFile = "Box.kt",
                        packageHash = 174855588,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 70, offset = 3214, length = 67),
                            SourceLocationInfo(lineNumber = 71, offset = 3286, length = 130)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(Column)P(2,3,1)75@3779L61,76@3845L133:Column.kt#2w3rfo",
                    ComposeContext(
                        name = "Column",
                        sourceFile = "Column.kt",
                        packageHash = 174855588,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 76, offset = 3779, length = 61),
                            SourceLocationInfo(lineNumber = 77, offset = 3845, length = 133)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(ComposeNode):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "ComposeNode",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(Layout)P(!1,2)77@3132L23,79@3222L420:Layout.kt#80mrfh",
                    ComposeContext(
                        name = "Layout",
                        sourceFile = "Layout.kt",
                        packageHash = 484791389,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 78, offset = 3132, length = 23),
                            SourceLocationInfo(lineNumber = 80, offset = 3222, length = 420)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(Layout)P(1)122@4734L23,125@4885L385:Layout.kt#80mrfh",
                    ComposeContext(
                        name = "Layout",
                        sourceFile = "Layout.kt",
                        packageHash = 484791389,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 123, offset = 4734, length = 23),
                            SourceLocationInfo(lineNumber = 126, offset = 4885, length = 385)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(ReusableComposeNode):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "ReusableComposeNode",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(ReusableContent)P(1)145@5313L9:Composables.kt#9igjgp",
                    ComposeContext(
                        name = "ReusableContent",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 146, offset = 5313, length = 9)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(Row)P(2,1,3)76@3779L58,77@3842L130:Row.kt#2w3rfo",
                    ComposeContext(
                        name = "Row",
                        sourceFile = "Row.kt",
                        packageHash = 174855588,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 77, offset = 3779, length = 58),
                            SourceLocationInfo(lineNumber = 78, offset = 3842, length = 130)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(animateColor)P(2)68@3220L31,69@3287L70,73@3370L70:Transition.kt#xbi5r1",
                    ComposeContext(
                        name = "animateColor",
                        sourceFile = "Transition.kt",
                        packageHash = 2014706845,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 69, offset = 3220, length = 31),
                            SourceLocationInfo(lineNumber = 70, offset = 3287, length = 70),
                            SourceLocationInfo(lineNumber = 74, offset = 3370, length = 70)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(animateFloat)P(2)939@37552L78:Transition.kt#pdpnli",
                    ComposeContext(
                        name = "animateFloat",
                        sourceFile = "Transition.kt",
                        packageHash = 1534686390,
                        locations = listOf(SourceLocationInfo(lineNumber = 940, offset = 37552, length = 78)),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(animateValue)P(3,2)857@34142L32,858@34197L31,859@34253L23,861@34289L89:Transition.kt#pdpnli",
                    ComposeContext(
                        name = "animateValue",
                        sourceFile = "Transition.kt",
                        packageHash = 1534686390,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 858, offset = 34142, length = 32),
                            SourceLocationInfo(lineNumber = 859, offset = 34197, length = 31),
                            SourceLocationInfo(lineNumber = 860, offset = 34253, length = 23),
                            SourceLocationInfo(lineNumber = 862, offset = 34289, length = 89)
                        ),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(remember):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "remember",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(remember)P(1):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "remember",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(remember)P(1,2):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "remember",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(remember)P(1,2,3):Composables.kt#9igjgp",
                    ComposeContext(
                        name = "remember",
                        sourceFile = "Composables.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = listOf(
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(rememberCoroutineScope)488@20446L144:Effects.kt#9igjgp",
                    ComposeContext(
                        name = "rememberCoroutineScope",
                        sourceFile = "Effects.kt",
                        packageHash = 575200393,
                        locations = listOf(SourceLocationInfo(lineNumber = 489, offset = 20446, length = 144)),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC(viewModel)P(3,2,1)*80@3834L7,90@4209L68:ViewModel.kt#3tja67",
                    ComposeContext(
                        name = "viewModel",
                        sourceFile = "ViewModel.kt",
                        packageHash = 231007039,
                        locations = listOf(
                            SourceLocationInfo(lineNumber = 81, offset = 3834, length = 7),
                            SourceLocationInfo(lineNumber = 91, offset = 4209, length = 68)
                        ),
                        repeatOffset = 0,
                        parameters = listOf(
                            Parameter(sortedIndex = 3, inlineClass = null),
                            Parameter(sortedIndex = 2, inlineClass = null),
                            Parameter(sortedIndex = 1, inlineClass = null),
                            Parameter(sortedIndex = 0, inlineClass = null)
                        ),
                        isCall = true,
                        isInline = true
                    )
                ),
                Arguments.of(
                    "CC:CompositionLocal.kt#9igjgp",
                    ComposeContext(
                        name = null,
                        sourceFile = "CompositionLocal.kt",
                        packageHash = 575200393,
                        locations = emptyList(),
                        repeatOffset = -1,
                        parameters = null,
                        isCall = true,
                        isInline = true
                    )
                )

                )
        }
    }
}
