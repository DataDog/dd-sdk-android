const express = require('express');
const Busboy = require('busboy');
const storage = require('../lib/storage');
const forwarder = require('../lib/forwarder');
const decompressor = require('../lib/decompressor');

const router = express.Router();

/**
 * Custom middleware to capture raw body without auto-decompression.
 * Express body-parser tries to decompress based on content-encoding,
 * but it doesn't support zstd. We need the raw bytes.
 */
function captureRawBody(req, res, next) {
  const chunks = [];
  
  req.on('data', chunk => chunks.push(chunk));
  req.on('end', () => {
    req.rawBody = Buffer.concat(chunks);
    next();
  });
  req.on('error', err => next(err));
}

/**
 * Determine feature type from URL path
 */
function getFeatureType(path) {
  if (path.includes('/rum')) return 'rum';
  if (path.includes('/logs')) return 'logs';
  if (path.includes('/spans')) return 'traces';
  if (path.includes('/replay')) return 'session-replay';
  if (path.includes('/profile')) return 'profiling';
  if (path.includes('/exposures')) return 'feature-flags-exposures';
  if (path.includes('/flags')) return 'feature-flags-assignments';
  return 'unknown';
}

/**
 * Extract relevant headers for storage
 */
function extractHeaders(headers) {
  const relevant = {};
  const keysToCapture = [
    'content-type',
    'content-encoding',
    'dd-api-key',
    'dd-evp-origin',
    'dd-evp-origin-version',
    'dd-request-id',
    'user-agent'
  ];
  
  for (const key of keysToCapture) {
    if (headers[key]) {
      relevant[key] = headers[key];
    }
  }
  return relevant;
}

/**
 * Handle POST requests for RUM, Logs, Traces, Exposures (non-multipart)
 */
router.post(['/:feature', '/*'], captureRawBody, async (req, res) => {
  const feature = getFeatureType(req.originalUrl);
  const contentType = req.headers['content-type'] || '';
  
  // If multipart, let the multipart handler deal with it
  if (contentType.includes('multipart/form-data')) {
    return handleMultipart(req, res, feature);
  }
  
  try {
    const rawBody = req.rawBody;
    const headers = extractHeaders(req.headers);
    const encoding = req.headers['content-encoding'];
    
    // Try to decompress for viewing
    let decompressedBody = null;
    let decompressError = null;
    let eventCount = 0;
    
    if (encoding === 'zstd' || encoding === 'gzip') {
      try {
        decompressedBody = await decompressor.decompress(rawBody, encoding);
      } catch (e) {
        decompressError = e.message;
      }
    } else {
      // No compression, body is already readable
      decompressedBody = rawBody.toString('utf-8');
    }
    
    // Count events (each line is one event in NDJSON format)
    if (decompressedBody) {
      eventCount = decompressedBody.split('\n').filter(line => line.trim()).length;
    }
    
    // Store the request
    const record = storage.save({
      timestamp: Date.now(),
      feature,
      method: req.method,
      path: req.originalUrl,
      headers: JSON.stringify(headers),
      rawBodySize: rawBody.length,
      rawBody: rawBody.toString('base64'),
      decompressedBody: decompressedBody,
      decompressError: decompressError,
      contentType: contentType,
      encoding: encoding || null,
      eventCount: eventCount
    });
    
    console.log(`📥 [${feature}] Captured request #${record.id} (${rawBody.length} bytes)`);
    
    // Forward to Datadog
    const forwardResult = await forwarder.forward(req, rawBody, feature);
    
    // Update record with forward result
    storage.updateForwardResult(record.id, forwardResult);
    
    // Return Datadog's response to SDK
    res.status(forwardResult.status).send(forwardResult.body);
    
  } catch (error) {
    console.error(`❌ Error processing ${feature} request:`, error);
    res.status(500).json({ error: error.message });
  }
});

/**
 * Handle GET requests (Feature Flags assignments)
 */
