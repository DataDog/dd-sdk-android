#!/usr/bin/env python3

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Convert a streamed `am instrument -r` result into JUnit XML."""

import argparse
import re
import sys
import time
import xml.etree.ElementTree as ElementTree
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, TextIO, Tuple


STATUS_START = 1
STATUS_OK = 0
STATUS_ERROR = -1
STATUS_FAILURE = -2
STATUS_IGNORED = -3
STATUS_ASSUMPTION_FAILURE = -4

STATUS_PATTERN = re.compile(r"^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$")
STATUS_CODE_PATTERN = re.compile(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)$")
FINAL_CODE_PATTERN = re.compile(r"^INSTRUMENTATION_CODE: (-?\d+)$")
INVALID_XML_CHARACTERS = re.compile(
    "[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x84\x86-\x9f]"
)


@dataclass
class TestResult:
    class_name: str
    test_name: str
    sequence: str
    started_at: float
    duration: float = 0.0
    outcome: str = "running"
    details: str = ""


class InstrumentationParser:
    def __init__(self) -> None:
        self.status: Dict[str, str] = {}
        self.last_status_key: Optional[str] = None
        self.tests: List[TestResult] = []
        self.active_tests: Dict[Tuple[str, str, str], TestResult] = {}
        self.final_code: Optional[int] = None
        self.runner_failures: List[str] = []

    def consume(self, line: str) -> None:
        status_match = STATUS_PATTERN.match(line.rstrip("\r\n"))
        if status_match:
            key, value = status_match.groups()
            self.status[key] = value
            self.last_status_key = key
            return

        status_code_match = STATUS_CODE_PATTERN.match(line.rstrip("\r\n"))
        if status_code_match:
            self._complete_status(int(status_code_match.group(1)))
            self.status = {}
            self.last_status_key = None
            return

        final_code_match = FINAL_CODE_PATTERN.match(line.rstrip("\r\n"))
        if final_code_match:
            self.final_code = int(final_code_match.group(1))
            return

        if line.startswith("INSTRUMENTATION_FAILED:"):
            self.runner_failures.append(line.partition(":")[2].strip())
            return

        if self.last_status_key is not None:
            continuation = line.rstrip("\r\n")
            self.status[self.last_status_key] += "\n" + continuation

    def finish(self) -> None:
        for test in self.active_tests.values():
            test.duration = time.monotonic() - test.started_at
            test.outcome = "error"
            test.details = "Instrumentation ended before this test completed."
        self.active_tests.clear()

        if self.final_code is None:
            self.runner_failures.append("Instrumentation did not report a final result code.")
        elif self.final_code != -1:
            self.runner_failures.append(
                f"Instrumentation finished with result code {self.final_code}."
            )

    def has_failures(self) -> bool:
        return bool(self.runner_failures) or any(
            test.outcome in {"failure", "error"} for test in self.tests
        )

    def _complete_status(self, code: int) -> None:
        class_name = self.status.get("class", "unknown")
        test_name = self.status.get("test")
        sequence = self.status.get("current", "")

        if not test_name:
            if code < 0:
                self.runner_failures.append(self._details())
            return

        key = (class_name, test_name, sequence)
        if code == STATUS_START:
            test = TestResult(class_name, test_name, sequence, time.monotonic())
            self.tests.append(test)
            self.active_tests[key] = test
            return

        if code not in {
            STATUS_OK,
            STATUS_ERROR,
            STATUS_FAILURE,
            STATUS_IGNORED,
            STATUS_ASSUMPTION_FAILURE,
        }:
            return

        test = self.active_tests.pop(key, None)
        if test is None:
            test = TestResult(class_name, test_name, sequence, time.monotonic())
            self.tests.append(test)

        test.duration = time.monotonic() - test.started_at
        test.details = self._details()
        test.outcome = {
            STATUS_OK: "passed",
            STATUS_ERROR: "error",
            STATUS_FAILURE: "failure",
            STATUS_IGNORED: "skipped",
            STATUS_ASSUMPTION_FAILURE: "skipped",
        }[code]

    def _details(self) -> str:
        return self.status.get("stack") or self.status.get("stream", "").strip()


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert streamed Android instrumentation output to JUnit XML."
    )
    parser.add_argument(
        "--raw-output",
        required=True,
        type=Path,
        help="Path where the original instrumentation output will be stored.",
    )
    parser.add_argument(
        "--junit-output",
        required=True,
        type=Path,
        help="Path where the generated JUnit XML will be stored.",
    )
    return parser.parse_args()


def sanitized(value: str) -> str:
    return INVALID_XML_CHARACTERS.sub("", value)


def write_junit(parser: InstrumentationParser, output_path: Path) -> None:
    suites_element = ElementTree.Element("testsuites")
    grouped_tests: Dict[str, List[TestResult]] = defaultdict(list)
    for test in parser.tests:
        grouped_tests[test.class_name].append(test)

    total_duration = 0.0
    total_failures = 0
    total_errors = len(parser.runner_failures)
    total_skipped = 0

    for class_name, tests in grouped_tests.items():
        failures = sum(test.outcome == "failure" for test in tests)
        errors = sum(test.outcome == "error" for test in tests)
        skipped = sum(test.outcome == "skipped" for test in tests)
        duration = sum(test.duration for test in tests)

        suite_element = ElementTree.SubElement(
            suites_element,
            "testsuite",
            {
                "name": sanitized(class_name),
                "tests": str(len(tests)),
                "failures": str(failures),
                "errors": str(errors),
                "skipped": str(skipped),
                "time": f"{duration:.3f}",
            },
        )

        for test in tests:
            case_element = ElementTree.SubElement(
                suite_element,
                "testcase",
                {
                    "classname": sanitized(test.class_name),
                    "name": sanitized(test.test_name),
                    "time": f"{test.duration:.3f}",
                },
            )
            if test.outcome == "failure":
                failure_element = ElementTree.SubElement(
                    case_element,
                    "failure",
                    {"message": sanitized(first_line(test.details) or "Test failed")},
                )
                failure_element.text = sanitized(test.details)
            elif test.outcome == "error":
                error_element = ElementTree.SubElement(
                    case_element,
                    "error",
                    {"message": sanitized(first_line(test.details) or "Test errored")},
                )
                error_element.text = sanitized(test.details)
            elif test.outcome == "skipped":
                skipped_element = ElementTree.SubElement(case_element, "skipped")
                skipped_element.text = sanitized(test.details)

        total_duration += duration
        total_failures += failures
        total_errors += errors
        total_skipped += skipped

    if parser.runner_failures:
        suite_element = ElementTree.SubElement(
            suites_element,
            "testsuite",
            {
                "name": "instrumentation-runner",
                "tests": "1",
                "failures": "0",
                "errors": "1",
                "skipped": "0",
                "time": "0.000",
            },
        )
        case_element = ElementTree.SubElement(
            suite_element,
            "testcase",
            {"classname": "instrumentation-runner", "name": "execution", "time": "0.000"},
        )
        details = "\n".join(parser.runner_failures)
        error_element = ElementTree.SubElement(
            case_element,
            "error",
            {"message": sanitized(first_line(details) or "Instrumentation failed")},
        )
        error_element.text = sanitized(details)

    suites_element.set("tests", str(len(parser.tests) + bool(parser.runner_failures)))
    suites_element.set("failures", str(total_failures))
    suites_element.set("errors", str(total_errors))
    suites_element.set("skipped", str(total_skipped))
    suites_element.set("time", f"{total_duration:.3f}")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    ElementTree.ElementTree(suites_element).write(
        output_path,
        encoding="utf-8",
        xml_declaration=True,
    )


def first_line(value: str) -> str:
    return value.splitlines()[0] if value else ""


def stream_input(parser: InstrumentationParser, raw_output: TextIO) -> None:
    for line in sys.stdin:
        sys.stdout.write(line)
        sys.stdout.flush()
        raw_output.write(line)
        raw_output.flush()
        parser.consume(line)


def main() -> int:
    arguments = parse_arguments()
    if arguments.raw_output.resolve() == arguments.junit_output.resolve():
        raise ValueError("Raw output and JUnit output paths must be different.")

    arguments.raw_output.parent.mkdir(parents=True, exist_ok=True)
    instrumentation_parser = InstrumentationParser()
    with arguments.raw_output.open("w", encoding="utf-8") as raw_output:
        stream_input(instrumentation_parser, raw_output)

    instrumentation_parser.finish()
    write_junit(instrumentation_parser, arguments.junit_output)
    return 1 if instrumentation_parser.has_failures() else 0


if __name__ == "__main__":
    sys.exit(main())
