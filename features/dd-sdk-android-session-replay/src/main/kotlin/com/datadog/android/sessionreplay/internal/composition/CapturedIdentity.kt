/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger

internal data class RumViewIdentityScope(val value: String)

internal enum class CapturedIdentityKind {
    SCREEN_ROOT,
    WINDOW,
    VIEW,
    COMPOSE_HOST,
    COMPOSE_NODE,
    LAYER,
    WIREFRAME
}

private const val WEB_VIEW_WIREFRAME_NAMESPACE = 0L
private const val SHAPE_WIREFRAME_NAMESPACE = 1L
private const val TEXT_WIREFRAME_NAMESPACE = 2L
private const val IMAGE_WIREFRAME_NAMESPACE = 3L
private const val PLACEHOLDER_WIREFRAME_NAMESPACE = 4L

/** Matches the wireframe identifier namespaces used by the iOS Core Animation pipeline. */
internal enum class CapturedWireframeKind(internal val wireIdNamespace: Long) {
    /**
     * Deliberately namespace `0`, i.e. unshifted: the wire id for a web-view wireframe must equal
     * its `slotId` verbatim (not `slotId` shifted into a namespace), matching the pre-existing
     * `id == slotId` contract the legacy recorder and iOS both rely on for WebView/JS-bridge
     * correlation (see [CapturedIdentityFactory.webViewWireframe]). `slotId` is caller-supplied and,
     * once wired to the real view-identity resolver, may be any `Int`-range value (including
     * negative), so this is the one identity kind not structurally guaranteed collision-free
     * against the rest of the tree. [DefaultCapturedIdentityFactory] offsets every non-wireframe
     * layer id by [LAYER_ID_OFFSET] specifically so that raw layer ids can never land in that same
     * `Int` range; a residual, validation-caught collision remains possible only between two
     * web-view wireframes with a colliding `slotId` in the same view scope.
     */
    WEB_VIEW(WEB_VIEW_WIREFRAME_NAMESPACE),
    SHAPE(SHAPE_WIREFRAME_NAMESPACE),
    TEXT(TEXT_WIREFRAME_NAMESPACE),
    IMAGE(IMAGE_WIREFRAME_NAMESPACE),
    PLACEHOLDER(PLACEHOLDER_WIREFRAME_NAMESPACE)
}

internal data class CapturedIdentity(
    val scope: RumViewIdentityScope,
    val kind: CapturedIdentityKind,
    val wireframeKind: CapturedWireframeKind?,
    val namespace: List<String>,
    val localId: String,
    val wireId: Long
) {
    fun path(): List<String> = namespace + kind.name + localId
}

internal fun interface CapturedReplayIdGenerator {
    fun next(): Long
}

internal class AutoIncrementingCapturedReplayIdGenerator(
    initialId: Long = 0
) : CapturedReplayIdGenerator {
    private var currentId = initialId

    @Synchronized
    override fun next(): Long {
        val replayId = currentId
        currentId = if (currentId < Int.MAX_VALUE) currentId + 1 else 0
        return replayId
    }
}

@Suppress("TooManyFunctions") // Each function creates one supported capture identity type.
internal interface CapturedIdentityFactory {
    val scope: RumViewIdentityScope

    fun screenRoot(): CapturedIdentity

    fun window(windowId: String): CapturedIdentity

    fun view(window: CapturedIdentity, viewId: String): CapturedIdentity

    fun composeHost(window: CapturedIdentity, hostId: String): CapturedIdentity

    fun composeNode(host: CapturedIdentity, nodeId: String): CapturedIdentity

    fun layer(owner: CapturedIdentity, layerId: String): CapturedIdentity

    fun shapeWireframe(owner: CapturedIdentity): CapturedIdentity

    fun textWireframe(owner: CapturedIdentity): CapturedIdentity

    fun imageWireframe(owner: CapturedIdentity): CapturedIdentity

    fun placeholderWireframe(owner: CapturedIdentity): CapturedIdentity

    fun webViewWireframe(owner: CapturedIdentity, slotId: Long): CapturedIdentity
}

