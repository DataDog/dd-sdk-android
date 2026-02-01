const Database = require('better-sqlite3');
const path = require('path');

let db = null;

/**
 * Initialize the SQLite database
 */
function init() {
  const dbPath = path.join(__dirname, '..', 'data', 'captures.db');
  db = new Database(dbPath);
  
  // Create tables if they don't exist
  db.exec(`
    CREATE TABLE IF NOT EXISTS requests (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      timestamp INTEGER NOT NULL,
      feature TEXT NOT NULL,
      method TEXT NOT NULL,
      path TEXT NOT NULL,
      headers TEXT,
      rawBodySize INTEGER,
      rawBody TEXT,
      decompressedBody TEXT,
      decompressError TEXT,
      contentType TEXT,
      encoding TEXT,
      isMultipart INTEGER DEFAULT 0,
      partCount INTEGER DEFAULT 0,
      eventCount INTEGER DEFAULT 0,
      forwardStatus INTEGER,
      forwardError TEXT,
      forwardedAt INTEGER,
      responseBody TEXT
    );
    
    CREATE INDEX IF NOT EXISTS idx_timestamp ON requests(timestamp DESC);
    CREATE INDEX IF NOT EXISTS idx_feature ON requests(feature);
    CREATE INDEX IF NOT EXISTS idx_eventCount ON requests(eventCount);
  `);
  
  console.log('📦 Database initialized');
}

/**
 * Save a captured request
 */
function save(record) {
  const stmt = db.prepare(`
    INSERT INTO requests (
      timestamp, feature, method, path, headers, rawBodySize, rawBody,
      decompressedBody, decompressError, contentType, encoding,
      isMultipart, partCount, eventCount
    ) VALUES (
      @timestamp, @feature, @method, @path, @headers, @rawBodySize, @rawBody,
      @decompressedBody, @decompressError, @contentType, @encoding,
      @isMultipart, @partCount, @eventCount
    )
  `);
  
  const result = stmt.run({
    timestamp: record.timestamp,
    feature: record.feature,
    method: record.method || 'POST',
    path: record.path,
    headers: record.headers,
    rawBodySize: record.rawBodySize || 0,
    rawBody: record.rawBody || null,
    decompressedBody: record.decompressedBody || null,
    decompressError: record.decompressError || null,
    contentType: record.contentType || null,
    encoding: record.encoding || null,
    isMultipart: record.isMultipart || 0,
    partCount: record.partCount || 0,
    eventCount: record.eventCount || 0
  });
  
  return { id: result.lastInsertRowid, ...record };
}

/**
 * Update forward result for a request
 */
function updateForwardResult(id, result) {
  const stmt = db.prepare(`
    UPDATE requests 
    SET forwardStatus = @status, forwardError = @error, forwardedAt = @forwardedAt
    WHERE id = @id
  `);
  
  stmt.run({
    id,
    status: result.status,
    error: result.error || null,
    forwardedAt: Date.now()
  });
}

/**
 * Update response body for a request (for GET requests)
 */
function updateResponseBody(id, body) {
  const stmt = db.prepare(`
    UPDATE requests SET responseBody = @body WHERE id = @id
  `);
  
  stmt.run({
    id,
    body: typeof body === 'string' ? body : JSON.stringify(body)
  });
}

/**
 * Get all requests with optional filtering
 */
function getAll(options = {}) {
  let query = 'SELECT * FROM requests';
  const params = {};
  const conditions = [];
  
  if (options.feature) {
    conditions.push('feature = @feature');
    params.feature = options.feature;
  }
  
  if (options.since) {
    conditions.push('timestamp >= @since');
    params.since = options.since;
  }
  
  if (options.minEvents) {
    conditions.push('eventCount >= @minEvents');
    params.minEvents = options.minEvents;
  }
  
  if (options.maxEvents) {
    conditions.push('eventCount <= @maxEvents');
    params.maxEvents = options.maxEvents;
  }
  
  if (conditions.length > 0) {
    query += ' WHERE ' + conditions.join(' AND ');
  }
  
  query += ' ORDER BY timestamp DESC';
  
  if (options.limit) {
    query += ' LIMIT @limit';
    params.limit = options.limit;
  }
  
  const stmt = db.prepare(query);
  return stmt.all(params);
}

/**
 * Get a single request by ID
 */
function getById(id) {
  const stmt = db.prepare('SELECT * FROM requests WHERE id = ?');
  return stmt.get(id);
}

/**
 * Get statistics
 */
function getStats() {
  const stats = db.prepare(`
    SELECT 
      feature,
      COUNT(*) as count,
      SUM(rawBodySize) as totalBytes,
      AVG(rawBodySize) as avgBytes,
      MIN(timestamp) as firstSeen,
      MAX(timestamp) as lastSeen
    FROM requests
    GROUP BY feature
  `).all();
  
  const total = db.prepare('SELECT COUNT(*) as count FROM requests').get();
  
  return {
    total: total.count,
    byFeature: stats
  };
}

/**
 * Clear all captured data
 */
function clear() {
  db.exec('DELETE FROM requests');
  console.log('🗑️ All captured data cleared');
}

module.exports = {
  init,
  save,
  updateForwardResult,
  updateResponseBody,
  getAll,
  getById,
  getStats,
  clear
};
