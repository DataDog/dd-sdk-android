#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.

import re
from math import floor

DIVIDER = "#"
DOT = "."
EXTEND_SEPARATOR = ":"
TESTABLE_API_REGEX = re.compile("^(?<!deprecated)(\\s*)(constructor|fun)(.+)", re.IGNORECASE)
TYPE_REGEX = re.compile("(\\s*)(.*)(class|interface|enum|object)(\\s)(.+)$", re.IGNORECASE)
DEFAULT_IGNORED_TYPE_REGEX = re.compile("(.*)(companion|deprecated)(\\s)(.*)$", re.IGNORECASE)
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


def sanitize_prefix(previous_indent: int,
                    current_indent: int,
                    prefix: str) -> str:
    # we divide the difference to 2 as the indentation step in apiSurface tool is 2
    difference = floor((current_indent - previous_indent) / INDENT_SIZE)
    if difference <= 0:
        split = prefix.split(DIVIDER)
        # in case the levels of indentation are equal we drop one group
        # from prefix therefore we need to add 1 to difference
        drop_number_of_groups = abs(difference) + 1
        remaining_groups = len(split) - 1 - drop_number_of_groups
        new_prefix = DIVIDER.join(split[:remaining_groups])
        if len(new_prefix) > 0:
            new_prefix += DIVIDER
        return new_prefix
    else:
        return prefix


"""
Resolves the type name from the type_matcher_group and handles several corner cases:
- if type definition is " class Builder" it will return "Builder"
- if type definition is "com.datadog.android.Foo" it will return "Foo"
- if type definition is "com.datadog.android.Foo : com.datadog.android.Bar" it will return "Foo"
- if type definition is "com.datadog.android.Foo<Any>" it will return "Foo<Any>"
"""


def resolve_type_name(type_definition) -> str:
    return type_definition.strip().split(EXTEND_SEPARATOR)[0].strip().split(DOT)[-1]


class ApiSurface:
    file_path: str
    extra_ignored_types_reg_ex: re

    def __init__(self, file_path: str, ignored_types: [str] = None):
        self.file_path = file_path
        if ignored_types:
            self.extra_ignored_types_reg_ex = re.compile(f"^{'|'.join(ignored_types)}", re.IGNORECASE)
        else:
            self.extra_ignored_types_reg_ex = None

    def fetch_testable_methods(self) -> set:
        to_return = set()
        with open(self.file_path, 'r') as file:
            rows = file.readlines()
        prefix = ""
        # We will need this prefix which holds also the attributes of the type (e.g. "companion", "deprecated", "open")
        # to be able to ignore some APIs.
        prefix_with_type_attributes = ""

        current_indent = 0
        for row in rows:
            is_type_matcher = TYPE_REGEX.match(row)
            if is_type_matcher:
                indent = len(is_type_matcher.group(1))
                prefix = sanitize_prefix(current_indent, indent, prefix)
                sanitize_prefix(current_indent, indent, prefix_with_type_attributes)
                current_indent = indent
                type_attributes = is_type_matcher.group(2)
                type_name = resolve_type_name(is_type_matcher.group(5))
                prefix += type_name + DIVIDER
                prefix_with_type_attributes = type_attributes + type_name + DIVIDER
            elif TESTABLE_API_REGEX.match(row) \
                    and (not DEFAULT_IGNORED_TYPE_REGEX.match(prefix_with_type_attributes)) \
                    and (self.extra_ignored_types_reg_ex is None or not self.extra_ignored_types_reg_ex.match(prefix)):
                to_return.add(prefix + row.strip())
        return to_return
