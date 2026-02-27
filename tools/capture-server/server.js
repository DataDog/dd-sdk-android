const express = require('express');
const path = require('path');
const captureRoutes = require('./routes/capture');
const uiRoutes = require('./routes/ui');
const storage = require('./lib/storage');

const app = express();
const PORT = process.env.PORT || 8080;

// Initialize storage
storage.init();

// Serve static files for web UI
app.use(express.static(path.join(__dirname, 'public')));

// Mount routes
app.use('/api/v2', captureRoutes);
app.use('/flags', captureRoutes);  // Feature flags assignments endpoint
app.use('/ui-api', uiRoutes);

// Web UI route
app.get('/ui', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Root redirect to UI
app.get('/', (req, res) => {
  res.redirect('/ui');
});

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.listen(PORT, () => {
  console.log(`\n🚀 Datadog Capture Server running at http://localhost:${PORT}`);
  console.log(`📊 Web UI available at http://localhost:${PORT}/ui`);
  console.log(`\n📡 Endpoints:`);
  console.log(`   POST /api/v2/rum       - RUM events`);
  console.log(`   POST /api/v2/logs      - Logs`);
  console.log(`   POST /api/v2/spans     - Traces`);
  console.log(`   POST /api/v2/replay    - Session Replay`);
  console.log(`   POST /api/v2/profile   - Profiling`);
  console.log(`   POST /api/v2/exposures - Feature Flags exposures`);
  console.log(`   GET/POST /flags/*      - Feature Flags assignments\n`);
});
