#!/bin/zsh

#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

source ./ci/scripts/vault_config.sh
source ./ci/scripts/list-secrets.sh

# Usage:
#   get_secret <secret_name>
#
# Notes:
# - For <secret_name> use constants defined in './ci/scripts/vault_config.sh'
# - Requires `vault` to be installed
get_secret() {
    local secret_name=$1

    export VAULT_ADDR=$DD_VAULT_ADDR

    if [ "$CI" = "true" ]; then
        echo "Login as CI" >&2
        vault login -method=aws -no-print
    else
        if vault token lookup &>/dev/null; then
            echo "Reading '$secret_name' secret in local env. You are already authenticated with 'vault'." >&2
        else
            echo "Reading '$secret_name' secret in local env. You will now be authenticated with OIDC in your web browser." >&2
            vault login -method=oidc -no-print
        fi
    fi

    local secret_value=$(vault kv get -field=value "$DD_ANDROID_SECRETS_PATH_PREFIX/$secret_name")

    if [[ -z "$secret_value" ]]; then
        echo "Error" "Failed to retrieve the '$secret_name' secret or the secret is empty." >&2
        exit 1
    fi

    echo "Successfully retrieved $secret_name." >&2
    echo "$secret_value"
}

# Only run the main logic if the script is executed directly (not sourced)
if [ "$CI" != "true" ]; then
    list_secrets
    select_secret
    get_secret "$SECRET_NAME"
fi
