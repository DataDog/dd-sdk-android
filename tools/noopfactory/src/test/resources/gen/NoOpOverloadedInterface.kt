@file:Suppress("ktlint")

package com.example

import kotlin.Deprecated
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit

@Suppress("RedundantUnitReturnType")
internal class NoOpOverloadedInterface : OverloadedInterface {
    @Deprecated("foobar")
    public override fun doSomething(i: Int): Unit {
    }

    public override fun doSomething(i: String): Unit {
    }
}
