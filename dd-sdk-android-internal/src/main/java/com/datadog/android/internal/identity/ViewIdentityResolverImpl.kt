/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.identity

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
 * Implementation of [ViewIdentityResolver] that generates globally unique, stable identifiers
 * for Android Views by computing and hashing their canonical path in the view hierarchy.
 *
 * Thread-safe: [setCurrentScreen] is called from a RUM worker thread while [onWindowRefreshed]
 * and [resolveViewIdentity] are called from the main thread.
 *
 * @param appIdentifier The application package name used as the root of canonical paths
 */
@Suppress("TooManyFunctions")
class ViewIdentityResolverImpl(
    private val appIdentifier: String
) : ViewIdentityResolver {

    /**
     * Cache: Resource ID (Int) → Resource name (String).
     * Example: 2131230001 → "com.example.app:id/login_button"
     *
     * LRU cache with fixed size. Never explicitly cleared - resource ID mappings
     * are global and don't change based on screen. Avoids repeated calls to
     * resources.getResourceName() which is expensive.
     */
    @Suppress("UnsafeThirdPartyFunctionCall") // LinkedHashMap constructor doesn't throw
    private val resourceNameCache: MutableMap<Int, String> = Collections.synchronizedMap(
        object : LinkedHashMap<Int, String>(RESOURCE_NAME_CACHE_SIZE, DEFAULT_LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
                return size > RESOURCE_NAME_CACHE_SIZE
            }
        }
    )

    /**
     * Cache: View reference → PathData (canonical path + identity hash).
     * Example: Button@0x7f3a → PathData("com.app/view:Home/login_button", "a1b2c3...")
     *
     * WeakHashMap so entries are removed when Views are garbage collected.
     * Cleared when screen changes (paths include screen namespace, so become invalid).
     */
    @Suppress("UnsafeThirdPartyFunctionCall") // WeakHashMap() is never null
    private val viewPathDataCache: MutableMap<View, PathData> =
        Collections.synchronizedMap(WeakHashMap())

    /**
     * Cache: Root view reference → Screen namespace string.
     * Example: DecorView@0x1a2b → "view:HomeScreen"
     *
     * Avoids recomputing namespace (which may involve walking context chain).
     * Cleared when screen changes (namespace depends on currentRumViewIdentifier).
     */
    @Suppress("UnsafeThirdPartyFunctionCall") // WeakHashMap() is never null
    private val rootScreenNamespaceCache: MutableMap<View, String> =
        Collections.synchronizedMap(WeakHashMap())

    /** The current RUM view identifier, set via setCurrentScreen(). */
    private val currentRumViewIdentifier = AtomicReference<String?>(null)

    @Synchronized
    override fun setCurrentScreen(identifier: String?) {
        @Suppress("UnsafeThirdPartyFunctionCall") // type-safe: generics prevent VarHandle type mismatches
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
    override fun resolveViewIdentity(view: View): String? {
        return viewPathDataCache[view]?.identityHash
    }

    private fun indexTree(root: View) {
        val screenNamespace = getScreenNamespace(root)
        val rootCanonicalPath = buildRootCanonicalPath(root, screenNamespace)

        traverseAndIndexViews(root, rootCanonicalPath)
    }

    /** Builds the canonical path for the root view (used as prefix for all descendants). */
    private fun buildRootCanonicalPath(root: View, screenNamespace: String): String {
        val rootPathSegment = getViewPathSegment(root, null)
        // Root view (e.g., DecorView) is not interactable, so we don't cache its identity.
        // We only need its path as the prefix for descendant paths.
        return "$appIdentifier/$screenNamespace/$rootPathSegment"
    }

    /** Depth-first traversal of view hierarchy, computing and caching identity for each view. */
    private fun traverseAndIndexViews(root: View, rootCanonicalPath: String) {
        // Index the root view (all cache insertions happen here for consistency)
        md5Hex(rootCanonicalPath)?.let { hash ->
            viewPathDataCache[root] = PathData(rootCanonicalPath, hash)
        }

        val stack = Stack<ViewWithCanonicalPath>()
        stack.push(ViewWithCanonicalPath(root, rootCanonicalPath))

        while (stack.isNotEmpty()) {
            val (parent, parentPath) = stack.pop()
            if (parent is ViewGroup) {
                indexChildrenOf(parent, parentPath, stack)
            }
        }
    }

    private fun indexChildrenOf(
        parent: ViewGroup,
        parentPath: String,
        stack: Stack<ViewWithCanonicalPath>
    ) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val childPath = "$parentPath/${getViewPathSegment(child, parent)}"
            val childHash = md5Hex(childPath) ?: continue

            viewPathDataCache[child] = PathData(childPath, childHash)
            stack.push(ViewWithCanonicalPath(child, childPath))
        }
    }

    private fun getScreenNamespace(rootView: View): String {
        rootScreenNamespaceCache[rootView]?.let { return it }

        val screenNamespace = getNamespaceFromRumView()
            ?: getNamespaceFromActivity(rootView)
            ?: getNamespaceFromRootResourceId(rootView)
            ?: getNamespaceFromRootClassName(rootView)

        rootScreenNamespaceCache[rootView] = screenNamespace
        return screenNamespace
    }

    /** Priority 1: Use RUM view identifier if available (set via RumMonitor.startView). */
    private fun getNamespaceFromRumView(): String? {
        return currentRumViewIdentifier.get()?.let { viewName ->
            "$NAMESPACE_VIEW_PREFIX${escapePathComponent(viewName)}"
        }
    }

    /** Priority 2: Fall back to Activity class name if root view has Activity context. */
    private fun getNamespaceFromActivity(rootView: View): String? {
        return findActivity(rootView)?.let { activity ->
            "$NAMESPACE_ACTIVITY_PREFIX${escapePathComponent(activity::class.java.name)}"
        }
    }

    /** Priority 3: Fall back to root view's resource ID if it has one. */
    private fun getNamespaceFromRootResourceId(rootView: View): String? {
        return getResourceName(rootView)?.let { resourceName ->
            "$NAMESPACE_ROOT_ID_PREFIX${escapePathComponent(resourceName)}"
        }
    }

    /** Priority 4: Last resort - use root view's class name. */
    private fun getNamespaceFromRootClassName(rootView: View): String {
        return "$NAMESPACE_ROOT_CLASS_PREFIX${escapePathComponent(rootView.javaClass.name)}"
    }

    private fun getViewPathSegment(view: View, parentView: ViewGroup?): String {
        val resourceName = getResourceName(view)
        if (resourceName != null) return escapePathComponent(resourceName)

        val siblingIndex = countPrecedingSiblingsOfSameClass(view, parentView)
        return "$LOCAL_KEY_CLASS_PREFIX${escapePathComponent(view.javaClass.name)}#$siblingIndex"
    }

    /**
     * Counts how many siblings of the same class appear before this view in the parent.
     * Used to disambiguate views without resource IDs (e.g., "TextView#0", "TextView#1").
     * Returns 0 if parentView is null (root view case).
     */
    private fun countPrecedingSiblingsOfSameClass(view: View, parentView: ViewGroup?): Int {
        if (parentView == null) return 0

        var count = 0
        val viewClass = view.javaClass
        for (i in 0 until parentView.childCount) {
            val sibling = parentView.getChildAt(i)
            if (sibling === view) break
            if (sibling.javaClass == viewClass) count++
        }
        return count
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
    private data class PathData(val canonicalPath: String, val identityHash: String)

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
