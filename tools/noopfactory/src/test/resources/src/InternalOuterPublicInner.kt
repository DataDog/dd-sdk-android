@file:Suppress("MatchingDeclarationName")

package com.example

import com.datadog.tools.annotation.NoOpImplementation

internal class InternalOuter {

    @NoOpImplementation
    interface PublicInner {
        fun doSomething(): Int
    }
}
