@file:Suppress("ktlint")

package com.example

import kotlin.Deprecated
import kotlin.Int
import kotlin.String
import kotlin.Suppress

internal class NoOpOverloadedInterface : OverloadedInterface {
    @Deprecated("foobar")
    override fun doSomething(i: Int) {
    }

    override fun doSomething(i: String) {
    }
}
