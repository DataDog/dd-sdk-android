/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

/**
 * Datadog context captured at evaluation time.
 *
 * This context is used for aggregating evaluations by RUM view and for
 * enriching evaluation events with RUM session information.
 *
 * @param service Service name, or null if not available
 * @param applicationId RUM application ID, or null if RUM is not active
 * @param viewId RUM view ID, or null if no active view
 * @param viewName RUM view name, or null if no active view
 */
internal data class DDContext(
    val service: String?,
    val applicationId: String?,
    val viewId: String?,
    val viewName: String?
) {
    internal companion object {
        /**
         * Empty context when RUM is not active or no view is available.
         */
        internal val EMPTY = DDContext(
            service = null,
            applicationId = null,
            viewId = null,
            viewName = null
        )

        private const val RUM_APPLICATION_ID = "application_id"
        private const val RUM_VIEW_ID = "view_id"
        private const val RUM_VIEW_NAME = "view_name"

        internal fun fromFeatureContext(
            featureContext: Map<String, Any?>,
            service: String?
        ): DDContext {
            val applicationId = featureContext[RUM_APPLICATION_ID] as? String
            val viewId = featureContext[RUM_VIEW_ID] as? String
            val viewName = featureContext[RUM_VIEW_NAME] as? String

            return DDContext(
                service = service,
                applicationId = applicationId,
                viewId = viewId,
                viewName = viewName
            )
        }
    }
}
