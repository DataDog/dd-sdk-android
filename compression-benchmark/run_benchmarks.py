#!/usr/bin/env python3
"""
Compression Benchmark Runner

This script runs the compression benchmarks and outputs formatted tables
comparing Gzip, Zstd JNI, and Zstd Java compression algorithms.

Usage:
    ./run_benchmarks.py              # Run benchmarks and show results
    ./run_benchmarks.py --parse-only # Parse existing results without running
    ./run_benchmarks.py --help       # Show help
"""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


# ANSI color codes
class Colors:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    BLUE = '\033[0;34m'
    YELLOW = '\033[1;33m'
    BOLD = '\033[1m'
    NC = '\033[0m'  # No Color


def print_header(text: str) -> None:
    """Print a formatted header."""
    print(f"\n{Colors.BOLD}{Colors.BLUE}{'=' * 50}{Colors.NC}")
    print(f"{Colors.BOLD}{Colors.BLUE}  {text}{Colors.NC}")
    print(f"{Colors.BOLD}{Colors.BLUE}{'=' * 50}{Colors.NC}\n")


def print_warning(text: str) -> None:
    """Print a warning message."""
    print(f"{Colors.YELLOW}{text}{Colors.NC}")


def print_success(text: str) -> None:
    """Print a success message."""
    print(f"{Colors.GREEN}{text}{Colors.NC}")


def print_error(text: str) -> None:
    """Print an error message."""
    print(f"{Colors.RED}{text}{Colors.NC}")


def format_ns(ns: float) -> str:
    """Format nanoseconds to human-readable string."""
    if ns == 0:
        return "N/A"
    elif ns < 1000:
        return f"{ns:.0f} ns"
    elif ns < 1_000_000:
        return f"{ns / 1000:.1f} μs"
    else:
        return f"{ns / 1_000_000:.2f} ms"


def format_bytes(size: int) -> str:
    """Format bytes to human-readable string."""
    if size < 1024:
        return f"{size} B"
    elif size < 1024 * 1024:
        return f"{size / 1024:.1f} KB"
    else:
        return f"{size / (1024 * 1024):.2f} MB"


def get_script_dir() -> Path:
    """Get the directory containing this script."""
    return Path(__file__).parent.resolve()


def get_project_dir() -> Path:
    """Get the project root directory."""
    return get_script_dir().parent


def find_benchmark_output_dir() -> Optional[Path]:
    """Find the benchmark output directory."""
    base_dir = get_script_dir() / "build" / "outputs" / "connected_android_test_additional_output"
    base_dir = base_dir / "releaseAndroidTest" / "connected"
    
    if not base_dir.exists():
        return None
    
    # Find the first device directory
    for child in base_dir.iterdir():
        if child.is_dir():
            return child
    
    return None


def run_benchmarks() -> bool:
    """Run the Gradle benchmark task."""
    project_dir = get_project_dir()
    
    print(f"Running benchmarks from: {project_dir}")
    print_warning("Note: For accurate results, run on a real device, not an emulator.\n")
    
    try:
        result = subprocess.run(
            ["./gradlew", ":compression-benchmark:connectedReleaseAndroidTest", "--no-daemon"],
            cwd=project_dir,
            capture_output=False,
            text=True
        )
        
        if result.returncode == 0:
            print_success("\nBenchmarks completed successfully!")
            return True
        else:
            print_warning("\nSome benchmarks may have failed. Parsing available results...")
            return True  # Still try to parse results
            
    except FileNotFoundError:
        print_error("Error: gradlew not found. Make sure you're in the project directory.")
        return False
    except Exception as e:
        print_error(f"Error running benchmarks: {e}")
        return False


