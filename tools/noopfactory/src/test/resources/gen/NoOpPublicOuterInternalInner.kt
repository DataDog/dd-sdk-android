@file:Suppress(
    "ktlint",
    "MatchingDeclarationName",
)

package com.example

import kotlin.Int
import kotlin.Suppress

internal class NoOpPublicOuterInternalInner : PublicOuter.InternalInner {
    override fun doSomething(): Int = 0
}
