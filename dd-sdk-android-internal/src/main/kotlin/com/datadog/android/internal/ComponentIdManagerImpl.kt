/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import com.datadog.android.internal.utils.toHexString
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Collections
import java.util.Stack
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Implementation of [ComponentIdManager] that generates globally unique, stable identifiers
 * for Android Views.
 *
 * Thread-safe: [setCurrentScreen] is called from a RUM worker thread while [onWindowRefreshed]
 * and [getComponentId] are called from the main thread.
 *
 * @param appIdentifier The application package name used as the root of canonical paths
 */
@Suppress("TooManyFunctions")
class ComponentIdManagerImpl(
    private val appIdentifier: String
) : ComponentIdManager {

    @Suppress("UnsafeThirdPartyFunctionCall") // LinkedHashMap constructor doesn't throw
    private val resourceNameCache: MutableMap<Int, String> = Collections.synchronizedMap(
        object : LinkedHashMap<Int, String>(RESOURCE_NAME_CACHE_SIZE, DEFAULT_LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
                return size > RESOURCE_NAME_CACHE_SIZE
            }
        }
    )

    @Suppress("UnsafeThirdPartyFunctionCall") // WeakHashMap() is never null
    private val viewPathDataCache: MutableMap<View, PathData> =
        Collections.synchronizedMap(WeakHashMap())

    @Suppress("UnsafeThirdPartyFunctionCall") // WeakHashMap() is never null
    private val rootScreenNamespaceCache: MutableMap<View, String> =
        Collections.synchronizedMap(WeakHashMap())
    private val currentRumViewIdentifier = AtomicReference<String?>(null)

    @Synchronized
    override fun setCurrentScreen(identifier: String?) {
        val previous = currentRumViewIdentifier.getAndSet(identifier)
        if (previous != identifier) {
            rootScreenNamespaceCache.clear()
            viewPathDataCache.clear()
        }
    }

    @Synchronized
    override fun onWindowRefreshed(root: View) {
        indexTree(root)
    }

    @Synchronized
    override fun getComponentId(view: View): String? {
        val pathData = viewPathDataCache[view] ?: computePathDataOnDemand(view)?.also {
            viewPathDataCache[view] = it
        }
        return pathData?.componentId
    }

    private fun indexTree(root: View) {
        val screenNamespace = getScreenNamespace(root)

        val rootPathSegment = getViewPathSegment(root, null)
        val rootCanonicalPath = "$appIdentifier/$screenNamespace/$rootPathSegment"

        val rootComponentId = md5Hex(rootCanonicalPath) ?: return
        viewPathDataCache[root] = PathData(rootCanonicalPath, rootComponentId)

        val stack = Stack<ViewWithCanonicalPath>()
        stack.push(ViewWithCanonicalPath(root, rootCanonicalPath))

        while (stack.isNotEmpty()) {
            val (currentView, currentCanonicalPath) = stack.pop()
            if (currentView is ViewGroup) {
                val childCount = currentView.childCount
                for (i in 0 until childCount) {
                    val childView = currentView.getChildAt(i)
                    val childPathSegment = getViewPathSegment(childView, currentView)
                    val childCanonicalPath = "$currentCanonicalPath/$childPathSegment"
                    val childComponentId = md5Hex(childCanonicalPath) ?: continue
                    viewPathDataCache[childView] = PathData(childCanonicalPath, childComponentId)
                    stack.push(ViewWithCanonicalPath(childView, childCanonicalPath))
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private fun computePathDataOnDemand(view: View): PathData? {
        val ancestorChain = mutableListOf<ViewWithParent>()
        var currentView: View? = view
        var cachedAncestorPathData: PathData? = null

        while (currentView != null) {
            val cached = viewPathDataCache[currentView]
            if (cached != null) {
                cachedAncestorPathData = cached
                break
            }
            val parentView = currentView.parent as? ViewGroup
            ancestorChain.add(ViewWithParent(currentView, parentView))
            currentView = parentView
        }

        if (ancestorChain.isEmpty()) return cachedAncestorPathData

        val startIndex: Int
        val baseCanonicalPath: String

        if (cachedAncestorPathData != null) {
            baseCanonicalPath = cachedAncestorPathData.canonicalPath
            startIndex = ancestorChain.lastIndex
        } else {
            val (rootView, rootParentView) = ancestorChain.lastOrNull() ?: return null
            if (ancestorChain.size == 1 && rootParentView == null) return null

            val screenNamespace = getScreenNamespace(rootView)
            val rootPathSegment = getViewPathSegment(rootView, null)
            val rootCanonicalPath = "$appIdentifier/$screenNamespace/$rootPathSegment"
            val rootComponentId = md5Hex(rootCanonicalPath) ?: return null
            viewPathDataCache[rootView] = PathData(rootCanonicalPath, rootComponentId)

            baseCanonicalPath = rootCanonicalPath
            startIndex = ancestorChain.lastIndex - 1
        }

        var canonicalPath = baseCanonicalPath
        for (i in startIndex downTo 0) {
            val (ancestorView, parentView) = ancestorChain[i]
            val pathSegment = getViewPathSegment(ancestorView, parentView)
            canonicalPath = "$canonicalPath/$pathSegment"
            val componentId = md5Hex(canonicalPath) ?: return null
            viewPathDataCache[ancestorView] = PathData(canonicalPath, componentId)
        }

        return viewPathDataCache[view]
    }

    private fun getScreenNamespace(rootView: View): String {
        rootScreenNamespaceCache[rootView]?.let { return it }

        val screenNamespace = currentRumViewIdentifier.get()
            ?.let { "$NAMESPACE_VIEW_PREFIX${escapePathComponent(it)}" }
            ?: findActivity(rootView)?.let { "$NAMESPACE_ACTIVITY_PREFIX${escapePathComponent(it::class.java.name)}" }
            ?: getResourceName(rootView)?.let { "$NAMESPACE_ROOT_ID_PREFIX${escapePathComponent(it)}" }
            ?: "$NAMESPACE_ROOT_CLASS_PREFIX${escapePathComponent(rootView.javaClass.name)}"

        rootScreenNamespaceCache[rootView] = screenNamespace
        return screenNamespace
    }

    private fun getViewPathSegment(view: View, parentView: ViewGroup?): String {
        val resourceName = getResourceName(view)
        if (resourceName != null) return escapePathComponent(resourceName)

        val indexAmongSameClassSiblings = if (parentView != null) {
            var precedingCount = 0
            val viewClass = view.javaClass
            for (i in 0 until parentView.childCount) {
                val siblingView = parentView.getChildAt(i)
                if (siblingView === view) break
                if (siblingView.javaClass == viewClass) precedingCount++
            }
            precedingCount
        } else {
            0
        }

        return "$LOCAL_KEY_CLASS_PREFIX${escapePathComponent(view.javaClass.name)}#$indexAmongSameClassSiblings"
    }

    private fun getResourceName(view: View): String? {
        val id = view.id
        if (id == View.NO_ID) return null

        return resourceNameCache[id] ?: try {
            view.resources?.getResourceName(id)?.also { name ->
                resourceNameCache[id] = name
            }
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    private data class ViewWithCanonicalPath(val view: View, val canonicalPath: String)
    private data class ViewWithParent(val view: View, val parentView: ViewGroup?)
    private data class PathData(val canonicalPath: String, val componentId: String)

    companion object {
        private const val RESOURCE_NAME_CACHE_SIZE = 500
        private const val DEFAULT_LOAD_FACTOR = 0.75f
        private const val NAMESPACE_VIEW_PREFIX = "view:"
        private const val NAMESPACE_ACTIVITY_PREFIX = "act:"
        private const val NAMESPACE_ROOT_ID_PREFIX = "root-id:"
        private const val NAMESPACE_ROOT_CLASS_PREFIX = "root-cls:"
        private const val LOCAL_KEY_CLASS_PREFIX = "cls:"
    }
}

private fun escapePathComponent(input: String): String {
    return input.replace("%", "%25").replace("/", "%2F")
}

private fun md5Hex(input: String): String? {
    return try {
        val messageDigest = MessageDigest.getInstance("MD5")
        messageDigest.update(input.toByteArray(Charsets.UTF_8))
        messageDigest.digest().toHexString()
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _: NoSuchAlgorithmException) {
        null
    }
}

@Suppress("ReturnCount")
private fun findActivity(view: View): Activity? {
    var ctx: Context? = view.context ?: return null
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
