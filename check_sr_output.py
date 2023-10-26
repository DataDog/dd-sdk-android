#  Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
#  This product includes software developed at Datadog (https://www.datadoghq.com/).
#  Copyright 2016-Present Datadog, Inc.



import sys
import json
import os
def cleanup():
    if os.path.exists("records"):
        for file in os.listdir("records"):
            os.remove(os.path.join("records", file))

def dumpSegmentIntoFile(segment, counter):
    records_directory = 'records'
    records_file_name = os.path.join(records_directory, f'sent_records{counter}.json')
    if not (os.path.exists(records_directory)):
        os.mkdir(records_directory)
    # print(f"Dumping segment into file: {records_file_name}")
    with open(records_file_name, 'w') as outfile:
        outfile.write(segment)

def read_json_file(file_name):
    try:
        # define a json array
        json_objects = []
        batch_number = 0
        counter = 0
        with open(file_name, 'r') as file:
            # Read the lines of the file and parse each line as a JSON object
            for line in file:
                dumpSegmentIntoFile(line, counter)
                counter+=1
                json_array = json.loads(line)['records']
                # traverse the json array
                for json_object in json_array:
                    json_object['batch_number'] = batch_number
                    json_objects.append(json_object)
                batch_number += 1
            return json_objects
    except FileNotFoundError:
        print(f"Error: File '{file_name}' not found.")
    except json.JSONDecodeError as e:
        print(f"Error decoding JSON: {e}")
    except Exception as e:
        print(f"An error occurred: {e}")
    return None


# Check if the correct number of command line arguments is provided
if len(sys.argv) != 2:
    print("Usage: python script_name.py <file_name>")
    sys.exit(1)

# Get the file name from the command line arguments
file_name = sys.argv[1]

# Cleanup
cleanup()

# Call the function to read and print the contents of the file
sent_records = read_json_file(file_name)
# print sent records divided by new line
prevTimestamp = 0
prevRecord = None

for record in sent_records:
    if(record['timestamp'] < prevTimestamp):
        print(f"Following record is out of order: {record} \n with previous record: {prevRecord} \n")
    # prevTimestamp is max of prevTimestamp and current timestamp
    if(record['timestamp'] >= prevTimestamp):
        prevTimestamp = record['timestamp']
        prevRecord = record
