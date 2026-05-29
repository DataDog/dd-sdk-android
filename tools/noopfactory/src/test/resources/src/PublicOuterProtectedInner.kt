@file:Suppress("MatchingDeclarationName")

package com.example

import com.datadog.tools.annotation.NoOpImplementation

abstract class PublicOuter {

    @NoOpImplementation
    protected interface ProtectedInner {
        fun doSomething(): Int
    }
}
