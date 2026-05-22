@file:Suppress("ktlint")

package com.example

import kotlin.Suppress

internal class NoOpOuter2 : Outer2 {
    override fun getInner(): Outer2.Inner2 = NoOpOuter2Inner2()
}
