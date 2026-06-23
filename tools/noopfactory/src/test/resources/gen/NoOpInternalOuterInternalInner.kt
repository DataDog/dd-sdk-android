@file:Suppress(
    "ktlint",
    "MatchingDeclarationName",
)

package com.example

import kotlin.Int
import kotlin.Suppress

internal class NoOpInternalOuterInternalInner : InternalOuter.InternalInner {
    override fun doSomething(): Int = 0
}
