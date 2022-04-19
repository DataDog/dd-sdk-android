/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.content.Context
import android.view.View

class T(context: Context) : View(context) {

    // fun onAttach(r: R) {
    //     val hashCode = r.hashCode()
    //     var parentHashCode = 0
    //     val parent = r.parent
    //     if(parent!=null){
    //         parentHashCode = parent.hashCode()
    //     }
    //     val composeNode = ComposeNodeView(hashCode, parentHashCode, r, r, r)
    //     SessionReplay.addNode(
    //         this,
    //         composeNode
    //     )
    // }

    fun stop() {
    }
}