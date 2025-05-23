#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.

from flask import Flask, request, Response
import requests
import os
import subprocess
import tempfile
from werkzeug.utils import secure_filename
import logging
from perfetto.trace_processor import TraceProcessor
from datetime import datetime, timezone
import json
import shlex
from perfetto_to_pprof import create_pprof_profile
import uuid

app = Flask(__name__)

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Configuration
TARGET_ENDPOINT = os.getenv('TARGET_ENDPOINT', 'https://intake.profile.datadoghq.com/api/v2/profile')
DEBUG_MODE = os.getenv('DEBUG_MODE', 'false').lower() == 'true'  # Flag to control whether to forward or just log
PPROF_OUTPUT_DIR = 'pprof_converted_files' # Directory for storing converted pprof files

# Ensure pprof output directory exists
os.makedirs(PPROF_OUTPUT_DIR, exist_ok=True)
logger.info(f"Converted pprof files will be stored in: {os.path.abspath(PPROF_OUTPUT_DIR)}")

def generate_curl_command(url, headers, files, data):
    """Generate a curl command string that represents the request."""
    cmd = ['curl', '-i']
    
    # Add headers
    for key, value in headers.items():
        cmd.extend(['-H', f'"{key}: {value}"'])
    
    # Add files
    for key, (filename, file_obj, content_type) in files.items():
        cmd.extend(['-F', f'"{key}=@{filename}"'])
    
    # Add event data
    if data and 'event' in data:
        cmd.extend(['-F', '"event=@-;type=application/json"'])
    
    # Add URL
    cmd.append(url)
    
    # Create the complete command with proper line breaks
    curl_cmd = ' \\\n  '.join(cmd)
    
    # Add the event JSON as a heredoc with proper formatting
    if data and 'event' in data:
        # Parse and pretty print the JSON
        try:
            json_data = json.loads(data['event'])
            pretty_json = json.dumps(json_data, indent=2)
            curl_cmd += f" <<END\n{pretty_json}\nEND"
        except json.JSONDecodeError:
            # If JSON parsing fails, use the original string
            curl_cmd += f" <<END\n{data['event']}\nEND"
    
    return curl_cmd

def ns_to_iso(ns: int) -> str:
    """Convert nanoseconds to ISO-8601 format in seconds."""
    # Convert nanoseconds to seconds
    seconds = ns / 1_000_000_000
    # Create datetime object in UTC
    dt = datetime.fromtimestamp(seconds, tz=timezone.utc)
    # Format as ISO-8601 with seconds precision and 'Z' suffix
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")

def get_trace_timestamps(trace_path):
    """Extract start and end timestamps from a trace file using Perfetto API."""
    try:
        # Create TraceProcessor with the file path
        tp = TraceProcessor(trace_path)
        
        # get the trace clock delta
        try:
            # First, let's see what clock types are available
            clock_types_query = """
            SELECT DISTINCT clock_name 
            FROM clock_snapshot 
            ORDER BY clock_name;
            """
            clock_types = tp.query(clock_types_query)
            logger.info("Available clock types:")
            for clock in clock_types:
                logger.info(f"  - {clock.clock_name}")

            # Now try to get the delta using a more general approach
            delta_result = tp.query("""
            WITH clock_values AS (
                SELECT 
                    snapshot_id,
                    clock_name,
                    clock_value
                FROM clock_snapshot
                WHERE clock_name IN ('CLOCK_REALTIME', 'CLOCK_MONOTONIC', 'REALTIME', 'MONOTONIC')
                ORDER BY snapshot_id
            ),
            clock_pairs AS (
                SELECT 
                    snapshot_id,
                    MAX(CASE WHEN clock_name IN ('CLOCK_REALTIME', 'REALTIME') THEN clock_value END) as realtime,
                    MAX(CASE WHEN clock_name IN ('CLOCK_MONOTONIC', 'MONOTONIC') THEN clock_value END) as monotonic
                FROM clock_values
                GROUP BY snapshot_id
                HAVING realtime IS NOT NULL AND monotonic IS NOT NULL
            )
            SELECT (realtime - monotonic) as offset_ns
            FROM clock_pairs
            ORDER BY snapshot_id
            LIMIT 1;
            """)
            
            # Check if we got any results
            try:
                delta = next(delta_result).offset_ns
                logger.info(f"Trace clock delta: {delta} ns")
            except StopIteration:
                logger.warning("No clock delta found in the trace, using 0 as default")
                delta = 0
                
        except Exception as e:
            logger.error(f"Error getting clock delta: {str(e)}")
            logger.error("Using 0 as default clock delta")
            delta = 0
            
        # Query to get the first and last timestamp in the trace
        query = """
        SELECT  MIN(ts) AS start_ns,
                MAX(ts) AS end_ns
        FROM    perf_sample 
        """
        result = tp.query(query)
        row = next(result)
        
        logger.info(f"Raw trace timestamps (ns): {row.start_ns} to {row.end_ns}")
        
        # Convert nanoseconds to ISO format
        start_iso = ns_to_iso(row.start_ns + delta)
        end_iso = ns_to_iso(row.end_ns + delta)
        
        logger.info(f"Trace time range: {start_iso} to {end_iso}")
        return start_iso, end_iso
    except Exception as e:
        logger.error(f"Error getting trace timestamps: {str(e)}")
        raise