internal class DefaultCapturedIdentityFactory(
    override val scope: RumViewIdentityScope,
    private val replayIdGenerator: CapturedReplayIdGenerator = SHARED_REPLAY_ID_GENERATOR,
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : CapturedIdentityFactory {

    private val identities = mutableMapOf<IdentityKey, CapturedIdentity>()

    override fun screenRoot(): CapturedIdentity =
        createLayerIdentity(CapturedIdentityKind.SCREEN_ROOT, emptyList(), SCREEN_ROOT_LOCAL_ID)

    override fun window(windowId: String): CapturedIdentity =
        createLayerIdentity(CapturedIdentityKind.WINDOW, emptyList(), windowId)

    override fun view(window: CapturedIdentity, viewId: String): CapturedIdentity {
        validateOwner(window, CapturedIdentityKind.WINDOW)
        return createLayerIdentity(CapturedIdentityKind.VIEW, window.path(), viewId)
    }

    override fun composeHost(window: CapturedIdentity, hostId: String): CapturedIdentity {
        validateOwner(window, CapturedIdentityKind.WINDOW)
        return createLayerIdentity(CapturedIdentityKind.COMPOSE_HOST, window.path(), hostId)
    }

    override fun composeNode(host: CapturedIdentity, nodeId: String): CapturedIdentity {
        validateOwner(host, CapturedIdentityKind.COMPOSE_HOST)
        return createLayerIdentity(CapturedIdentityKind.COMPOSE_NODE, host.path(), nodeId)
    }

    override fun layer(owner: CapturedIdentity, layerId: String): CapturedIdentity {
        validateLayerOwner(owner)
        return createLayerIdentity(CapturedIdentityKind.LAYER, owner.path(), layerId)
    }

    override fun shapeWireframe(owner: CapturedIdentity): CapturedIdentity =
        createNamespacedWireframeIdentity(owner, CapturedWireframeKind.SHAPE)

    override fun textWireframe(owner: CapturedIdentity): CapturedIdentity =
        createNamespacedWireframeIdentity(owner, CapturedWireframeKind.TEXT)

    override fun imageWireframe(owner: CapturedIdentity): CapturedIdentity =
        createNamespacedWireframeIdentity(owner, CapturedWireframeKind.IMAGE)

    override fun placeholderWireframe(owner: CapturedIdentity): CapturedIdentity =
        createNamespacedWireframeIdentity(owner, CapturedWireframeKind.PLACEHOLDER)

    override fun webViewWireframe(owner: CapturedIdentity, slotId: Long): CapturedIdentity {
        validateLayerOwner(owner)
        return createIdentity(
            kind = CapturedIdentityKind.WIREFRAME,
            wireframeKind = CapturedWireframeKind.WEB_VIEW,
            namespace = owner.path(),
            localId = slotId.toString(),
            wireId = slotId
        )
    }

    private fun createLayerIdentity(
        kind: CapturedIdentityKind,
        namespace: List<String>,
        localId: String
    ): CapturedIdentity {
        val key = IdentityKey(kind, null, namespace.toList(), localId)
        identities[key]?.let { return it }
        // Offset (not namespace-shifted) so raw layer ids can never collide with an unshifted
        // web-view slotId, which is caller-supplied and may fall anywhere in the Int range - while
        // staying below 1L shl 32 so createNamespacedWireframeIdentity's `... or owner.wireId` still
        // composes cleanly against a namespace shifted into the bits above it. See
        // CapturedWireframeKind.WEB_VIEW.
        return createIdentity(key, LAYER_ID_OFFSET + replayIdGenerator.next())
    }

    private fun createNamespacedWireframeIdentity(
        owner: CapturedIdentity,
        wireframeKind: CapturedWireframeKind
    ): CapturedIdentity {
        validateLayerOwner(owner)
        return createIdentity(
            kind = CapturedIdentityKind.WIREFRAME,
            wireframeKind = wireframeKind,
            namespace = owner.path(),
            localId = wireframeKind.name,
            wireId = (wireframeKind.wireIdNamespace shl NAMESPACE_SHIFT) or owner.wireId
        )
    }

    private fun createIdentity(
        kind: CapturedIdentityKind,
        wireframeKind: CapturedWireframeKind,
        namespace: List<String>,
        localId: String,
        wireId: Long
    ): CapturedIdentity = createIdentity(
        IdentityKey(kind, wireframeKind, namespace.toList(), localId),
        wireId
    )

    private fun createIdentity(key: IdentityKey, wireId: Long): CapturedIdentity {
        identities[key]?.let { return it }
        return CapturedIdentity(
            scope = scope,
            kind = key.kind,
            wireframeKind = key.wireframeKind,
            namespace = key.namespace,
            localId = key.localId,
            wireId = wireId
        ).also { identities[key] = it }
    }

    // These checks guard against programmer-only misuse of the internal identity factory. They
    // must never throw: a bug tripping them would otherwise crash the host application, so any
    // violation is reported to the maintainer telemetry instead and construction proceeds.
    private fun validateLayerOwner(owner: CapturedIdentity) {
        validateOwner(owner)
        if (owner.kind == CapturedIdentityKind.WIREFRAME) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "Identity owner must be a captured layer." }
            )
        }
    }

    private fun validateOwner(
        owner: CapturedIdentity,
        expectedKind: CapturedIdentityKind? = null
    ) {
        if (owner.scope != scope) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "Identity owner belongs to a different RUM view scope." }
            )
        }
        if (expectedKind != null && owner.kind != expectedKind) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "Identity owner must be a $expectedKind." }
            )
        }
    }

    private data class IdentityKey(
        val kind: CapturedIdentityKind,
        val wireframeKind: CapturedWireframeKind?,
        val namespace: List<String>,
        val localId: String
    )

    private companion object {
        const val SCREEN_ROOT_LOCAL_ID = "screen"
        const val NAMESPACE_SHIFT = 32
        val SHARED_REPLAY_ID_GENERATOR = AutoIncrementingCapturedReplayIdGenerator()
    }
}

/**
 * Added to every raw layer replay id so that layer wire ids always land above the Int range
 * (`[1L shl 31, (1L shl 32) - 1]` given the generator wraps at [Int.MAX_VALUE]) - outside the range
 * an unshifted web-view slotId can occupy - while staying below `1L shl 32`, so
 * [DefaultCapturedIdentityFactory.createNamespacedWireframeIdentity]'s
 * `(wireframeKind.wireIdNamespace shl 32) or owner.wireId` still composes cleanly against a layer
 * owner. See [CapturedWireframeKind.WEB_VIEW].
 */
internal const val LAYER_ID_OFFSET = 1L shl 31
