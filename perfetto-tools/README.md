# Perfetto Tools for Android Profiling

This directory contains tools to help process and analyze Perfetto traces for Android application performance monitoring.

## `perfetto_to_pprof.py`

This Python script is the primary tool in this folder. It converts CPU profiling data from a Perfetto trace file into the `pprof` format, which is widely supported by profiling visualization tools like Google's `pprof`.

### Purpose

When you capture a Perfetto trace containing CPU samples (specifically `perf_sample` data along with stack unwinding tables like `stack_profile_mapping`, `stack_profile_frame`, and `stack_profile_callsite`), this script extracts that information and transforms it into a `.pb.gz` or `.pprof` file. This output file can then be analyzed to understand CPU usage, identify hot spots, and visualize call stacks by our profiling dashboard. 

### Setup

It is recommended to use a Python virtual environment to manage dependencies for this tool.

1.  **Create a Virtual Environment** (if you haven't already):
    Navigate to the `perfetto-tools` directory in your terminal and run:
    ```bash
    python3 -m venv venv
    ```
    This creates a `venv` subdirectory containing the virtual environment.

2.  **Activate the Virtual Environment**:
    *   On macOS and Linux:
        ```bash
        source venv/bin/activate
        ```
    *   On Windows:
        ```bash
        .\venv\Scripts\activate
        ```
    Your terminal prompt should change to indicate that the virtual environment is active.

3.  **Install Dependencies**:
    With the virtual environment active, install the required packages from `requirements.txt`:
    ```bash
    pip install -r requirements.txt
    ```

### Dependencies

The necessary Python packages are listed in `requirements.txt` and include:

1.  **`perfetto`**: For parsing Perfetto traces.
2.  **`protobuf`**: For working with the `pprof` data structures.

Additionally, you'll need:

*   **Python 3**: Ensure you have Python 3 installed (as used for the virtual environment setup).
*   **`profile_pb2.py`**: This file contains the Python classes generated from the `profile.proto` definition used by `pprof`.
    *   It is assumed that `profile_pb2.py` is either in the same directory as `perfetto_to_pprof.py` or can be imported (e.g., if this tool is part of a larger Python package).
    *   You can typically generate this from a `profile.proto` file (often found in the `pprof` or Perfetto source repositories) using the `protoc` compiler:
        ```bash
        # Example:
        # protoc --python_out=. profile.proto
        ```

### Usage

The script is run from the command line:

```bash
python3 perfetto_to_pprof.py -t <path_to_trace_file> -o <output_pprof_file> [options]
```

**Arguments:**

*   `-t, --trace TRACE_FILE` (Required): Path to the input Perfetto trace file (e.g., `my_app.perfetto-trace`).
*   `-o, --output OUTPUT_FILE` (Default: `profile.pb.gz`): Path for the output `pprof` file. Common extensions are `.pprof` or `.pb.gz`.
*   `-p, --pid PID` (Optional): Filter samples to a specific Process ID (PID). This is highly recommended to focus the analysis on your application's process.
*   `--sampling_interval_ns INTERVAL_NS` (Optional): The sampling interval in nanoseconds (e.g., `10000000` for 10ms).
    *   If not set, the script attempts to infer this from the trace data.
    *   If inference fails, it defaults to 2ms (`2000000` ns).
    *   Providing an accurate value is important if the inference is not reliable for your trace, as this value determines the weight of each sample.

**Example Command:**

```bash
python3 perfetto_to_pprof.py --trace /path/to/your/app_trace.perfetto-trace --output /path/to/your/app_profile.pprof --pid 12345
```

## Workflow: Profiling with `dd-sdk-android-rum` and Local Analysis

This workflow describes how to use `perfetto_to_pprof.py` to analyze profiling data from your Android application, particularly when using `dd-sdk-android-rum` and a local server setup for data interception and conversion.

1.  **Set Up Local Profiling Data Interception:**
    *   Start your `local_profiling_server`. (The setup and operation of this server are specific to your environment and not covered by this tool's documentation.)
    *   Configure your Android application's `dd-sdk-android-rum` (or its profiling uploader component) to send profiling traces to your `local_profiling_server` instead of the default Datadog endpoint.
    *   Ensure your `local_profiling_server` is capable of receiving this profiling data and saving it as a standard Perfetto trace file (e.g., `captured_trace.perfetto-trace`).

2.  **Convert Intercepted Trace to `pprof` Format (Using `perfetto_to_pprof.py`):**
    *   Once your `local_profiling_server` has captured and saved a Perfetto trace file from your application, use the `perfetto_to_pprof.py` script (from this folder) to convert this file into the `pprof` format:
        ```bash
        # Example:
        python3 perfetto-tools/perfetto_to_pprof.py \\
            -t /path/to/your/server/captured_trace.perfetto-trace \\
            -o /path/to/output/my_app_profile.pprof \\
            -p <your_app_pid_if_known> # Recommended to focus the profile
        ```
    *   Replace placeholders with the actual path to the trace file saved by your server, your desired output path for the `.pprof` file, and your application's PID (if available and applicable).

3.  **Analyze with `pprof` ("Local Server"):**
    *   `pprof` is a Go tool for visualizing and analyzing profiling data. If you don't have Go installed, you'll need to set it up first.
    *   Once Go is installed, `pprof` is usually available via `go tool pprof`.
    *   **Interactive Web UI (Recommended):**
        This launches a local web server where you can explore the profile data through various views (flame graphs, top functions, call graphs, etc.).
        ```bash
        go tool pprof -http=:8080 ./my_app_cpu_profile.pprof
        ```
        Then open `http://localhost:8080` in your web browser.
    *   **Text-Based Output:**
        For a quick summary in your terminal:
        ```bash
        go tool pprof --text ./my_app_cpu_profile.pprof
        ```
        You can use commands like `top`, `list <function_name>`, `web` (to generate an SVG and open it) within the `pprof` interactive terminal.
    *   **Other Output Formats:** `pprof` can generate various outputs like callgrind files, PDFs, etc. See `go tool pprof -help` for more options.
