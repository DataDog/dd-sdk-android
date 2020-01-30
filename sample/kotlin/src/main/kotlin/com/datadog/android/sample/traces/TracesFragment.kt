package com.datadog.android.sample.traces

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.sample.MainActivity
import com.datadog.android.sample.R
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.util.GlobalTracer

class TracesFragment : Fragment(), View.OnClickListener {

    lateinit var mainScope: Scope
    lateinit var mainSpan: Span

    lateinit var viewModel: TracesViewModel
    lateinit var spinner: ProgressBar

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_traces, container, false)
        rootView.findViewById<Button>(R.id.start_async_operation).setOnClickListener(this)
        spinner = rootView.findViewById(R.id.spinner)
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(TracesViewModel::class.java)
    }

    override fun onResume() {
        val tracer = GlobalTracer.get()
        val mainActivitySpan = (activity as MainActivity).mainSpan
        mainSpan = tracer
            .buildSpan("TracesFragment").asChildOf(mainActivitySpan).start()
        mainScope = tracer.activateSpan(mainSpan)
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        mainScope.close()
        mainSpan.finish()
    }

    override fun onDetach() {
        super.onDetach()
        viewModel.stopAsyncOperations()
        spinner.visibility = View.INVISIBLE
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.start_async_operation -> {
                spinner.visibility = View.VISIBLE
                viewModel.startAsyncOperation(
                    onDone = {
                        spinner.visibility = View.INVISIBLE
                    })
            }
        }
    }

    // endregion

    companion object {
        fun newInstance(): TracesFragment {
            return TracesFragment()
        }
    }
}
