package com.datadog.android.sample.traces;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.datadog.android.log.Logger;
import com.datadog.android.sample.MainActivity;
import com.datadog.android.sample.R;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public class TracesFragment extends Fragment implements View.OnClickListener {

    private Scope mMainScope;
    private Span mMainSpan;
    private ProgressBar mSpinner;
    private TracesViewModel mViewModel;
    private Logger mLogger = new Logger.Builder()
            .setLoggerName("traces_fragment")
            .setLogcatLogsEnabled(true)
            .build();

    private int mInteractionsCount = 0;

    public static TracesFragment newInstance() {
        return new TracesFragment();
    }

    // region Fragment

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_traces, container, false);
        rootView.findViewById(R.id.start_async_operation).setOnClickListener(this);
        mSpinner = rootView.findViewById(R.id.spinner);
        return rootView;
    }

    @Override
    public void onResume() {
        final Tracer tracer = GlobalTracer.get();
        @SuppressWarnings("ConstantConditions")
        final Span mainActivitySpan = ((MainActivity)getActivity()).getMainSpan();
        mMainSpan = tracer
                .buildSpan("TracesFragment").asChildOf(mainActivitySpan).start();
        mMainScope = tracer.activateSpan(mMainSpan);
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMainScope.close();
        mMainSpan.finish();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mViewModel.stopAsyncOperations();
        mSpinner.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(TracesViewModel.class);
        mLogger.addAttribute("interactions", mInteractionsCount);
    }

    // endregion

    // region View.OnClickListener

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.start_async_operation) {
            mLogger.w("User triggered an async operation");
            mSpinner.setVisibility(View.VISIBLE);
            mViewModel.startAsyncOperation(new TracesViewModel.Task.Callback() {
                @Override
                public void onDone() {
                    mSpinner.setVisibility(View.INVISIBLE);
                }
            });
        }
    }
}
