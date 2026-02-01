const zlib = require('zlib');
const { promisify } = require('util');

const gunzip = promisify(zlib.gunzip);

// Note: zstd decompression requires native module or pure JS implementation
// For simplicity, we'll try to use a pure JS approach when available

let ZstdCodec = null;

/**
 * Initialize zstd codec (lazy loading)
 */
async function initZstd() {
  if (ZstdCodec) return ZstdCodec;
  
  try {
    const zstdModule = require('zstd-codec');
    await new Promise((resolve) => {
      zstdModule.ZstdCodec.run((codec) => {
        ZstdCodec = new codec.Streaming();
        resolve();
      });
    });
    return ZstdCodec;
  } catch (e) {
    console.warn('⚠️ zstd-codec not available, zstd decompression disabled:', e.message);
    return null;
  }
}

/**
 * Decompress data based on encoding
 * @param {Buffer} data - The compressed data
 * @param {string} encoding - 'gzip' or 'zstd'
 * @returns {Promise<string>} - The decompressed string
 */
async function decompress(data, encoding) {
  if (!data || data.length === 0) {
    return '';
  }
  
  if (encoding === 'gzip') {
    const result = await gunzip(data);
    return result.toString('utf-8');
  }
  
  if (encoding === 'zstd') {
    const codec = await initZstd();
    
    if (!codec) {
      throw new Error('zstd decompression not available - install zstd-codec');
    }
    
    try {
      const decompressed = codec.decompress(data);
      return Buffer.from(decompressed).toString('utf-8');
    } catch (e) {
      throw new Error(`zstd decompression failed: ${e.message}`);
    }
  }
  
  throw new Error(`Unknown encoding: ${encoding}`);
}

/**
 * Try to decompress and parse JSON
 * @param {Buffer} data - The compressed data
 * @param {string} encoding - 'gzip' or 'zstd'
 * @returns {Promise<object>} - Parsed JSON or raw string
 */
async function decompressAndParse(data, encoding) {
  const text = await decompress(data, encoding);
  
  try {
    // Try to parse as JSON (might be NDJSON - newline delimited)
    if (text.includes('\n')) {
      // Parse each line as JSON
      const lines = text.split('\n').filter(line => line.trim());
      return lines.map(line => {
        try {
          return JSON.parse(line);
        } catch {
          return line;
        }
      });
    }
    return JSON.parse(text);
  } catch {
    return text;
  }
}

module.exports = {
  decompress,
  decompressAndParse,
  initZstd
};
