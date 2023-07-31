#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.

import sys
from argparse import ArgumentParser, Namespace, ArgumentTypeError
from src.feature import Feature
from src.api_surface import ApiSurface
from src.constants import API_SURFACE_PATHS
from src.constants import NIGHTLY_TESTS_DIRECTORY_PATH
from src.constants import NIGHTLY_TESTS_PACKAGE
from src.constants import IGNORED_TYPES


def restricted_float(x):
    try:
        x = float(x)
    except ValueError:
        raise ArgumentTypeError(f"{x} not a floating-point literal")

    if x < 0.0 or x > 1.0:
        raise ArgumentTypeError(f"{x} not in range [0.0, 1.0]")
    return x


def parse_arguments(args: list) -> Namespace:
    parser = ArgumentParser()

    parser.add_argument("-td", "--tests-directory-path",
                        default=NIGHTLY_TESTS_DIRECTORY_PATH,
                        help="The path to the nightly tests directory.")
    parser.add_argument("-as", "--api-surface-paths",
                        default=API_SURFACE_PATHS,
                        nargs="+",
                        help="The path to the api surface file.")
    parser.add_argument("-t", "--test-coverage-threshold-to-assert",
                        type=restricted_float,
                        required=True,
                        help="The test coverage threshold. It takes values from 0 to 1.")
    parser.add_argument("-i", "--ignored-entities",
                        nargs="+",
                        default=IGNORED_TYPES,
                        help="The entities to be ignored when traversing the apiSurface file.")

    return parser.parse_args(args)


def resolve_coverage(to_cover_test_cases: set, covered_test_cases: set) -> float:
    total_amount = len(to_cover_test_cases)
    if total_amount <= 0:
        return 1
    intersection = to_cover_test_cases.intersection(covered_test_cases)
    covered = len(intersection)
    return round(covered / total_amount, 2)


def fetch_all_apis(
        api_surface_paths: [str],
        ignored_types: [str]) -> set:
    uncovered_tests_cases = set()
    api_surfaces = list(
        map(lambda api_surface_path: ApiSurface(api_surface_path, ignored_types), api_surface_paths))
    for api_surface in api_surfaces:
        uncovered_tests_cases.update(api_surface.fetch_testable_methods())
    return uncovered_tests_cases


def fetch_already_covered_apis(tests_directory_path: str) -> set:
    covered_apis = set()
    features = [
        Feature(f'{tests_directory_path}/{NIGHTLY_TESTS_PACKAGE}/logs'),
        Feature(f'{tests_directory_path}/{NIGHTLY_TESTS_PACKAGE}/rum'),
        Feature(f'{tests_directory_path}/{NIGHTLY_TESTS_PACKAGE}/trace'),
        Feature(f'{tests_directory_path}/{NIGHTLY_TESTS_PACKAGE}/webview'),
        Feature(f'{tests_directory_path}/{NIGHTLY_TESTS_PACKAGE}/main'),
        Feature(f'{tests_directory_path}/{NIGHTLY_TESTS_PACKAGE}/crash'),
    ]
    for feature in features:
        covered_apis.update(feature.fetch_test_cases())
    return covered_apis


def compute_test_coverage(
        tests_directory_path: str,
        api_surface_paths: [str],
        threshold: int,
        ignored_types: [str]) -> int:
    all_apis = fetch_all_apis(api_surface_paths, ignored_types)
    covered_apis = fetch_already_covered_apis(tests_directory_path)
    coverage = resolve_coverage(all_apis, covered_apis)
    print(f'Test coverage percentage is: {coverage}')
    uncovered_apis = sorted(all_apis.difference(covered_apis))
    if len(uncovered_apis) > 0:
        print(f'Not yet covered APIs:\n{chr(10).join(uncovered_apis)}')
    if coverage < threshold:
        print(f'The test coverage percentage: [{coverage} is less than the required threshold: {threshold}]')
        return 1

    return 0


def run_main() -> int:
    cli_args = parse_arguments(sys.argv[1:])
    return compute_test_coverage(cli_args.tests_directory_path,
                                 cli_args.api_surface_paths,
                                 cli_args.test_coverage_threshold_to_assert,
                                 cli_args.ignored_entities)


if __name__ == "__main__":
    sys.exit(run_main())
