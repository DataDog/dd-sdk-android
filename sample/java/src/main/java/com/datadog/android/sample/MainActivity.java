/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sample;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.datadog.android.log.Logger;
import com.datadog.android.sample.logs.LogsFragment;
import com.datadog.android.sample.webview.WebFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;


public class MainActivity extends AppCompatActivity {

    private Logger logger;

    private BottomNavigationView.OnNavigationItemSelectedListener navigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
            return switchToFragment(menuItem.getItemId());
        }
    };

    // region Activity Lifecycle

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logger = SampleApplication.fromContext(this).getLogger();
        logger.d("MainActivity/onCreate");

        setContentView(R.layout.activity_main);

        switchToFragment(R.id.navigation_logs);
        ((BottomNavigationView) findViewById(R.id.navigation))
                .setOnNavigationItemSelectedListener(navigationItemSelectedListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        logger.d("MainActivity/onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        logger.d("MainActivity/onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.d("MainActivity/onResume");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            logger.e("Interrupted sleep", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        logger.d("MainActivity/onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        logger.d("MainActivity/onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.d("MainActivity/onDestroy");
    }

    // endregion

    // region Internal

    private boolean switchToFragment(@IdRes int id) {
        Fragment fragmentToUse = null;
        switch (id) {
            case R.id.navigation_logs:
                logger.i("Switching to fragment: Logs");
                fragmentToUse = LogsFragment.newInstance();
                break;
            case R.id.navigation_webview:
                logger.i("Switching to fragment: Web");
                fragmentToUse = WebFragment.newInstance();
                break;
        }

        if (fragmentToUse == null) {
            logger.w("Switching to fragment: unknown @" + id);
            Toast.makeText(this, "We're unable to create this fragment.", Toast.LENGTH_LONG).show();
            return false;
        } else {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_host, fragmentToUse);
            ft.commit();
            return true;
        }
    }

    // endregion
}
