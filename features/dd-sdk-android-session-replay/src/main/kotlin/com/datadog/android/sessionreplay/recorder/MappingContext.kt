/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper

/**
 * Contains the context information which will be passed from parent to its children when
 * traversing the tree view for masking, as well as utilities and helpers that allow generating the wireframes
 * expected by Datadog.
 * @param systemInformation as [SystemInformation]
 * @param imageWireframeHelper a helper tool to capture images within a View
 * @param textAndInputPrivacy the text and input privacy level to use when building the wireframes
 * @param imagePrivacy the image recording configuration to use when building the wireframes
 * @param touchPrivacyManager the manager to handle touch privacy area.
 * @param hasOptionSelectorParent tells if one of the parents of the current [android.view.View]
 * is an option selector type (e.g. time picker, date picker, drop - down list)
 */
data class MappingContext(
    val systemInformation: SystemInformation,
    val imageWireframeHelper: ImageWireframeHelper,
    val textAndInputPrivacy: TextAndInputPrivacy,
    val imagePrivacy: ImagePrivacy,
    val touchPrivacyManager: TouchPrivacyManager,
    val hasOptionSelectorParent: Boolean = false
)
