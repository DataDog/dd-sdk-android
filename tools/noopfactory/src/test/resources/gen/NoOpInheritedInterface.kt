package com.example

import kotlin.Suppress

@Suppress("RedundantUnitReturnType")
internal class NoOpInheritedInterface : InheritedInterface {
    public override fun doSomething() {
    }

    public override fun parentMethod() {
    }

    public override fun rootMethod() {
    }
}
