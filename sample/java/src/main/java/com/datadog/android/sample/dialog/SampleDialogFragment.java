/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.datadog.android.log.Logger;
import com.datadog.android.sample.R;
import com.datadog.android.sample.user.UserFragment;

public class SampleDialogFragment extends DialogFragment {

    private Logger mLogger = new Logger.Builder()
            .setLoggerName("dialog_fragment")
            .setLogcatLogsEnabled(true)
            .build();


    public static SampleDialogFragment newInstance() {
        return new SampleDialogFragment();
    }


    // region DialogFragment

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        return new AlertDialog.Builder(requireContext(), getTheme())
                .setTitle(R.string.title_dialog)
                .setMessage(R.string.msg_dialog)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLogger.i("Clicked on ok");
                    }
                })
                .setNeutralButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLogger.i("Clicked on cancel");
                    }
                })
                .create();
    }

    // endregion
}
