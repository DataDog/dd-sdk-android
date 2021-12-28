#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2019-Present Datadog, Inc

import os
import sys
import time
from argparse import ArgumentParser, Namespace
from xml.etree import ElementTree
from datadog import initialize, api

def parse_arguments(args: list) -> Namespace:
    parser = ArgumentParser()

    parser.add_argument("-f", "--flavor", required=True)
    parser.add_argument("-p", "--prefix", required=True)

    return parser.parse_args(args)

def report_coverage(module_name: str, report_file: str):
    root = ElementTree.parse(report_file).getroot()

    now = time.time()

    for counter in root.findall('counter'):
        counter_type = counter.get('type').lower()
        counter_covered = int(counter.get('covered'))
        counter_missed = int(counter.get('missed'))
        counter_total = counter_covered + counter_missed
        coverage = float(counter_covered) / float(counter_total)
        metric_name = f'ci.coverage.{counter_type}'

        metrics = [
            {'metric': metric_name, 'type': 'gauge', 'points': (now, coverage), 'tags': [f'module:{module_name}']}
        ]
        response = api.Metric.send(metrics=metrics)
        print(response)

def compute_coverage_module(module_name: str, flavor: str) -> int:
    print(f"Checking coverage for {module_name}")

    jacoco_report_dir = f'jacocoTest{flavor}UnitTestReport'
    jacoco_report_file = f'jacocoTest{flavor}UnitTestReport.xml'
    jacoco_file = os.path.join(module_name, 'build', 'jacoco', jacoco_report_dir, jacoco_report_file)

    if os.path.exists(jacoco_file):
        print(f"Found jacoco report at {jacoco_file}")
        report_coverage(module_name, jacoco_file)
    else:
        print(f"No jacoco report found at {jacoco_file}")

def run_main() -> int:
    cli_args = parse_arguments(sys.argv[1:])

    # Initialize DD SDK
    initialize()

    for subdir in os.listdir('.'):
        if os.path.isdir(subdir) and subdir.startswith(cli_args.prefix):
            compute_coverage_module(subdir, cli_args.flavor)

    return 0


if __name__ == "__main__":
    sys.exit(run_main())
