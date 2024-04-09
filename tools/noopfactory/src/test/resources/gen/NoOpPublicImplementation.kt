@file:Suppress("ktlint")

package com.example

import kotlin.Suppress
import kotlin.Unit

@Suppress("RedundantUnitReturnType")
public class NoOpPublicImplementation : PublicImplementation {
    public override fun doSomething(): Unit {
    }
}
