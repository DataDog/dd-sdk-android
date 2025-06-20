#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2019-Present Datadog, Inc

import sys
import os
import glob

import xml.etree.ElementTree as ET
import xml.dom.minidom as MD

from typing import List


DEFAULT_NS = "https://schema.gradle.org/dependency-verification"
NS_PREFIX = "{%s}" % DEFAULT_NS
NSMAP = {None : DEFAULT_NS}

ROOT_TAG = (NS_PREFIX + "verification-metadata")
CONFIGURATION_TAG = (NS_PREFIX + "configuration")
METADATA_TAG = (NS_PREFIX + "verify-metadata")
SIGNATURES_TAG = (NS_PREFIX + "verify-signatures")

COMPONENTS_TAG = (NS_PREFIX + "components")
COMPONENT_TAG = (NS_PREFIX + "component")

def run_main() -> int:
    files = glob.glob('**/verification-metadata.xml', recursive=True)

    # prepare final xml
    root = ET.Element(ROOT_TAG)
    configuration = ET.Element(CONFIGURATION_TAG)
    metadata = ET.Element(METADATA_TAG)
    metadata.text = "true"
    configuration.insert(0, metadata)
    signatures = ET.Element(SIGNATURES_TAG)
    signatures.text = "true"
    configuration.insert(1, signatures)
    root.insert(0, configuration)

    components = ET.Element(COMPONENTS_TAG)
    root.insert(1, components)

    index = 0
    for filename in files:
        data = ET.parse(filename).getroot()
        for child in data:
            if child.tag == COMPONENTS_TAG:
                for component in child:
                    components.insert(index, component)
                    index = index + 1

    # remove unnecessary whitespaces in content
    for elem in root.iter():
        if elem.text is not None:
            elem.text = elem.text.strip()
        if elem.tail is not None:
            elem.tail = elem.tail.strip()

    raw_xml = ET.tostring(root, method="xml").decode('utf-8')
    # A bug in the python etree xml api prevents writing standalone xml, so we need
    # to remove the default namespace:
    sanitized_xml = raw_xml.replace("<ns0:", "<").replace("</ns0:", "</").replace("xmlns:ns0=", "xmlns=")
    formatted_xml = MD.parseString(sanitized_xml).toprettyxml(indent="   ", encoding="utf-8")

    with open("verification-metadata.xml", "w") as output_file:
        output_file.write(formatted_xml.decode('utf-8'))

    return 0


if __name__ == "__main__":
    sys.exit(run_main())
