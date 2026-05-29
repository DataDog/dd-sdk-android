@file:Suppress("MatchingDeclarationName")

package com.example

import com.datadog.tools.annotation.NoOpImplementation

class PublicOuter {

    @NoOpImplementation
    internal interface InternalInner {
        fun doSomething(): Int
    }
}
