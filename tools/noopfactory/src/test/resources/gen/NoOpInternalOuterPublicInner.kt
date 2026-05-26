@file:Suppress(
    "ktlint",
    "MatchingDeclarationName",
)

package com.example

import kotlin.Int
import kotlin.Suppress

internal class NoOpInternalOuterPublicInner : InternalOuter.PublicInner {
    override fun doSomething(): Int = 0
}