def parse_benchmark_json(json_path: Path) -> Dict:
    """Parse the benchmark JSON file and extract results."""
    results = {
        'speed': {},  # payload -> algorithm -> {median, min, max}
        'device': None
    }
    
    try:
        with open(json_path) as f:
            data = json.load(f)
    except Exception as e:
        print_error(f"Error reading benchmark JSON: {e}")
        return results
    
    # Extract device info
    context = data.get('context', {})
    build = context.get('build', {})
    results['device'] = f"{build.get('model', 'Unknown')} (SDK {build.get('version', {}).get('sdk', '?')})"
    
    # Parse benchmarks
    for benchmark in data.get('benchmarks', []):
        name = benchmark.get('name', '')
        class_name = benchmark.get('className', '')
        
        # Determine algorithm
        if 'GzipBenchmark' in class_name:
            algo = 'Gzip'
        elif 'ZstdJniBenchmark' in class_name:
            algo = 'Zstd JNI'
        elif 'ZstdJavaBenchmark' in class_name:
            algo = 'Zstd Java'
        else:
            continue
        
        # Determine payload type
        if 'smallPayload' in name:
            payload = 'Small (~885 B)'
        elif 'mediumPayload' in name:
            payload = 'Medium (~4.3 KB)'
        elif 'largePayload' in name:
            payload = 'Large (~37 KB)'
        elif 'logsPayload' in name:
            payload = 'Logs (~1.2 KB)'
        else:
            continue
        
        # Extract timing metrics
        time_ns = benchmark.get('metrics', {}).get('timeNs', {})
        
        if payload not in results['speed']:
            results['speed'][payload] = {}
        
        results['speed'][payload][algo] = {
            'median': time_ns.get('median', 0),
            'min': time_ns.get('minimum', 0),
            'max': time_ns.get('maximum', 0)
        }
    
    return results


def get_compression_ratios_from_device() -> List[Dict]:
    """Get compression ratio results from device file or logcat."""
    ratios = []
    
    # First try to read from the file saved on device
    try:
        # Try to pull the file from the device
        result = subprocess.run(
            ['adb', 'shell', 'cat', '/storage/emulated/0/Android/data/com.datadog.benchmark.compression.test/files/compression_ratios.json'],
            capture_output=True,
            text=True,
            timeout=10
        )
        
        if result.returncode == 0 and result.stdout.strip():
            try:
                ratios = json.loads(result.stdout)
                if ratios:
                    return ratios
            except json.JSONDecodeError:
                pass
    except Exception:
        pass
    
    # Fallback to logcat
    try:
        result = subprocess.run(
            ['adb', 'logcat', '-d'],
            capture_output=True,
            text=True,
            timeout=10
        )
        
        for line in result.stdout.split('\n'):
            if 'CompressionRatio' in line and '{' in line:
                match = re.search(r'\{.*\}', line)
                if match:
                    try:
                        data = json.loads(match.group())
                        if 'name' in data and 'original' in data:
                            ratios.append(data)
                    except json.JSONDecodeError:
                        pass
        
        # Return the last 4 entries (most recent run)
        return ratios[-4:] if len(ratios) >= 4 else ratios
        
    except subprocess.TimeoutExpired:
        print_warning("Timeout reading logcat")
        return []
    except FileNotFoundError:
        print_warning("adb not found. Cannot read compression ratios from device.")
        return []
    except Exception as e:
        print_warning(f"Error reading compression ratios: {e}")
        return []


def print_speed_table(speed_results: Dict) -> None:
    """Print the compression speed results table."""
    print_header("COMPRESSION SPEED RESULTS")
    
    if not speed_results:
        print_warning("No speed benchmark results available.")
        return
    
    algos = ['Gzip', 'Zstd JNI', 'Zstd Java']
    payload_order = ['Small (~885 B)', 'Logs (~1.2 KB)', 'Medium (~4.3 KB)', 'Large (~37 KB)']
    
    # Print table
    header = f"{'Payload':<20} | {'Gzip':>12} | {'Zstd JNI':>12} | {'Zstd Java':>12}"
    separator = "-" * len(header)
    
    print(separator)
    print(header)
    print(separator)
    
    for payload in payload_order:
        if payload in speed_results:
            row = f"{payload:<20}"
            for algo in algos:
                if algo in speed_results[payload]:
                    median = speed_results[payload][algo]['median']
                    row += f" | {format_ns(median):>12}"
                else:
                    row += f" | {'N/A':>12}"
            print(row)
    
    print(separator)
    
    # Print detailed stats
    print("\nDetailed Statistics (median / min / max):")
    print(separator)
    
    for payload in payload_order:
        if payload in speed_results:
            print(f"\n{payload}:")
            for algo in algos:
                if algo in speed_results[payload]:
                    stats = speed_results[payload][algo]
                    print(f"  {algo:<12}: {format_ns(stats['median'])} / {format_ns(stats['min'])} / {format_ns(stats['max'])}")


