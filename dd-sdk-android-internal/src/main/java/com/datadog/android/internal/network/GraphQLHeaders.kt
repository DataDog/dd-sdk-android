/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.network

/**
 * Headers used internally for intercepting GraphQL requests.
 * @param headerValue name of the header.
 */
enum class GraphQLHeaders(val headerValue: String) {
    DD_GRAPHQL_NAME_HEADER("_dd-custom-header-graph-ql-operation-name"),
    DD_GRAPHQL_VARIABLES_HEADER("_dd-custom-header-graph-ql-variables"),
    DD_GRAPHQL_TYPE_HEADER("_dd-custom-header-graph-ql-operation_type"),
    DD_GRAPHQL_PAYLOAD_HEADER("_dd-custom-header-graph-ql-payload")
}
