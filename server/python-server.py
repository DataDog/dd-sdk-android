import os
import json
import uuid
import zlib
import logging
from datetime import datetime
from flask import Flask, request, jsonify

# Configure logging for better observability
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')

app = Flask(__name__)

# Base directory where all bucket folders will be stored
BASE_BUCKET_DIR = "buckets"

def write_payload(bucket_name: str, payload: any) -> None:
    """
    Writes the given payload to a JSON file within a bucket folder.
    Each file is named with a timestamp and a unique uuid.
    """
    bucket_path = os.path.join(BASE_BUCKET_DIR, bucket_name)
    os.makedirs(bucket_path, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")
    unique_id = uuid.uuid4().hex
    filename = f"{timestamp}_{unique_id}.json"
    file_path = os.path.join(bucket_path, filename)

    with open(file_path, "w") as f:
        json.dump(payload, f, indent=4)
    logging.info("Payload written to %s", file_path)

def parse_json_lines(raw_data: str) -> list:
    """
    Parses a multi-line string where each non-empty line is a JSON object.
    Returns a list of parsed JSON objects.

    Raises:
        ValueError: If any line fails to decode as JSON.
    """
    json_lines = [line for line in raw_data.splitlines() if line.strip()]
    parsed_data = []
    for line in json_lines:
        try:
            parsed_data.append(json.loads(line))
        except json.JSONDecodeError as e:
            error_msg = f"Invalid JSON in line: {line} - {e}"
            logging.error(error_msg)
            raise ValueError(error_msg)
    return parsed_data

def decompress_bytes(input_bytes: bytes) -> bytes:
    """
    Decompress the given bytes using zlib.
    """
    return zlib.decompress(input_bytes)

def extract_sr_segment_as_json(req: request) -> any:
    """
    Extracts the 'segment' file from a multipart/form-data request,
    decompresses its bytes, and parses it as JSON.
    Returns the parsed JSON or None if extraction fails.
    """
    segment_file = req.files.get('segment')
    if segment_file:
        compressed_data = segment_file.read()
        if compressed_data:
            try:
                decompressed_data = decompress_bytes(compressed_data)
                return json.loads(decompressed_data.decode('utf-8'))
            except (zlib.error, json.JSONDecodeError) as e:
                logging.error("Error processing segment: %s", e)
    return None

@app.route('/api/v2/rum', methods=['POST'])
def handle_rum():
    """
    Endpoint to handle RUM data.
    Expects multi-line JSON data in the request body.
    """
    try:
        raw_data = request.data.decode('utf-8')
        data = parse_json_lines(raw_data)
    except ValueError as ve:
        return jsonify({"status": "error", "message": str(ve)}), 400

    logging.info("Received data on /api/v2/rum: %s", data)
    write_payload("rum", data)
    return jsonify({"status": "success", "endpoint": "rum", "data": data}), 202

@app.route('/api/v2/logs', methods=['POST'])
def handle_logs():
    """
    Endpoint to handle logs data.
    Expects a valid JSON payload in the request body.
    """
    data = request.get_json()
    if data is None:
        error_msg = "Invalid JSON payload."
        logging.error(error_msg)
        return jsonify({"status": "error", "message": error_msg}), 400

    logging.info("Received data on /api/v2/logs: %s", data)
    write_payload("logs", data)
    return jsonify({"status": "success", "endpoint": "logs", "data": data}), 202

@app.route('/api/v2/spans', methods=['POST'])
def handle_spans():
    """
    Endpoint to handle spans data.
    Expects multi-line JSON data in the request body.
    """
    try:
        raw_data = request.data.decode('utf-8')
        data = parse_json_lines(raw_data)
    except ValueError as ve:
        return jsonify({"status": "error", "message": str(ve)}), 400

    logging.info("Received data on /api/v2/spans: %s", data)
    write_payload("spans", data)
    return jsonify({"status": "success", "endpoint": "spans", "data": data}), 202

@app.route('/api/v2/replay', methods=['POST'])
def handle_replay():
    """
    Endpoint to handle replay data.
    Expects a multipart/form-data request with a 'segment' file.
    The file is decompressed and parsed as JSON.
    """
    data = extract_sr_segment_as_json(request)
    if data is None:
        error_msg = "Invalid or missing segment data."
        logging.error(error_msg)
        return jsonify({"status": "error", "message": error_msg}), 400

    logging.info("Received data on /api/v2/replay: %s", data)
    write_payload("replay", data)
    return jsonify({"status": "success", "endpoint": "replay", "data": data}), 202

if __name__ == '__main__':
    # Run the server on all network interfaces on port 5000
    app.run(host='0.0.0.0', port=5000, debug=True)
