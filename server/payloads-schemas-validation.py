import os
import json
import logging
import random
import jsonschema
from typing import Dict, List, Union

# Configure logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

# Directory containing payload buckets
BUCKETS_DIR = "buckets"


def load_schema(schema_path: str) -> Dict[str, object]:
    """Load a JSON schema from the given file path."""
    with open(schema_path, 'r') as f:
        return json.load(f)


def sanitize_ref(ref_value: Union[str, object]) -> Union[str, object]:
    """
    Sanitize a $ref value by removing any directory path.

    For a string like 'path/to/schema.json#/fragment', returns 'schema.json#/fragment'.
    If the ref_value starts with '#' or is not a string, it is returned unchanged.
    """
    if isinstance(ref_value, str) and not ref_value.startswith("#"):
        return os.path.basename(ref_value)
    return ref_value


def sanitize_refs(obj: Union[Dict[str, object], List[object]]) -> None:
    """
    Recursively sanitize all $ref attributes within the given object.
    """
    if isinstance(obj, dict):
        for key, value in obj.items():
            if key == "$ref" and isinstance(value, str):
                obj[key] = sanitize_ref(value)
            else:
                sanitize_refs(value)
    elif isinstance(obj, list):
        for item in obj:
            sanitize_refs(item)


def build_schema_store(directory: str) -> Dict[str, Dict[str, object]]:
    """
    Recursively searches for JSON schema files in the given directory, loads each schema,
    sanitizes its $ref values, and builds a store mapping the schema's basename (from $id) to the schema.
    """
    store = {}
    for root, _, files in os.walk(directory):
        for filename in files:
            if filename.endswith(".json"):
                file_path = os.path.join(root, filename)
                try:
                    schema = load_schema(file_path)
                except Exception as e:
                    logging.error("Error loading schema from %s: %s", file_path, e)
                    continue

                schema_id = schema.get("$id")
                if not schema_id:
                    logging.warning("Schema in %s does not have an '$id' field.", file_path)
                    continue

                # Use os.path.basename to extract the file name portion of the $id
                schema_id = os.path.basename(schema_id)
                sanitize_refs(schema)
                schema["$id"] = schema_id
                store[schema_id] = schema
    return store


def validate_payload(payload: Dict[str, object], main_schema: Dict[str, object],
                     schema_store: Dict[str, Dict[str, object]]) -> str:
    """
    Validates the payload against the provided main schema using the associated schema store.

    Returns:
        "Valid" if the payload is valid, or an error message indicating the issue.
    """
    resolver = jsonschema.RefResolver.from_schema(main_schema, store=schema_store)
    try:
        jsonschema.validate(instance=payload, schema=main_schema, resolver=resolver)
        return "Valid"
    except jsonschema.ValidationError as e:
        return f"Invalid: {e.message}"


def validate_bucket_payloads(bucket: str) -> None:
    """
    Processes up to 2 randomly selected JSON files in a given bucket directory,
    validating each payload against its corresponding JSON schema.
    """
    bucket_dir = os.path.join(BUCKETS_DIR, bucket)
    if not os.path.isdir(bucket_dir):
        logging.error("Bucket directory '%s' does not exist.", bucket_dir)
        return

    json_files = [filename for filename in os.listdir(bucket_dir) if filename.endswith(".json")]
    if not json_files:
        logging.warning("No JSON files found in bucket '%s'.", bucket)
        return

    # Randomly select up to 2 files for validation
    selected_files = random.sample(json_files, min(2, len(json_files)))

    bucket_schema = BUCKET_SCHEMAS.get(bucket)
    if not bucket_schema:
        logging.error("No schema configuration found for bucket '%s'.", bucket)
        return

    main_schema = bucket_schema.get("main")
    schema_store = bucket_schema.get("schema_store")
    if not main_schema:
        logging.error("No main schema found for bucket '%s'.", bucket)
        return

    for filename in selected_files:
        filepath = os.path.join(bucket_dir, filename)
        try:
            with open(filepath, "r") as f:
                payload = json.load(f)
            result = validate_payload(payload, main_schema, schema_store)
            if result == "Valid":
                logging.info("Valid payload in %s", filepath)
            else:
                logging.error("Invalid payload in %s: %s", filepath, result)
        except Exception as e:
            logging.error("Error processing file '%s': %s", filepath, e)


def main():
    if not os.path.isdir(BUCKETS_DIR):
        logging.error("Buckets directory '%s' does not exist.", BUCKETS_DIR)
        return

    for bucket in os.listdir(BUCKETS_DIR):
        bucket_path = os.path.join(BUCKETS_DIR, bucket)
        if os.path.isdir(bucket_path):
            logging.info("Starting validation for bucket: %s", bucket)
            validate_bucket_payloads(bucket)
            logging.info("Finished validation for bucket: %s", bucket)


if __name__ == "__main__":
    # Pre-load and sanitize the replay schema
    replay_schema_path = "features/dd-sdk-android-session-replay/src/main/json/schemas/session-replay-mobile-schema.json"
    replay_main_schema = load_schema(replay_schema_path)
    sanitize_refs(replay_main_schema)

    # Define schema configurations for each bucket
    BUCKET_SCHEMAS = {
        "rum": {
            "main": load_schema("features/dd-sdk-android-rum/src/main/json/rum/rum-collection-schema.json"),
            "schema_store": build_schema_store("features/dd-sdk-android-rum/src/main/json/rum")
        },
        "logs": {
            "main": load_schema("features/dd-sdk-android-logs/src/main/json/log/logs-collection-schema.json"),
            "schema_store": build_schema_store("features/dd-sdk-android-logs/src/main/json/log")
        },
        "spans": {
            "main": load_schema("features/dd-sdk-android-trace/src/main/json/trace/spans-collection-schema.json"),
            "schema_store": build_schema_store("features/dd-sdk-android-trace/src/main/json/trace")
        },
        "replay": {
            "main": replay_main_schema,
            "schema_store": build_schema_store("features/dd-sdk-android-session-replay/src/main/json")
        },
    }

    main()
