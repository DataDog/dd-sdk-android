@file:Suppress("ktlint")

package com.example

import kotlin.Suppress

internal class NoOpNestedReturnTypeInterface : NestedReturnTypeInterface {
    override fun getInner(): Outer.Inner = NoOpOuterInner()
}
