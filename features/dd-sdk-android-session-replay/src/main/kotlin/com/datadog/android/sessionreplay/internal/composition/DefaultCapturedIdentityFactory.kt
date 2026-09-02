/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger

/**
 * Added to every raw layer replay id so that layer wire ids always land above the Int range
 * (`[1L shl 31, (1L shl 32) - 1]` given the generator wraps at [Int.MAX_VALUE]) - outside the range
 * an unshifted web-view slotId can occupy - while staying below `1L shl 32`, so
 * [DefaultCapturedIdentityFactory.createNamespacedWireframeIdentity]'s
 * `(wireframeKind.wireIdNamespace shl 32) or owner.wireId` still composes cleanly against a layer
 * owner. See [CapturedWireframeKind.WEB_VIEW].
 */
internal const val LAYER_ID_OFFSET = 1L shl 31

internal class DefaultCapturedIdentityFactory(
    override val scope: RumViewIdentityScope,
    private val replayIdGenerator: CapturedReplayIdGenerator = AutoIncrementingCapturedReplayIdGenerator(),
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : CapturedIdentityFactory {

    // Concrete traversal implementations are still to come; this factory has no documented
    // thread-confinement contract, so every access to the cache below is serialized rather than
    // assumed to happen from one thread.
    private val lock = Any()
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
        return synchronized(lock) {
            // Offset, not namespace-shifted - see LAYER_ID_OFFSET's KDoc for why.
            identities[key] ?: createIdentityLocked(key, LAYER_ID_OFFSET + replayIdGenerator.next())
        }
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

    private fun createIdentity(key: IdentityKey, wireId: Long): CapturedIdentity =
        synchronized(lock) { createIdentityLocked(key, wireId) }

    private fun createIdentityLocked(key: IdentityKey, wireId: Long): CapturedIdentity {
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
    }
}