def print_ratio_table(ratios: List[Dict]) -> None:
    """Print the compression ratio results table."""
    print_header("COMPRESSION RATIO RESULTS")
    
    if not ratios:
        print_warning("No compression ratio results available.")
        print("Run the benchmarks first to generate ratio data.")
        return
    
    header = f"{'Payload':<15} | {'Original':>10} | {'Gzip':>14} | {'Zstd JNI':>14} | {'Zstd Java':>14}"
    separator = "-" * len(header)
    
    print(separator)
    print(header)
    print(separator)
    
    totals = {'original': 0, 'gzip': 0, 'zstd_jni': 0, 'zstd_java': 0}
    
    for entry in ratios:
        name = entry['name']
        original = entry['original']
        gzip = entry['gzip']
        zstd_jni = entry['zstd_jni']
        zstd_java = entry['zstd_java']
        
        totals['original'] += original
        totals['gzip'] += gzip
        totals['zstd_jni'] += zstd_jni
        totals['zstd_java'] += zstd_java
        
        gzip_pct = (gzip / original) * 100
        zstd_jni_pct = (zstd_jni / original) * 100
        zstd_java_pct = (zstd_java / original) * 100
        
        row = (f"{name:<15} | {original:>10} | "
               f"{gzip:>6} ({gzip_pct:>5.1f}%) | "
               f"{zstd_jni:>6} ({zstd_jni_pct:>5.1f}%) | "
               f"{zstd_java:>6} ({zstd_java_pct:>5.1f}%)")
        print(row)
    
    print(separator)
    
    # Print totals/averages
    if totals['original'] > 0:
        avg_gzip = (totals['gzip'] / totals['original']) * 100
        avg_zstd_jni = (totals['zstd_jni'] / totals['original']) * 100
        avg_zstd_java = (totals['zstd_java'] / totals['original']) * 100
        
        print()
        row = (f"{'TOTAL':<15} | {totals['original']:>10} | "
               f"{totals['gzip']:>6} ({avg_gzip:>5.1f}%) | "
               f"{totals['zstd_jni']:>6} ({avg_zstd_jni:>5.1f}%) | "
               f"{totals['zstd_java']:>6} ({avg_zstd_java:>5.1f}%)")
        print(row)
        print(separator)
        
        print("\nSummary (lower is better):")
        print(f"  Gzip average compression ratio:      {avg_gzip:.2f}%")
        print(f"  Zstd JNI average compression ratio:  {avg_zstd_jni:.2f}%")
        print(f"  Zstd Java average compression ratio: {avg_zstd_java:.2f}%")


def main():
    parser = argparse.ArgumentParser(
        description="Run compression benchmarks and display results",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  ./run_benchmarks.py              Run benchmarks and show results
  ./run_benchmarks.py --parse-only Parse existing results only
        """
    )
    parser.add_argument(
        '--parse-only',
        action='store_true',
        help='Parse existing results without running benchmarks'
    )
    
    args = parser.parse_args()
    
    print_header("Compression Benchmark Runner")
    
    # Run benchmarks if not parse-only
    if not args.parse_only:
        if not run_benchmarks():
            sys.exit(1)
    else:
        print_warning("Skipping benchmark run, parsing existing results...")
    
    # Find output directory
    output_dir = find_benchmark_output_dir()
    if not output_dir:
        print_error("No benchmark output found.")
        print("Run the benchmarks first: ./run_benchmarks.py")
        sys.exit(1)
    
    device_name = output_dir.name
    print(f"\n{Colors.BOLD}Device:{Colors.NC} {device_name}")
    
    # Parse benchmark JSON
    benchmark_json = output_dir / "com_datadog_benchmark_compression_test-benchmarkData.json"
    
    if benchmark_json.exists():
        results = parse_benchmark_json(benchmark_json)
        print_speed_table(results['speed'])
    else:
        print_warning(f"Benchmark JSON not found: {benchmark_json}")
        print_speed_table({})
    
    # Get compression ratios from device
    ratios = get_compression_ratios_from_device()
    print_ratio_table(ratios)
    
    # Print footer
    print_header("BENCHMARK COMPLETE")
    
    if benchmark_json.exists():
        print(f"Full benchmark JSON:\n  {benchmark_json}")
    
    report_path = get_script_dir() / "build" / "outputs" / "reports" / "androidTests" / "connected" / "release" / "index.html"
    if report_path.exists():
        print(f"\nHTML Report:\n  {report_path}")
    
    print()
    print_warning("Note: For production benchmarks, run on a real device (not emulator)")
    print_warning("      and remove the 'suppressErrors' line from build.gradle.kts")


if __name__ == '__main__':
    main()
