@file:Suppress("ktlint")

package com.example

import kotlin.Int
import kotlin.String
import kotlin.Suppress

internal class NoOpOuterInner : Outer.Inner {
    override val name: String = ""

    override fun doSomething(): Int = 0
}
