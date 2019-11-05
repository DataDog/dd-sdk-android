package com.datadog.gradle.utils

import org.w3c.dom.Node
import org.w3c.dom.NodeList
import kotlin.collections.Iterator as KIterator

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