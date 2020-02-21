/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sample;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.datadog.android.log.Logger;
import com.datadog.android.rum.GlobalRum;
import com.datadog.android.rum.RumMonitor;
import com.datadog.android.sample.logs.LogsFragment;
import com.datadog.android.sample.traces.TracesFragment;
import com.datadog.android.sample.user.UserFragment;
import com.datadog.android.sample.webview.WebFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

import java.util.HashMap;


public class MainActivity extends AppCompatActivity {

    private Logger mLogger;
    private Scope mMainScope;
    private Span mMainSpan;
    private Span mResumePauseSpan;
    private RumMonitor mRumMonitor;

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

        mLogger = SampleApplication.fromContext(this).getLogger();
        mLogger.d("MainActivity/onCreate");

        mRumMonitor = GlobalRum.get();

        setContentView(R.layout.activity_main);

        switchToFragment(R.id.navigation_logs);
        ((BottomNavigationView) findViewById(R.id.navigation))
                .setOnNavigationItemSelectedListener(navigationItemSelectedListener);
    }

    @Override
    protected void onStart() {
        final Tracer tracer = GlobalTracer.get();
        mMainSpan = tracer.buildSpan("MainActivity").start();
        mMainScope = tracer.activateSpan(mMainSpan);
        super.onStart();
        mLogger.d("MainActivity/onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mLogger.d("MainActivity/onRestart");
    }

    @Override
    protected void onResume() {
        mRumMonitor.startView(this, getComponentName().getClassName(), new HashMap<String, Object>());
        mResumePauseSpan = GlobalTracer.get()
                .buildSpan("onResumeOnPause")
                .asChildOf(mMainSpan)
                .start();
        super.onResume();
        mLogger.d("MainActivity/onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLogger.d("MainActivity/onPause");
        mResumePauseSpan.finish();
        mRumMonitor.stopView(this, new HashMap<String, Object>());
    }

    @Override
    protected void onStop() {
        super.onStop();
        mLogger.d("MainActivity/onStop");
        mMainScope.close();
        mMainSpan.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLogger.d("MainActivity/onDestroy");
    }

    // endregion

    // region utility methods

    public Span getMainSpan() {
        return mMainSpan;
    }

    // endregion

    // region Internal

    private boolean switchToFragment(@IdRes final int id) {

        mRumMonitor.addUserAction(
                "switchToFragment",
                new HashMap<String,Object>(){{
                    put("fragment_id", Integer.toString(id));
                }});
        final Fragment fragmentToUse;
        final String spanName;
        switch (id) {
            case R.id.navigation_logs:
                mLogger.i("Switching to fragment: Logs");
                spanName = "SwitchingToLogsFragment";
                fragmentToUse = LogsFragment.newInstance();
                break;
            case R.id.navigation_webview:
                mLogger.i("Switching to fragment: Web");
                spanName = "SwitchingToWebViewFragment";
                fragmentToUse = WebFragment.newInstance();
                break;
            case R.id.navigation_traces:
                mLogger.i("Switching to fragment: Traces");
                spanName = "SwitchingToTracesFragment";
                fragmentToUse = TracesFragment.newInstance();
                break;
            case R.id.navigation_user:
                mLogger.i("Switching to fragment: User");
                spanName = "SwitchingToUserFragment";
                fragmentToUse = UserFragment.newInstance();
                break;
            default:
                mLogger.e("Switching to unknown fragment " + id);
                throw new IllegalStateException("Unknown fragment " + id);
        }

        addSpanInScope(spanName, new Runnable() {
            @Override
            public void run() {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_host, fragmentToUse);
                ft.commit();
            }
        });


        return true;

    }

    private void addSpanInScope(String opName, Runnable execute) {
        final Tracer tracer = GlobalTracer.get();
        final Span span = tracer.buildSpan(opName).start();
        try {
            final Scope scope = tracer.activateSpan(span);
            execute.run();
            scope.close();
        } catch (Exception e) {
            span.log(e.getMessage());
        } finally {
            span.finish();
        }
    }
    // endregion
}
