/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("PackageNaming")

package java.nio.file

/**
 * Class-loading placeholder for AssertJ on API 24–25, before Android provides this type in API 26.
 * Does not implement filesystem operations.
 */
class Path
