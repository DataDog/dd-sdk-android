#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.

import re

DOT = "."
NESTED_TYPE_SEPARATOR = "$"
API_SEPARATOR = "#"
EXTEND_SEPARATOR = ":"
TESTABLE_API_REGEX = re.compile("^(\\s*)(?<!deprecated)(\\s*)(constructor|fun)(.+)")
TYPE_REGEX = re.compile("(\\s*)(.*)(class|interface|enum|object)(\\s)(.*)$")
DEFAULT_IGNORED_TYPE_ATTRS = ["companion", "data"]
DEFAULT_IGNORED_TYPES = ["enum", "interface", "annotation"]
INDENT_SIZE = 2

"""
In this function we will sanitize the current API prefix as following.
Say we have the following apiSurface file:

class Foo
  fun a
  class Bar
    fun b
class AnotherFoo
  fun c

When we reach line `class AnotherFoo` the prefix will be `Foo#Bar` but the API key 
for `fun c` should be: `AnotherFoo#fun c` therefore based on the indentation difference (2) 
in this case we will substract 2 entities from the prefix (Foo and Bar) which will result in an empty prefix at
this point. Going further we will attach the current entity `AnotherFoo#`

"""

"""
Resolves the type name from the type_matcher_group and handles several corner cases:
- if type definition is " class Builder" it will return "Builder"
- if type definition is "com.datadog.android.Foo" it will return "Foo"
- if type definition is "com.datadog.android.Foo : com.datadog.android.Bar" it will return "Foo"
- if type definition is "com.datadog.android.Foo<Any>" it will return "Foo<Any>"
"""


def resolve_type_name(type_definition) -> str:
    return type_definition.strip().split(EXTEND_SEPARATOR)[0].strip()


class ApiSurface:
    file_path: str
    ignored_types: [str]
    verbose: bool

    def __init__(self, file_path: str, ignored_types: [str] = None, verbose: bool = False):
        self.file_path = file_path
        self.ignored_types = ignored_types
        self.verbose = verbose

    def fetch_testable_methods(self) -> set:
        to_return = set()
        with open(self.file_path, 'r') as file:
            rows = file.readlines()
        stack = ["", "", "", "", "", ""] # we don't expect nested types with more than 5 levels
        # We will need this prefix which holds also the attributes of the type (e.g. "companion", "deprecated", "open")
        # to be able to ignore some APIs.
        type_attributes = ""
        type = ""

        for row in rows:
            is_type_matcher = TYPE_REGEX.match(row)
            is_testable_api = TESTABLE_API_REGEX.match(row)
            if is_type_matcher:
                depth = int(len(is_type_matcher.group(1)) / INDENT_SIZE)
                type_attributes = is_type_matcher.group(2).strip()
                type = is_type_matcher.group(3)
                type_name = resolve_type_name(is_type_matcher.group(5))
                stack[depth] = type_name
            elif is_testable_api:
                depth = int(len(is_testable_api.group(1)) / INDENT_SIZE)
                prefix = NESTED_TYPE_SEPARATOR.join(stack[:depth])
                if (type_attributes in DEFAULT_IGNORED_TYPE_ATTRS) or (type in DEFAULT_IGNORED_TYPES):
                    if self.verbose:
                        print(f"Ignored by default {type_attributes} {type} {prefix}{API_SEPARATOR}{row.strip()}")
                    pass
                elif self.ignored_types is not None and prefix in self.ignored_types:
                    if self.verbose:
                        print(f"Ignored manually {type_attributes} {type} {prefix}{API_SEPARATOR}{row.strip()}")
                else:
                    if self.verbose:
                        print(f"Keeping {type_attributes} {type} {prefix}{API_SEPARATOR}{row.strip()}")
                    separator = API_SEPARATOR if prefix else ""
                    to_return.add(prefix + separator + row.strip())
        return to_return