router.get('/*', async (req, res) => {
  const feature = 'feature-flags-assignments';
  
  try {
    const headers = extractHeaders(req.headers);
    
    // Store the request
    const record = storage.save({
      timestamp: Date.now(),
      feature,
      method: 'GET',
      path: req.originalUrl,
      headers: JSON.stringify(headers),
      rawBodySize: 0,
      rawBody: null,
      decompressedBody: null,
      decompressError: null,
      contentType: req.headers['content-type'] || null,
      encoding: null
    });
    
    console.log(`📥 [${feature}] Captured GET request #${record.id}`);
    
    // Forward to Datadog
    const forwardResult = await forwarder.forwardGet(req, feature);
    
    // Update record with forward result
    storage.updateForwardResult(record.id, forwardResult);
    
    // Store the response body for viewing
    if (forwardResult.body) {
      storage.updateResponseBody(record.id, forwardResult.body);
    }
    
    // Return Datadog's response to SDK
    res.status(forwardResult.status);
    if (forwardResult.contentType) {
      res.set('Content-Type', forwardResult.contentType);
    }
    res.send(forwardResult.body);
    
  } catch (error) {
    console.error(`❌ Error processing ${feature} request:`, error);
    res.status(500).json({ error: error.message });
  }
});

/**
 * Handle multipart requests (Session Replay, Profiling) using busboy
 */
async function handleMultipart(req, res, feature) {
  try {
    const headers = extractHeaders(req.headers);
    const files = [];
    const fields = {};
    
    // First, collect all the raw body data
    const rawChunks = [];
    await new Promise((resolve, reject) => {
      req.on('data', chunk => rawChunks.push(chunk));
      req.on('end', resolve);
      req.on('error', reject);
    });
    const rawBody = Buffer.concat(rawChunks);
    
    // Create a readable stream from the buffer for busboy
    const { Readable } = require('stream');
    const bodyStream = Readable.from(rawBody);
    
    await new Promise((resolve, reject) => {
      const busboy = Busboy({ headers: req.headers });
      
      busboy.on('file', (fieldname, file, info) => {
        const { filename, encoding, mimeType } = info;
        const chunks = [];
        
        file.on('data', (data) => {
          chunks.push(data);
        });
        
        file.on('end', () => {
          const buffer = Buffer.concat(chunks);
          files.push({
            fieldname,
            originalname: filename,
            mimetype: mimeType,
            size: buffer.length,
            buffer
          });
        });
      });
      
      busboy.on('field', (fieldname, value) => {
        fields[fieldname] = value;
      });
      
      busboy.on('close', resolve);
      busboy.on('error', reject);
      
      bodyStream.pipe(busboy);
    });
    
    // Reconstruct multipart body info for storage
    const parts = files.map(f => ({
      fieldname: f.fieldname,
      originalname: f.originalname,
      mimetype: f.mimetype,
      size: f.size,
      // Store small parts as base64, skip large binary data
      data: f.size < 10000 ? f.buffer.toString('base64') : `[${f.size} bytes - too large to store]`
    }));
    
    // Add fields to parts info
    if (Object.keys(fields).length > 0) {
      parts.push({ type: 'fields', data: fields });
    }
    
    // Calculate total size
    const totalSize = files.reduce((sum, f) => sum + f.size, 0);
    
    // Store the request
    const record = storage.save({
      timestamp: Date.now(),
      feature,
      method: req.method,
      path: req.originalUrl,
      headers: JSON.stringify(headers),
      rawBodySize: totalSize,
      rawBody: null,  // Multipart is stored in parts
      decompressedBody: JSON.stringify(parts, null, 2),
      decompressError: null,
      contentType: req.headers['content-type'],
      encoding: null,
      isMultipart: 1,
      partCount: files.length
    });
    
    console.log(`📥 [${feature}] Captured multipart request #${record.id} (${files.length} parts, ${totalSize} bytes)`);
    
    // Forward to Datadog - use the original raw body to preserve exact format
    const forwardResult = await forwarder.forwardMultipartRaw(req, rawBody, feature);
    
    // Update record with forward result
    storage.updateForwardResult(record.id, forwardResult);
    
    // Return Datadog's response to SDK
    res.status(forwardResult.status).send(forwardResult.body);
    
  } catch (error) {
    console.error(`❌ Error processing multipart ${feature} request:`, error);
    res.status(500).json({ error: error.message });
  }
}

module.exports = router;
