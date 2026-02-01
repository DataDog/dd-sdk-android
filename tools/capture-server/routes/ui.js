const express = require('express');
const storage = require('../lib/storage');

const router = express.Router();

/**
 * Get all captured requests
 */
router.get('/requests', (req, res) => {
  try {
    const { feature, limit, since, minEvents, maxEvents } = req.query;
    
    const options = {};
    if (feature) options.feature = feature;
    if (limit) options.limit = parseInt(limit);
    if (since) options.since = parseInt(since);
    if (minEvents) options.minEvents = parseInt(minEvents);
    if (maxEvents) options.maxEvents = parseInt(maxEvents);
    
    const requests = storage.getAll(options);
    
    // Don't send raw body in list view (too large)
    const lightRequests = requests.map(r => ({
      id: r.id,
      timestamp: r.timestamp,
      feature: r.feature,
      method: r.method,
      path: r.path,
      rawBodySize: r.rawBodySize,
      contentType: r.contentType,
      encoding: r.encoding,
      isMultipart: r.isMultipart,
      partCount: r.partCount,
      eventCount: r.eventCount || 0,
      forwardStatus: r.forwardStatus,
      forwardError: r.forwardError
    }));
    
    res.json(lightRequests);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * Get a single request with full details
 */
router.get('/requests/:id', (req, res) => {
  try {
    const request = storage.getById(parseInt(req.params.id));
    
    if (!request) {
      return res.status(404).json({ error: 'Request not found' });
    }
    
    // Parse headers
    if (request.headers) {
      request.parsedHeaders = JSON.parse(request.headers);
    }
    
    // Try to parse decompressed body as JSON for pretty display
    if (request.decompressedBody && typeof request.decompressedBody === 'string') {
      try {
        // Check if it's NDJSON (newline delimited)
        if (request.decompressedBody.includes('\n')) {
          const lines = request.decompressedBody.split('\n').filter(l => l.trim());
          request.parsedBody = lines.map(line => {
            try {
              return JSON.parse(line);
            } catch {
              return line;
            }
          });
        } else {
          request.parsedBody = JSON.parse(request.decompressedBody);
        }
      } catch {
        // Keep as string if not valid JSON
      }
    }
    
    res.json(request);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * Get statistics
 */
router.get('/stats', (req, res) => {
  try {
    const stats = storage.getStats();
    res.json(stats);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * Clear all captured data
 */
router.delete('/requests', (req, res) => {
  try {
    storage.clear();
    res.json({ success: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * Export all captured data
 */
router.get('/export', (req, res) => {
  try {
    const requests = storage.getAll();
    res.setHeader('Content-Disposition', 'attachment; filename=captured-requests.json');
    res.json(requests);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;
