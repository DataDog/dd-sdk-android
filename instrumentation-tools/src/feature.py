#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.

import os
import re

TEST_CASE_ID_REG_EX = re.compile("(.+)apiMethodSignature: (.+)$")


class Feature:
    directory_path: str

    def __init__(self, directory_path: str):
        self.directory_path = directory_path

    def fetch_test_cases(self) -> set:
        print(f'Walking through {self.directory_path}')
        test_cases = set()
        for root, subdirs, files in os.walk(self.directory_path):
            for file in files:
                print(f'fetching the test cases in {file}')
                self.fetch_test_cases_from_file(f'{root}/{file}', test_cases)
        return test_cases

    def fetch_test_cases_from_file(self, file_path: str, test_cases: set):
        with open(file_path, 'r') as file:
            for line in file.readlines():
                match = TEST_CASE_ID_REG_EX.match(line)
                if match:
                    test_cases.add(match.group(2))
