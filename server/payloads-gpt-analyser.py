import os
import json
import logging
import random
from typing import List
import openai

# Configure logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

# Directories for production payloads and testing examples
BUCKETS_DIR = "buckets"
TESTING_DIR = "valid_payloads"

# Use the advanced model "o3" (or change to your desired model identifier)
ADVANCED_MODEL = "gpt-4o"

def load_testing_examples(bucket: str, max_examples: int = 2) -> List[dict]:
    """
    Loads up to `max_examples` JSON payload examples from the testing directory for a given bucket.
    """
    examples = []
    bucket_dir = os.path.join(TESTING_DIR, bucket)
    if not os.path.isdir(bucket_dir):
        logging.warning("No testing folder found for bucket '%s'.", bucket)
        return examples

    for filename in os.listdir(bucket_dir):
        if filename.endswith(".json"):
            filepath = os.path.join(bucket_dir, filename)
            try:
                with open(filepath, "r") as f:
                    data = json.load(f)
                    examples.append(data)
                    if len(examples) >= max_examples:
                        break
            except Exception as e:
                logging.error("Error reading testing file '%s': %s", filepath, e)
    return examples

def construct_prompt_for_payload(payload: dict, testing_examples: List[dict], bucket: str) -> str:
    """
    Constructs a GPT prompt that includes testing examples and the payload to be analyzed.
    """
    prompt = f"Analyze and provide feedback for the following JSON payload from the '{bucket}' bucket.\n\n"

    if testing_examples:
        prompt += "Examples to compare to:\n"
        for example in testing_examples:
            prompt += f"{json.dumps(example, indent=2)}\n\n"
    else:
        prompt += "No testing examples available for this bucket.\n\n"

    prompt += "Payload to Analyze:\n"
    prompt += f"{json.dumps(payload, indent=2)}\n\n"

    prompt += (
        "Having the provided examples to compare to, analyze the JSON payload above and provide feedback.\n"
        "Identify errors or potential issues in the attributes, structure, or values of the payload.\n"
    )
    return prompt

def analyze_payload(client, payload: dict, testing_examples: List[dict], bucket: str, model: str = ADVANCED_MODEL) -> str:
    """
    Sends the constructed prompt for a single JSON payload to the GPT API using the advanced model,
    and returns the analysis.
    """
    prompt = construct_prompt_for_payload(payload, testing_examples, bucket)
    messages = [
        {"role": "system", "content": "You are an expert in analyzing JSON payloads."},
        {"role": "user", "content": prompt}
    ]

    try:
        response = client.chat.completions.create(
            model=model,
            messages=messages,
            temperature=0.7,
        )
        answer = response.choices[0].message.content.strip()
        return answer
    except Exception as e:
        logging.error("Error during GPT API call: %s", e)
        return "Error during analysis."

def analyze_bucket_payloads(bucket: str, client, model: str = ADVANCED_MODEL) -> None:
    """
    Processes up to 2 randomly selected JSON files in a given bucket,
    sending them individually for GPT analysis.
    """
    bucket_dir = os.path.join(BUCKETS_DIR, bucket)
    if not os.path.isdir(bucket_dir):
        logging.error("Bucket directory '%s' does not exist.", bucket_dir)
        return

    testing_examples = load_testing_examples(bucket)
    logging.info("Loaded %d testing examples for bucket '%s'.", len(testing_examples), bucket)

    # List all JSON files in the bucket directory
    json_files = [filename for filename in os.listdir(bucket_dir) if filename.endswith(".json")]
    # Randomly select 2 files (or fewer if there are less than 2)
    selected_files = random.sample(json_files, min(2, len(json_files)))

    for filename in selected_files:
        filepath = os.path.join(bucket_dir, filename)
        try:
            with open(filepath, "r") as f:
                payload = json.load(f)
            logging.info("Analyzing file: %s", filepath)
            analysis = analyze_payload(client, payload, testing_examples, bucket, model)
            print("=" * 80)
            print(f"Analysis for file: {filepath}\n{analysis}\n")
        except Exception as e:
            logging.error("Error processing file '%s': %s", filepath, e)

def main():
    # Set the OpenAI API key from the environment variable
    api_key = os.environ.get("PYTHON_SERVER_OAI_KEY")
    if not api_key:
        logging.error("OPENAI_API_KEY environment variable is not set.")
        return

    # Instantiate a new OpenAI client using the new interface
    client = openai.Client(api_key=api_key)
    # models = client.models.list()
    # print models
    # print(models)

    # Iterate through each bucket folder in the buckets directory and analyze each JSON file
    if not os.path.isdir(BUCKETS_DIR):
        logging.error("Buckets directory '%s' does not exist.", BUCKETS_DIR)
        return

    for bucket in os.listdir(BUCKETS_DIR):
        bucket_path = os.path.join(BUCKETS_DIR, bucket)
        if os.path.isdir(bucket_path):
            logging.info("Starting analysis for bucket: %s", bucket)
            analyze_bucket_payloads(bucket, client, ADVANCED_MODEL)
            logging.info("Finished analysis for bucket: %s", bucket)

if __name__ == "__main__":
    main()
