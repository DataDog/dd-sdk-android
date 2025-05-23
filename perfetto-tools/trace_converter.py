import os
import subprocess
import tempfile
import logging
from pathlib import Path

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def convert_to_pprof(input_file_path, traceconv_path='./traceconv', output_dir='profiling'):
    """
    Convert a trace file to pprof format using traceconv and save it in the profiling folder.
    
    Args:
        input_file_path (str): Path to the input trace file
        traceconv_path (str): Path to the traceconv executable
        output_dir (str): Directory to save the converted file
        
    Returns:
        str: Path to the converted pprof file
        
    Raises:
        Exception: If conversion fails
    """
    try:
        # Create output directory if it doesn't exist
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)
        
        # Generate output filename based on input filename
        input_path = Path(input_file_path)
        output_filename = f"{input_path.stem}_converted"
        output_path = output_dir / output_filename

        # Run traceconv to convert the file
        cmd = [traceconv_path, 'profile', input_file_path, str(output_path), '--perf']
        result = subprocess.run(cmd, capture_output=True, text=True)
        
        if result.returncode != 0:
            logger.error(f"Trace conversion failed: {result.stderr}")
            raise Exception(f"Trace conversion failed: {result.stderr}")
        
        logger.info(f"Successfully converted trace to pprof format: {output_path}")
        return str(output_path)
    except Exception as e:
        logger.error(f"Error during trace conversion: {str(e)}")
        raise

if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='Convert a trace file to pprof format')
    parser.add_argument('input_file', help='Path to the input trace file')
    parser.add_argument('--traceconv', default='./traceconv', help='Path to the traceconv executable')
    parser.add_argument('--output-dir', default='profiling', help='Directory to save the converted file')
    
    args = parser.parse_args()
    
    try:
        output_path = convert_to_pprof(args.input_file, args.traceconv, args.output_dir)
        logger.info(f"Converted file saved to: {output_path}")
    except Exception as e:
        logger.error(f"Conversion failed: {str(e)}")
        exit(1) 