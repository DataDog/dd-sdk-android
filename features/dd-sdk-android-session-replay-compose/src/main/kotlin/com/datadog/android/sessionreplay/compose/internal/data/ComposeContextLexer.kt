/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

internal object ComposeContextLexer {

    private sealed class TokenReader {

        abstract fun process(c: Char)
        abstract fun updateComposeContext(composeContext: ComposeContext?): ComposeContext?
        protected var nextReader: TokenReader? = null

        fun nextReader() = nextReader

        protected fun nextReader(c: Char): TokenReader {
            return when (c) {
                'C' -> CallTR()
                'P' -> ParametersTR()
                '1', '2', '3', '4', '5', '6', '7', '8', '9' -> LocationTR(c)
                ':' -> SourceFileTR()
                ',' -> EmptyTR()
                '*' -> RepeatTR()
                else -> InvalidTR()
            }
        }

        class InvalidTR : TokenReader() {
            override fun process(c: Char) {
            }

            override fun updateComposeContext(composeContext: ComposeContext?): ComposeContext? {
                return null
            }
        }

        class EmptyTR : TokenReader() {
            override fun process(c: Char) {
                nextReader = nextReader(c)
            }

            override fun updateComposeContext(composeContext: ComposeContext?): ComposeContext? {
                return composeContext
            }
        }

        class RepeatTR : TokenReader() {
            override fun process(c: Char) {
                nextReader = nextReader(c)
            }

            override fun updateComposeContext(composeContext: ComposeContext?): ComposeContext? {
                return composeContext?.copy(repeatOffset = composeContext.locations.size)
            }
        }

        class CallTR : TokenReader() {

            private var isInline = false
            private var callName = ""
            private var hasClassName = false

            override fun process(c: Char) {
                if (hasClassName) {
                    if (c == ')') {
                        nextReader = EmptyTR()
                    } else {
                        callName += c
                    }
                } else if (c == 'C') {
                    isInline = true
                } else if (c == '(') {
                    hasClassName = true
                } else {
                    nextReader = nextReader(c)
                }
            }

            override fun updateComposeContext(composeContext: ComposeContext?): ComposeContext? {
                return composeContext?.copy(
                    name = if (hasClassName) callName else null,
                    isCall = true,
                    isInline = isInline
                )
            }
        }

        class LocationTR(initialDigit: Char) : TokenReader() {
            var line: Int = initialDigit.digitToInt()
            var offset: Int = -1
            var length: Int = -1

            override fun process(c: Char) {
                when (c) {
                    '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' -> {
                        if (length >= 0) {
                            length = (length * 10) + c.digitToInt()
                        } else if (offset >= 0) {
                            offset = (offset * 10) + c.digitToInt()
                        } else {
                            line = (line * 10) + c.digitToInt()
                        }
                    }

                    '@' -> offset = 0
                    'L' -> length = 0

                    else -> nextReader = nextReader(c)
                }
            }

            override fun updateComposeContext(composeContext: ComposeContext?): ComposeContext? {
                // line is 0-indexed, but to be user readable, make it 1-indexed
                val sourceLocationInfo = SourceLocationInfo(line + 1, offset, length)
                return composeContext?.copy(
                    locations = composeContext.locations + sourceLocationInfo
                )
            }
        }

        class ParametersTR : TokenReader() {
            var isReadingParams = false
            var params = mutableListOf<Parameter>()
            var currentNumber: Int? = null
            var isNextNumberRun = false
            var isReadingInline = false
            var inlineClass: String? = null

            val expectedSortedIndex = mutableListOf(0, 1, 2, 3)
            var lastAdded = expectedSortedIndex.size - 1

            override fun process(c: Char) {
                when (c) {
                    '(' -> {
                        isReadingParams = true
                    }

                    '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' -> {
                        currentNumber = ((currentNumber ?: 0) * 10) + c.digitToInt()
                    }

                    '!' -> {
                        processCurrentNumber()
                        isNextNumberRun = true
                    }

                    ',' -> {
                        processCurrentNumber()
                    }

                    ':' -> {
                        isReadingInline = true
                        inlineClass = ""
                    }

                    ')' -> {
                        processCurrentNumber()
                        nextReader = EmptyTR()
                    }

                    else -> if (isReadingInline) {
                        inlineClass += c
                    } else {
                        nextReader = InvalidTR()
                    }
                }
            }

            private fun processCurrentNumber() {
                if (currentNumber == null) return

                if (isNextNumberRun) {
                    val count = currentNumber ?: 0
                    ensureIndexes(params.size + count)
                    repeat(count) {
                        params.add(Parameter(expectedSortedIndex.removeAt(0)))
                    }
                    isNextNumberRun = false
                } else {
                    val index = currentNumber ?: 0
                    val rawClass = inlineClass
                    val composeClass = if (rawClass != null && rawClass.startsWith("c#")) {
                        "androidx.compose." + rawClass.substring(2)
                    } else {
                        rawClass
                    }
                    params.add(Parameter(index, composeClass))
                    ensureIndexes(index)
                    expectedSortedIndex.remove(index)
                }
                currentNumber = null
                inlineClass = null
            }

            override fun updateComposeContext(composeContext: ComposeContext?): ComposeContext? {
                while (expectedSortedIndex.size > 0) {
                    params.add(Parameter(expectedSortedIndex.removeAt(0)))
                }
                return composeContext?.copy(
                    parameters = params
                )
            }

            private fun ensureIndexes(index: Int) {
                val missing = index - lastAdded
                if (missing > 0) {
                    val minAddAmount = 4
                    val amountToAdd = if (missing < minAddAmount) minAddAmount else missing
                    repeat(amountToAdd) {
                        expectedSortedIndex.add(it + lastAdded + 1)
                    }
                    lastAdded += amountToAdd
                }
            }
        }

        class SourceFileTR : TokenReader() {
            var sourceFile: String = ""
            var packageHash: String = ""
            var isReadingHash = false

            override fun process(c: Char) {
                when (c) {
                    '#' -> isReadingHash = true
                    else -> {
                        if (isReadingHash) {
                            packageHash += c
                        } else {
                            sourceFile += c
                        }
                    }
                }
            }

            override fun updateComposeContext(composeContext: ComposeContext?): ComposeContext? {
                return composeContext?.copy(
                    sourceFile = sourceFile,
                    packageHash = packageHash.toInt(36)
                )
            }
        }
    }

    fun parse(raw: String): ComposeContext? {
        var composeContext: ComposeContext? = ComposeContext()
        var i = 0
        var reader: TokenReader = TokenReader.EmptyTR()

        while (i < raw.length) {
            val c = raw[i]
            reader.process(c)
            val nextReader = reader.nextReader()
            if ((nextReader != null)) {
                composeContext = reader.updateComposeContext(composeContext)
                reader = nextReader
            }
            i++
        }

        composeContext = reader.updateComposeContext(composeContext)

        return composeContext
    }
}
