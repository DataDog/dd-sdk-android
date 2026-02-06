#!/bin/zsh

#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

# Usage:
# $ ./ci/scripts/vault_config.sh
#
# Note:
# - Requires `vault` to be installed

source ./ci/scripts/vault_config.sh
source ./ci/scripts/list-secrets.sh

select_input_method() {
    echo
    echo "How would you like to provide the secret value?"
    echo "1) Enter manually"
    echo "2) Read from text file"
    while true; do
        echo "Enter your choice:"
        read "input_method"
        case $input_method in
            1)
                get_secret_value_from_input
                break
                ;;
            2)
                get_secret_value_from_file
                break
                ;;
            *)
                echo "Invalid choice."
                ;;
        esac
    done
}

get_secret_value_from_file() {
    echo "Enter the file path to read the value for '$SECRET_NAME':"
    read "SECRET_FILE"
    echo

    SECRET_FILE=${SECRET_FILE/#\~/$HOME} # Expand ~ to home directory if present
    echo "Using '$SECRET_FILE'"

    if [[ -f "$SECRET_FILE" ]]; then
        SECRET_VALUE=$(cat "$SECRET_FILE")
    else
        echo "Error: File '$SECRET_FILE' does not exist."
        exit 1
    fi
}

get_secret_value_from_input() {
    echo "Enter the new value for '$SECRET_NAME':"
    read "SECRET_VALUE"
    echo
}

set_secret_value() {
    echo "You will now be authenticated with OIDC in your web browser. Press ENTER to continue."
    read
    export VAULT_ADDR=$DD_VAULT_ADDR
    vault login -method=oidc -no-print
    vault kv put "$DD_ANDROID_SECRETS_PATH_PREFIX/$SECRET_NAME" value="$SECRET_VALUE"
    echo "Secret '$SECRET_NAME' set successfully."
}

list_secrets
select_secret
select_input_method
set_secret_value