@app.route('/api/v2/profile', methods=['POST'])
def forward_profile():
    logger.info(f"Received request from client: {request.headers}")
    input_path = None # Path to the temporary input trace file
    converted_path = None # Path to the temporary output pprof file
    tp_instance_for_conversion = None # TraceProcessor instance for the conversion

    try:
        # Get the API key from request headers
        api_key = request.headers.get('DD-API-KEY')
        if not api_key:
            return Response('Missing DD-API-KEY header', status=401)

        # For debugging: Log available files and form fields
        print(f"Received files: {list(request.files.keys())}")
        print(f"Received form data: {list(request.form.keys())}")

        # MODIFIED: Check for 'cpu.pprof' in files, and 'event' in files OR form
        cpu_pprof_present = 'cpu.pprof' in request.files
        event_in_files = 'event' in request.files
        event_in_form = 'event' in request.form

        if not cpu_pprof_present or not (event_in_files or event_in_form):
            print(f"Debug: 'cpu.pprof' in request.files: {cpu_pprof_present}")
            print(f"Debug: 'event' in request.files: {event_in_files}")
            print(f"Debug: 'event' in request.form: {event_in_form}")
            return Response('Missing required cpu.pprof file, or event data in files/form', status=400)

        # Get the pprof file
        pprof_file = request.files['cpu.pprof']

        # MODIFIED: Get event from request.files or request.form
        event_json = None
        if event_in_files:
            print("Found 'event' in request.files")
            event_file_obj = request.files['event']
            event_json = event_file_obj.read().decode('utf-8')
        elif event_in_form:
            print("Found 'event' in request.form")
            event_json = request.form['event']

        # Save the uploaded file temporarily
        with tempfile.NamedTemporaryFile(delete=False, suffix=".perfetto-trace") as temp_input:
            pprof_file.save(temp_input.name)
            input_path = temp_input.name
        logger.info(f"Saved uploaded Perfetto trace to: {input_path}")

        try:
            # Get trace timestamps
            start_iso, end_iso = get_trace_timestamps(input_path)
            
            # Parse and update the event JSON
            try:
                event_data = json.loads(event_json)
                event_data["start"] = start_iso
                event_data["end"] = end_iso
                updated_event_json = json.dumps(event_data)
                # event_data = json.loads(event_json)
                # # Update the event JSON structure to match the Python example
                # payload = {
                #     "attachments": ["cpu.pprof"],
                #     "tags_profiler": "service:test-service-perfetto,version:1.0.0",
                #     "start": start_iso,
                #     "end": end_iso,
                #     "family": "go",
                #     "version": "4"
                # }
                # updated_event_json = json.dumps(payload)
                logger.info(f"Updated event JSON: {updated_event_json}")
            except json.JSONDecodeError as e:
                logger.error(f"Error parsing event JSON: {str(e)}")
                return Response('Invalid event JSON format', status=400)

            # Convert the trace file to pprof format using python-based script
            try:
                # Generate a unique filename for the pprof file in the designated directory
                if not os.path.exists(PPROF_OUTPUT_DIR):
                    os.makedirs(PPROF_OUTPUT_DIR)
                output_filename = f"profile_{datetime.now().strftime('%Y%m%d_%H%M%S_%f')}_{uuid.uuid4().hex[:8]}.pprof"
                converted_path = os.path.join(PPROF_OUTPUT_DIR, output_filename)
                logger.info(f"Pprof output will be saved to: {converted_path}")

                tp_instance_for_conversion = TraceProcessor(trace=input_path)
                logger.info(f"Initialized TraceProcessor for conversion from {input_path}")
                
                pid_val = None
                pid_str = request.form.get('pid')
                if pid_str:
                    try:
                        pid_val = int(pid_str)
                        logger.info(f"Using PID filter for conversion: {pid_val}")
                    except ValueError:
                        logger.warning(f"Invalid PID value '{pid_str}' for conversion. Ignoring PID filter.")
                
                create_pprof_profile(tp_instance_for_conversion, converted_path, pid_filter=pid_val)
                logger.info(f"Python-based pprof conversion successful: {converted_path}")

            except Exception as conversion_exception:
                logger.error(f"Error during python-based pprof conversion: {conversion_exception}")
                import traceback # Ensure traceback is imported here if not globally
                logger.error(traceback.format_exc())
                # converted_path might exist but be incomplete/invalid. Cleanup is handled in the main finally.
                raise # Re-raise to be caught by the outer handler to return 500

            # Debug file info
            file_size = os.path.getsize(converted_path)
            logger.info(f"Converted file {converted_path} size: {file_size} bytes")

            # Read file content directly
            with open(converted_path, 'rb') as fh:
                file_content = fh.read()
                logger.info(f"Read {len(file_content)} bytes from file")
                
                # Create a new multipart form for forwarding
                files = {
                    "cpu.pprof": (os.path.basename(converted_path), file_content, "application/octet-stream"),
                    "event": ("event.json", updated_event_json, "application/json")
                }
                
                # Log more details about the request
                logger.info(f"Attaching file as binary data of {len(file_content)} bytes")
                logger.info(f"File content starts with: {file_content[:20].hex()}")

                # Forward the request to the target endpoint
                headers = {
                    'DD-API-KEY': api_key,
                    'DD-EVP-ORIGIN': request.headers.get('DD-EVP-ORIGIN', ''),
                    'DD-EVP-ORIGIN-VERSION': request.headers.get('DD-EVP-ORIGIN-VERSION', '')
                }

                # Generate and log the curl command
                curl_cmd = generate_curl_command(TARGET_ENDPOINT, headers, files, None)
                logger.info("Equivalent curl command:")
                logger.info(curl_cmd)

                if DEBUG_MODE:
                    # Just log the request details in debug mode
                    logger.info("DEBUG_MODE enabled - logging request details instead of forwarding:")
                    logger.info(f"Target endpoint: {TARGET_ENDPOINT}")
                    logger.info(f"Headers: {headers}")
                    logger.info(f"Event data: {updated_event_json}")
                    logger.info(f"File: {pprof_file.filename}")
                    return Response('Request logged (DEBUG_MODE enabled)', status=200)
                else:
                    # Forward the request to the target endpoint
                    response = requests.post(
                        TARGET_ENDPOINT,
                        files=files,
                        headers=headers
                    )

                    # Log the response
                    logger.info(f"Forwarded request to {TARGET_ENDPOINT}. Status: {response.status_code}")
                    
                    # Return the response from the target endpoint
                    return Response(
                        response.content,
                        status=response.status_code,
                        content_type=response.headers.get('content-type', 'application/json')
                    )

        except Exception as inner_exception: # Catch exceptions from timestamping, event parsing, or conversion
            # This ensures that if conversion (or steps before it) fails,
            # tp_instance_for_conversion (if created) and files are cleaned up by the outer finally.
            # Re-raise to be handled by the main exception handler for consistent 500 response.
            raise inner_exception

    except Exception as e:
        logger.error(f"Error processing request: {str(e)}")
        import traceback # Import for logging full traceback
        logger.error(traceback.format_exc())
        return Response(f"Internal server error: {str(e)}", status=500)

    finally:
        # Clean up TraceProcessor for conversion
        if tp_instance_for_conversion:
            try:
                tp_instance_for_conversion.close()
                logger.info(f"Closed TraceProcessor for conversion (path: {input_path}).")
            except Exception as e_tp_close:
                logger.error(f"Error closing TraceProcessor for conversion: {e_tp_close}")
        
        # Clean up temporary files
        if input_path and os.path.exists(input_path):
            try:
                os.remove(input_path)
                logger.info(f"Removed temporary input trace: {input_path}")
            except Exception as e_remove_input:
                logger.error(f"Error removing temporary input trace {input_path}: {str(e_remove_input)}")
        
        # Do NOT remove the converted_path, as we want to keep it
        # if converted_path and os.path.exists(converted_path):
        #     try:
        #         os.remove(converted_path)
        #         logger.info(f"Removed temporary pprof file: {converted_path}")
        #     except Exception as e_remove_pprof:
        #         logger.error(f"Error removing temporary pprof file {converted_path}: {str(e_remove_pprof)}")

if __name__ == '__main__':
    port = int(os.getenv('PORT', 8080))
    app.run(host='0.0.0.0', port=port) 