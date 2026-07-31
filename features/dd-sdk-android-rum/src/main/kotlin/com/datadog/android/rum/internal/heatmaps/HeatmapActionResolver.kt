/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.heatmaps

import com.datadog.android.api.InternalLogger
import com.datadog.android.heatmaps.CrossPlatformHeatmapActionData
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.rum.model.ActionEvent
import java.util.Locale

internal sealed class HeatmapActionResolver {

    abstract fun resolve(viewUrl: String): ActionEvent.DdAction?

    internal class Native(
        private val data: NativeHeatmapActionData,
        private val registry: HeatmapIdentifierRegistry?
    ) : HeatmapActionResolver() {
        override fun resolve(viewUrl: String): ActionEvent.DdAction? {
            val permanentId = registry
                ?.getHeatmapIdentifier(data.viewKey, viewUrl)
                ?.rawValue ?: return null
            return buildDdAction(permanentId, data.positionX, data.positionY, data.targetWidth, data.targetHeight)
        }
    }

    internal class CrossPlatform(
        private val data: CrossPlatformHeatmapActionData,
        private val appPackageName: String,
        private val logger: InternalLogger
    ) : HeatmapActionResolver() {
        @Suppress("ReturnCount")
        override fun resolve(viewUrl: String): ActionEvent.DdAction? {
            if (data.viewUrl != viewUrl) {
                logger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { HEATMAP_VIEW_URL_MISMATCH.format(Locale.US, data.viewUrl, viewUrl) }
                )
                return null
            }
            if (data.elementPath.isEmpty()) {
                logger.log(InternalLogger.Level.ERROR, InternalLogger.Target.USER, { HEATMAP_EMPTY_ELEMENT_PATH })
                return null
            }
            val identifier = HeatmapIdentifier.create(
                elementPath = data.elementPath,
                screenName = viewUrl,
                appPackageName = appPackageName,
                onHashingFailure = { throwable ->
                    logger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        { HEATMAP_IDENTIFIER_HASH_FAILURE },
                        throwable
                    )
                }
            ) ?: return null
            return buildDdAction(
                identifier.rawValue,
                data.positionX,
                data.positionY,
                data.targetWidth,
                data.targetHeight
            )
        }
    }

    companion object {
        internal const val HEATMAP_VIEW_URL_MISMATCH =
            "Heatmap view URL mismatch (recorded \"%s\", current \"%s\") — heatmap data dropped."
        internal const val HEATMAP_EMPTY_ELEMENT_PATH =
            "CrossPlatformHeatmapActionData.elementPath is empty — heatmap data dropped."
        internal const val HEATMAP_IDENTIFIER_HASH_FAILURE =
            "Failed to compute heatmap identifier — heatmap data dropped."

        internal fun buildDdAction(
            permanentId: String,
            positionX: Long,
            positionY: Long,
            targetWidth: Long?,
            targetHeight: Long?
        ): ActionEvent.DdAction = ActionEvent.DdAction(
            position = ActionEvent.Position(x = positionX, y = positionY),
            target = ActionEvent.DdActionTarget(
                permanentId = permanentId,
                width = targetWidth,
                height = targetHeight
            )
        )
    }
}
