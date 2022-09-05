/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.sessionreplay.recorder.mapper.AllowAllWireframeMapper
import com.datadog.android.sessionreplay.recorder.mapper.GenericWireframeMapper
import com.datadog.android.sessionreplay.recorder.mapper.MaskAllWireframeMapper

/**
 * Defines the Session Replay privacy policy when recording the sessions.
 * @see SessionReplayPrivacy.ALLOW_ALL
 * @see SessionReplayPrivacy.MASK_ALL
 *
 */
enum class SessionReplayPrivacy {
    /** Does not apply any privacy rule on the recorded data. **/
    ALLOW_ALL,

    /**
     *  Masks all the elements. All the characters in texts will be replaced by X, images will be
     *  replaced with just a placeholder and switch buttons, check boxes and radio buttons will also
     *  be masked. This is the default privacy rule.
     **/
    MASK_ALL;

    internal fun mapper(): GenericWireframeMapper {
        return when (this) {
            ALLOW_ALL -> AllowAllWireframeMapper()
            MASK_ALL -> MaskAllWireframeMapper()
        }
    }
}
