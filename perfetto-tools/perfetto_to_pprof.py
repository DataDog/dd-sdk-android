#!/usr/bin/env python3

#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.

import os
import sys
import argparse
import logging
import struct
from datetime import datetime, timezone
from perfetto.trace_processor import TraceProcessor
import tempfile
import traceback # Added for detailed error logging

# ADDED: Imports for protobuf messages from profile_pb2.py
# Assuming profile_pb2.py is in the same directory and was generated from profile.proto
try:
    from . import profile_pb2 # Relative import for use as a module
except ImportError:
    import profile_pb2 # Direct import for running as a script

# Configure logging
logging.basicConfig(level=logging.INFO, 
                    format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def create_pprof_profile(tp, output_file, pid_filter=None, sampling_interval_ns_arg=None):
    """Create a pprof profile from a Perfetto trace using the protobuf library."""
    try:
        logger.info(f"Querying trace data with PID filter: {pid_filter}")

        # Initialize Profile object
        profile = profile_pb2.Profile()
        
        # String table management
        # The first entry in string_table must be an empty string.
        string_table_map = {"": 0} 
        profile.string_table.append("")

        def get_string_id(s):
            nonlocal string_table_map # Closure for string_table_map
            nonlocal profile          # Closure for profile
            if s is None: s = ""
            s = str(s) # Ensure it's a string
            if s in string_table_map:
                return string_table_map[s]
            idx = len(profile.string_table)
            profile.string_table.append(s)
            string_table_map[s] = idx
            return idx

        # ID anagement for protobuf messages (IDs start at 1)
        next_mapping_id = 1
        next_function_id = 1
        next_location_id = 1
        
        # Caches to avoid duplicating protobuf message objects
        # Keys: tuples of defining attributes that ensure uniqueness
        # Values: the created protobuf message object itself (or its ID if preferred for some caches)
        mappings_cache = {}    # (build_id_str, name_str) -> mapping_pb_object
        functions_cache = {}   # (name_str_idx, filename_str_idx) -> function_pb_object
        locations_cache = {}   # (address, mapping_id, tuple_of_function_ids_in_lines) -> location_pb_object

        actual_sample_duration_ns = sampling_interval_ns_arg

        if actual_sample_duration_ns is None:
            logger.info("Sampling interval not provided via argument. Attempting to infer from trace...")
            # SQL query to find the mode of positive deltas between consecutive samples on the same CPU
            sql_infer_interval = """
            WITH SampleDeltas AS (
                SELECT
                    ts - LAG(ts) OVER (PARTITION BY cpu ORDER BY ts) as delta_ns
                FROM perf_sample
            ),
            PositiveDeltaCounts AS (
                SELECT
                    delta_ns,
                    COUNT(*) AS num_occurrences
                FROM SampleDeltas
                WHERE delta_ns IS NOT NULL AND delta_ns > 0
                GROUP BY delta_ns
            )
            SELECT
                delta_ns
            FROM PositiveDeltaCounts
            ORDER BY num_occurrences DESC, delta_ns ASC
            LIMIT 1;
            """
            try:
                inferred_interval_result = list(tp.query(sql_infer_interval))
                if inferred_interval_result and inferred_interval_result[0].delta_ns > 0:
                    actual_sample_duration_ns = inferred_interval_result[0].delta_ns
                    logger.info(f"Successfully inferred sampling interval from trace: {actual_sample_duration_ns} ns.")
                else:
                    logger.warning("Could not infer a reliable sampling interval from trace data (no suitable deltas found or query returned no result).")
            except Exception as e:
                logger.warning(f"Failed to query/infer sampling interval from trace due to an error: {str(e)}. Traceback: {traceback.format_exc()}")
        
        if actual_sample_duration_ns is None: # If still None after argument check and inference attempt
            actual_sample_duration_ns = 2000000 # Default to 2ms (2000,000 ns)
            logger.warning(
                f"Using default sampling interval: {actual_sample_duration_ns} ns (10ms). "
                f"This may lead to inaccurate profile scaling. "
                f"If this is incorrect, and inference failed or was not attempted, consider providing the interval via --sampling_interval_ns."
            )
        else:
            logger.info(f"Using sampling interval for pprof values: {actual_sample_duration_ns} ns.")


        value_type = profile_pb2.ValueType(type=get_string_id("cpu"), unit=get_string_id("nanoseconds"))
        profile.sample_type.append(value_type)
        # profile.period_type = value_type
        profile.period = actual_sample_duration_ns
        profile.comment.append(get_string_id("Perfetto trace converted to pprof"))

        # 1. Fetch Mappings
        sql_mappings = "SELECT id, name, build_id, start, end FROM stack_profile_mapping"
        mapping_rows = list(tp.query(sql_mappings))
        mappings_by_id = {m.id: m for m in mapping_rows}
        logger.info(f"Fetched {len(mappings_by_id)} mappings.")

        # 2. Fetch Frames
        # sql_frames = "SELECT id, name, rel_pc, mapping FROM stack_profile_frame"
        # We only want to include frames that are in a .jar or .dex file because of the limitation in pprof dashboards. 
        # Apparently using too many frames will cause the dashboard to drop the less important leafs.
        # sql_frames = "SELECT spf.* FROM stack_profile_frame spf JOIN stack_profile_mapping spm ON spf.mapping = spm.id WHERE spm.name LIKE '%.jar' OR spm.name LIKE '%.dex';"
        sql_frames = "SELECT spf.* FROM stack_profile_frame spf JOIN stack_profile_mapping spm ON spf.mapping = spm.id WHERE spm.name NOT LIKE '%.so'"
        frame_rows = list(tp.query(sql_frames))
        frames_by_id = {f.id: f for f in frame_rows}
        logger.info(f"Fetched {len(frames_by_id)} frames.")

        # 3. Fetch Callsites
        sql_callsites = "SELECT id, parent_id, frame_id FROM stack_profile_callsite"
        callsite_rows = list(tp.query(sql_callsites))
        callsites_by_id = {cs.id: cs for cs in callsite_rows}
        logger.info(f"Fetched {len(callsites_by_id)} callsites.")

        # 4. Fetch Threads
        sql_threads = "SELECT utid, name, tid FROM thread WHERE is_main_thread=1" # Perfetto's thread table
        thread_rows = list(tp.query(sql_threads))
        threads_by_utid = {t.utid: t for t in thread_rows} # Keyed by utid for perf_sample
        logger.info(f"Fetched {len(threads_by_utid)} thread entries.")
        
        # 5. Fetch Perf Samples (these are the actual samples we'll process)
        # fetch all samples for selected threads
        sql_perf_sample = "SELECT ts, cpu, callsite_id, utid FROM perf_sample WHERE utid IN ({})".format(','.join(str(utid) for utid in threads_by_utid.keys()))
        

        sql_perf_sample += " ORDER BY ts" # Order by timestamp
        
        perf_sample_rows = list(tp.query(sql_perf_sample))
        
        if not perf_sample_rows:
            logger.warning("No samples found in the trace matching the criteria.")
            # Set minimal time and duration if no samples
            now_nanos = int(datetime.now(timezone.utc).timestamp() * 1e9)
            profile.time_nanos = now_nanos # Changed from pprof
            profile.duration_nanos = 0     # Changed from pprof
            with open(output_file, 'wb') as f:
                f.write(profile.SerializeToString())
            logger.info(f"Wrote empty profile to {output_file}")
            return output_file
        
        min_ts = perf_sample_rows[0].ts
        max_ts = perf_sample_rows[-1].ts
        profile.time_nanos = min_ts
        profile.duration_nanos = max_ts - min_ts
        
        logger.info(f"Processing {len(perf_sample_rows)} perf_sample events...")
        
        processed_samples_count = 0
        for r_sample in perf_sample_rows:
            current_location_ids_for_sample = []
            current_cs_id = r_sample.callsite_id
            
            # Reconstruct stack trace for this perf_sample event
            # The stack trace in pprof is leaf first.
            # We traverse from the leaf callsite upwards via parent_id.
            while current_cs_id is not None and current_cs_id in callsites_by_id:
                callsite = callsites_by_id[current_cs_id]
                if callsite.frame_id not in frames_by_id:
                    logger.warning(f"Frame ID {callsite.frame_id} not found for callsite {current_cs_id}. Skipping frame.")
                    current_cs_id = callsite.parent_id
                    continue
                
                frame = frames_by_id[callsite.frame_id]
                
                if frame.mapping not in mappings_by_id:
                    logger.warning(f"Mapping ID {frame.mapping} not found for frame {frame.id}. Skipping frame.")
                    current_cs_id = callsite.parent_id
                    continue
                
                mapping = mappings_by_id[frame.mapping]

                # Add mapping to pprof writer
                mapping_cache_key = (str(mapping.build_id), str(mapping.name))
                if mapping_cache_key in mappings_cache:
                    pprof_mapping = mappings_cache[mapping_cache_key]
                else:
                    pprof_mapping_filename_str_id = get_string_id(mapping.name)
                    pprof_mapping_build_id_str_id = get_string_id(mapping.build_id)
                    pprof_mapping = profile_pb2.Mapping(
                        id=next_mapping_id,
                        memory_start=mapping.start,
                        memory_limit=mapping.end,
                        file_offset=0, # pprof typically doesn't use this for JIT/sampled stacks without specific file info
                        filename=pprof_mapping_filename_str_id,
                        build_id=pprof_mapping_build_id_str_id,
                        has_functions=True, # Assume functions are present
                        has_filenames=True, # Assume filenames are present (at least the mapping name)
                        has_line_numbers=False, # Will be set to True if symbol info provides line numbers
                        has_inline_frames=False # Not explicitly handled here
                    )
                    profile.mapping.append(pprof_mapping)
                    mappings_cache[mapping_cache_key] = pprof_mapping
                    next_mapping_id += 1
                
                # pprof.mappings is 0-indexed list, mapping_id is 1-indexed
                # The ID for the pprof.Mapping object is pprof_mapping.id
                # The string table index for the mapping's filename is pprof_mapping.filename
                
                # Add function to pprof writer
                func_name_str = frame.name if frame.name else f"[unknown_0x{frame.rel_pc:x}]"
                func_name_str_id = get_string_id(func_name_str)
                # Use the mapping's actual filename string ID (which is pprof_mapping.filename)
                # The function's filename should refer to the source file, 
                # but defaults to the mapping filename if no specific source file is known.
                # For now, let's use the mapping filename as the key component, as source file info isn't integrated yet.
                function_cache_key = (func_name_str_id, pprof_mapping.filename) 

                if function_cache_key in functions_cache:
                    pprof_function = functions_cache[function_cache_key]
                else:
                    pprof_function = profile_pb2.Function(
                        id=next_function_id,
                        name=func_name_str_id,
                        system_name=func_name_str_id, # Or frame.name if different
                        filename=pprof_mapping.filename, # Default to mapping filename, can be refined if symbol info available
                        start_line=0 # Default to 0, can be refined if symbol info available
                    )
                    profile.function.append(pprof_function)
                    functions_cache[function_cache_key] = pprof_function
                    next_function_id += 1
                
                # Add location to pprof writer
                # A Location in pprof represents a unique instruction address and its inlined frames.
                # The key includes the address, the mapping ID, and a tuple of (function_id, line_number) for each line entry.
                # Since we currently create one Line per Location for simplicity:
                location_cache_key = (frame.rel_pc, pprof_mapping.id, pprof_function.id) # Using pprof_function.id for the line
                if location_cache_key in locations_cache:
                    pprof_location = locations_cache[location_cache_key]
                else:
                    # Create the Line object first
                    pprof_line = profile_pb2.Line(
                        function_id=pprof_function.id, 
                        line=0 # Default to 0, can be refined if symbol info available
                    )
                    pprof_location = profile_pb2.Location(
                        id=next_location_id,
                        mapping_id=pprof_mapping.id, # This is the ID of the pprof.Mapping object
                        address=frame.rel_pc,
                        line=[pprof_line]
                    )
                    profile.location.append(pprof_location)
                    locations_cache[location_cache_key] = pprof_location
                    next_location_id += 1
                
                current_location_ids_for_sample.append(pprof_location.id)
                
                current_cs_id = callsite.parent_id # Move to parent callsite

            if current_location_ids_for_sample: # If we successfully built a stack
                thread_info = threads_by_utid.get(r_sample.utid)
                thread_name_str = None
                if thread_info and thread_info.name:
                    thread_name_str = thread_info.name
                elif r_sample.utid is not None: # Fallback if name is not available but utid is
                    thread_name_str = f"Thread ({r_sample.utid})"

                pprof_sample = profile_pb2.Sample(
                    location_id=current_location_ids_for_sample,
                    value=[actual_sample_duration_ns],
                    label=[
                        profile_pb2.Label(key=get_string_id("cpu"), str=get_string_id("nanoseconds")),
                        profile_pb2.Label(key=get_string_id("pid"), str=get_string_id(str(r_sample.utid))),
                        profile_pb2.Label(key=get_string_id("thread_id"), str=get_string_id(str(r_sample.utid))),
                        profile_pb2.Label(key=get_string_id("thread name"), str=get_string_id(thread_name_str))
                    ]
                )
                profile.sample.append(pprof_sample)
                processed_samples_count +=1
        
        with open(output_file, 'wb') as f:
            f.write(profile.SerializeToString())
        
        logger.info(f"Successfully wrote pprof profile to {output_file} with {processed_samples_count} samples.")
        logger.info(f"Profile contains {len(profile.mapping)} mappings, {len(profile.function)} functions, {len(profile.location)} locations, and {len(profile.string_table)} string table entries.")
        return output_file
        
    except Exception as e:
        logger.error(f"Error creating pprof profile: {str(e)}")
        logger.error(traceback.format_exc()) # Log full traceback
        raise

def main():
    parser = argparse.ArgumentParser(description="Convert Perfetto trace to pprof format")
    parser.add_argument('-t', '--trace', required=True, help='Path to the Perfetto trace file')
    parser.add_argument('-o', '--output', default='profile.pb.gz', help='Output filename (e.g., profile.pb.gz or profile.pprof)')
    parser.add_argument('-p', '--pid', type=int, help='Filter samples to a specific PID (default: all PIDs)')
    parser.add_argument('--sampling_interval_ns', type=int, default=None, 
                        help='The sampling interval in nanoseconds (e.g., 10000000 for 10ms). If not set, attempts to infer from trace, then defaults to 10ms.')
    args = parser.parse_args()
    
    output_dir = os.path.dirname(args.output)
    if output_dir and not os.path.exists(output_dir): # Ensure output_dir is not empty string
        os.makedirs(output_dir, exist_ok=True)
    
    output_file = args.output
    # pprof files are often gzipped and may end with .pb.gz or .pprof
    # We are writing uncompressed protobuf data, common tools can handle this.
    # The .pb extension is also common for raw protobuf.
    # Let's not enforce .pb extension, user can name it .pprof or .pb.gz (and then gzip it themselves if needed)
    # if not output_file.endswith(('.pb', '.pprof', '.pb.gz')):
    #     original_name, original_ext = os.path.splitext(output_file)
    #     output_file = f"{original_name}.pprof"
    #     logger.info(f"Changing output filename to {output_file} (common for pprof files)")
    
    try:
        logger.info(f"Opening trace file: {args.trace}")
        # Using a temporary directory for TraceProcessor if it needs to extract files
        # with tempfile.TemporaryDirectory(prefix="perfetto_tp_") as tmpdir:
        #     tp = TraceProcessor(args.trace, temp_dir=tmpdir) # Pass temp_dir
        tp = TraceProcessor(args.trace) # Removed temp_dir argument
        
        if args.pid is not None:
            output_path = create_pprof_profile(tp, output_file, pid_filter=args.pid, sampling_interval_ns_arg=args.sampling_interval_ns)
        else:
            output_path = create_pprof_profile(tp, output_file, sampling_interval_ns_arg=args.sampling_interval_ns)
        
        logger.info(f"Conversion complete. Profile saved to: {output_path}")
        print(output_path)
        return 0
    except Exception as e:
        logger.error(f"Error during conversion: {str(e)}")
        logger.error(traceback.format_exc()) # Log full traceback
        return 1

if __name__ == "__main__":
    sys.exit(main())