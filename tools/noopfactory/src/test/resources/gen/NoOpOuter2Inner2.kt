@file:Suppress("ktlint")

package com.example

import kotlin.Int
import kotlin.String
import kotlin.Suppress

internal class NoOpOuter2Inner2 : Outer2.Inner2 {
    override val name: String = ""

    override fun doSomething(): Int = 0
}
