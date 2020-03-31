/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

import kotlin.collections.Iterator as KIterator
import org.w3c.dom.Node
import org.w3c.dom.NodeList

class NodeListSequence(
    private val nodeList: NodeList
) : Sequence<Node> {

    override fun iterator(): KIterator<Node> {
        return Iterator(nodeList)
    }

    class Iterator(
        private val nodeList: NodeList
    ) : KIterator<Node> {
        private var i = 0
        override fun hasNext() = nodeList.length > i
        override fun next(): Node = nodeList.item(i++)
    }
}

fun NodeList.asSequence() = NodeListSequence(this)
