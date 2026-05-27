@file:Suppress("MatchingDeclarationName", "UnusedPrivateClass")

package com.example

import com.datadog.tools.annotation.NoOpImplementation

class PublicOuter {

    @NoOpImplementation
    private interface PrivateInner {
        fun doSomething(): Int
    }
}
