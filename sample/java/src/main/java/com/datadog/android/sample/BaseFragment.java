/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample;

import androidx.fragment.app.Fragment;

public abstract class BaseFragment extends Fragment {

    /**
     * Called when back is pressed on the parent activity
     *
     * @return true if the normal behavior (terminate the current activity)
     * should be bypassed
     */
    public abstract boolean onBackPressed();
}
