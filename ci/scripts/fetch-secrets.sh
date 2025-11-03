#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

source ./ci/scripts/vault_config.sh
source ./ci/scripts/get-secret.sh

# Gradle properties
get_secret $DD_ANDROID_SECRET__GRADLE_PROPERTIES > ./ci/pipelines/secrets/gradle.properties
# Signing and publishing
get_secret $DD_ANDROID_SECRET__SIGNING_GPG_PRIVATE_KEY > ./ci/pipelines/secrets/gpg_private_key
get_secret $DD_ANDROID_SECRET__SIGNING_GPG_PASSPHRASE > ./ci/pipelines/secrets/gpg_passphrase
get_secret $DD_ANDROID_SECRET__SIGNING_GPG_PUBLIC_KEY > ./ci/pipelines/secrets/gpg_public_key
get_secret $DD_ANDROID_SECRET__PUBLISHING_CENTRAL_USERNAME > ./ci/pipelines/secrets/central_username
get_secret $DD_ANDROID_SECRET__PUBLISHING_CENTRAL_PWD > ./ci/pipelines/secrets/central_password
    # API and App keys
get_secret $DD_ANDROID_SECRET__API_KEY > ./ci/pipelines/secrets/api_key
get_secret $DD_ANDROID_SECRET__APP_KEY > ./ci/pipelines/secrets/app_key
get_secret $DD_ANDROID_SECRET__CODECOV_TOKEN > ./ci/pipelines/secrets/codecov_token
# Keystore
get_secret $DD_ANDROID_SECRET__KEYSTORE > ./ci/pipelines/secrets/keystore
get_secret $DD_ANDROID_SECRET__KEYSTORE_PWD > ./ci/pipelines/secrets/keystore_password
# E2E Testing
get_secret $DD_ANDROID_SECRET__E2E_CONFIG_JSON > ./ci/pipelines/secrets/e2e_config.json
get_secret $DD_ANDROID_SECRET__E2E_API_KEY > ./ci/pipelines/secrets/e2e_api_key
get_secret $DD_ANDROID_SECRET__E2E_APP_KEY > ./ci/pipelines/secrets/e2e_app_key
get_secret $DD_ANDROID_SECRET__E2E_MOBILE_APP_ID > ./ci/pipelines/secrets/e2e_mobile_app_id
    # WebView
get_secret $DD_ANDROID_SECRET__WEBVIEW_CONFIG_JSON > ./ci/pipelines/secrets/webview_config.json
get_secret $DD_ANDROID_SECRET__WEBVIEW_API_KEY > ./ci/pipelines/secrets/webview_api_key
get_secret $DD_ANDROID_SECRET__WEBVIEW_APP_KEY > ./ci/pipelines/secrets/webview_app_key
get_secret $DD_ANDROID_SECRET__WEBVIEW_MOBILE_APP_ID > ./ci/pipelines/secrets/webview_mobile_app_id
    # Staging
get_secret $DD_ANDROID_SECRET__E2E_STAGING_CONFIG_JSON > ./ci/pipelines/secrets/e2e_staging_config.json
get_secret $DD_ANDROID_SECRET__E2E_STAGING_API_KEY > ./ci/pipelines/secrets/e2e_staging_api_key
get_secret $DD_ANDROID_SECRET__E2E_STAGING_APP_KEY > ./ci/pipelines/secrets/e2e_staging_app_key
get_secret $DD_ANDROID_SECRET__E2E_STAGING_APP_ID > ./ci/pipelines/secrets/e2e_staging_app_id
    # Benchmark
get_secret $DD_ANDROID_SECRET__BENCHMARK_CONFIG_JSON > ./ci/pipelines/secrets/benchmark_config.json
get_secret $DD_ANDROID_SECRET__BENCHMARK_API_KEY > ./ci/pipelines/secrets/benchmark_api_key
get_secret $DD_ANDROID_SECRET__BENCHMARK_APP_KEY > ./ci/pipelines/secrets/benchmark_app_key
get_secret $DD_ANDROID_SECRET__BENCHMARK_MOBILE_APP_ID > ./ci/pipelines/secrets/benchmark_mobile_app_id