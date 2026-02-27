// State
let selectedRequestId = null;

// DOM Elements
const requestsList = document.getElementById('requestsList');
const requestDetail = document.getElementById('requestDetail');
const statsEl = document.getElementById('stats');
const featureFilter = document.getElementById('featureFilter');
const limitFilter = document.getElementById('limitFilter');
const minEventsFilter = document.getElementById('minEventsFilter');
const maxEventsFilter = document.getElementById('maxEventsFilter');
const refreshBtn = document.getElementById('refreshBtn');
const clearBtn = document.getElementById('clearBtn');

// Initialize
loadStats();
loadRequests();

// Event listeners
featureFilter.addEventListener('change', loadRequests);
limitFilter.addEventListener('change', loadRequests);
minEventsFilter.addEventListener('change', loadRequests);
maxEventsFilter.addEventListener('change', loadRequests);
refreshBtn.addEventListener('click', () => {
  loadStats();
  loadRequests();
});
clearBtn.addEventListener('click', clearAll);

// Auto-refresh every 5 seconds
setInterval(() => {
  loadStats();
  loadRequests();
}, 5000);

/**
 * Load statistics
 */
async function loadStats() {
  try {
    const response = await fetch('/ui-api/stats');
    const stats = await response.json();
    
    let html = `<div class="stat-item">
      <span class="stat-label">Total</span>
      <span class="stat-value">${stats.total}</span>
    </div>`;
    
    for (const feature of stats.byFeature) {
      html += `<div class="stat-item">
        <span class="stat-label">${formatFeatureName(feature.feature)}</span>
        <span class="stat-value">${feature.count} (${formatBytes(feature.totalBytes)})</span>
      </div>`;
    }
    
    statsEl.innerHTML = html;
  } catch (error) {
    statsEl.innerHTML = `<p class="error">Error loading stats</p>`;
  }
}

/**
 * Load requests list
 */
async function loadRequests() {
  try {
    const feature = featureFilter.value;
    const limit = limitFilter.value;
    const minEvents = minEventsFilter.value;
    const maxEvents = maxEventsFilter.value;
    
    let url = `/ui-api/requests?limit=${limit}`;
    if (feature) url += `&feature=${feature}`;
    if (minEvents) url += `&minEvents=${minEvents}`;
    if (maxEvents) url += `&maxEvents=${maxEvents}`;
    
    const response = await fetch(url);
    const requests = await response.json();
    
    if (requests.length === 0) {
      requestsList.innerHTML = '<p class="placeholder">No requests captured yet</p>';
      return;
    }
    
    let html = '';
    for (const req of requests) {
      const isSelected = req.id === selectedRequestId;
      html += `
        <div class="request-item ${isSelected ? 'selected' : ''}" 
             onclick="selectRequest(${req.id})">
          <div class="request-header">
            <span class="feature-badge feature-${req.feature}">${formatFeatureName(req.feature)}</span>
            <span class="request-time">${formatTime(req.timestamp)}</span>
          </div>
          <div class="request-meta">
            <span>${req.method}</span>
            <span>${formatBytes(req.rawBodySize)}</span>
            ${req.isMultipart ? `<span>${req.partCount} parts</span>` : ''}
            ${formatForwardStatus(req.forwardStatus)}
          </div>
          ${req.eventTypes ? `<div class="event-types">${formatEventTypes(req.eventTypes)}</div>` : (req.eventCount > 0 ? `<div class="event-types"><span class="event-type-pill">Events: ${req.eventCount}</span></div>` : '')}
        </div>
      `;
    }
    
    requestsList.innerHTML = html;
  } catch (error) {
    requestsList.innerHTML = '<p class="error">Error loading requests</p>';
  }
}

/**
 * Select and show request details
 */
async function selectRequest(id) {
  selectedRequestId = id;
  
  // Update selection in list
  document.querySelectorAll('.request-item').forEach(el => {
    el.classList.remove('selected');
  });
  event.currentTarget.classList.add('selected');
  
  try {
    const response = await fetch(`/ui-api/requests/${id}`);
    const req = await response.json();
    
    let html = `
      <div class="detail-section">
        <h4>Request Info</h4>
        <div class="detail-grid">
          <span class="detail-label">ID</span>
          <span class="detail-value">${req.id}</span>
          <span class="detail-label">Feature</span>
          <span class="detail-value"><span class="feature-badge feature-${req.feature}">${req.feature}</span></span>
          <span class="detail-label">Time</span>
          <span class="detail-value">${new Date(req.timestamp).toLocaleString()}</span>
          <span class="detail-label">Method</span>
          <span class="detail-value">${req.method}</span>
          <span class="detail-label">Path</span>
          <span class="detail-value">${req.path}</span>
          <span class="detail-label">Content-Type</span>
          <span class="detail-value">${req.contentType || 'N/A'}</span>
          <span class="detail-label">Encoding</span>
          <span class="detail-value">${req.encoding || 'none'}</span>
          <span class="detail-label">Raw Size</span>
          <span class="detail-value">${formatBytes(req.rawBodySize)}</span>
          <span class="detail-label">Events</span>
          <span class="detail-value">${req.eventCount || 0}${req.eventTypes ? ` &mdash; ${formatEventTypes(JSON.parse(req.eventTypes || '{}'))}` : ''}</span>
        </div>
      </div>
      
      <div class="detail-section">
        <h4>Forward Status</h4>
        <div class="detail-grid">
          <span class="detail-label">Status</span>
          <span class="detail-value">${formatForwardStatusDetail(req.forwardStatus)}</span>
          ${req.forwardError ? `
            <span class="detail-label">Error</span>
            <span class="detail-value error-message">${req.forwardError}</span>
          ` : ''}
        </div>
      </div>
    `;
    
    if (req.parsedHeaders) {
      html += `
        <div class="detail-section">
          <h4>Headers</h4>
          <div class="json-view">${JSON.stringify(req.parsedHeaders, null, 2)}</div>
        </div>
      `;
    }
    
    const rawEvents = Array.isArray(req.parsedBody)
      ? req.parsedBody
      : (req.parsedBody && typeof req.parsedBody === 'object' ? [req.parsedBody] : []);
    // Filter out the empty metadata line {} that the Android SDK prepends to each batch
    const events = rawEvents.filter(e => e && typeof e === 'object' && Object.keys(e).length > 0);
    if (events.length > 0) {
      html += `
        <div class="detail-section">
          <h4>Events (${events.length})</h4>
          <div class="events-list">
            ${events.map((ev, i) => renderEventItem(ev, i)).join('')}
          </div>
        </div>
      `;
    }

    if (req.parsedBody) {
      html += `
        <div class="detail-section">
          <h4>Body (Decompressed)</h4>
          <div class="json-view">${formatJson(req.parsedBody)}</div>
        </div>
      `;
    } else if (req.decompressedBody) {
      html += `
        <div class="detail-section">
          <h4>Body</h4>
          <div class="json-view">${escapeHtml(req.decompressedBody)}</div>
        </div>
      `;
    }

    if (req.decompressError) {
      html += `
        <div class="detail-section">
          <h4>Decompression Error</h4>
          <div class="error-message">${req.decompressError}</div>
        </div>
      `;
    }

    if (req.responseBody) {
      html += `
        <div class="detail-section">
          <h4>Response Body</h4>
          <div class="json-view">${tryFormatJson(req.responseBody)}</div>
        </div>
      `;
    }
    
    requestDetail.innerHTML = html;
  } catch (error) {
    requestDetail.innerHTML = `<p class="error">Error loading request details</p>`;
  }
}

/**
 * Clear all captured data
 */
async function clearAll() {
  if (!confirm('Are you sure you want to delete all captured requests?')) {
    return;
  }
  
  try {
    await fetch('/ui-api/requests', { method: 'DELETE' });
    selectedRequestId = null;
    requestDetail.innerHTML = '<p class="placeholder">Select a request to view details</p>';
    loadStats();
    loadRequests();
  } catch (error) {
    alert('Error clearing data');
  }
}

// Helper functions
function formatFeatureName(feature) {
  const names = {
    'rum': 'RUM',
    'logs': 'Logs',
    'traces': 'Traces',
    'session-replay': 'Session Replay',
    'profiling': 'Profiling',
    'feature-flags-exposures': 'FF Exposures',
    'feature-flags-assignments': 'FF Assignments'
  };
  return names[feature] || feature;
}

function formatBytes(bytes) {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function formatTime(timestamp) {
  const date = new Date(timestamp);
  return date.toLocaleTimeString();
}

function formatForwardStatus(status) {
  if (status === null || status === undefined) {
    return '<span class="forward-status pending">Pending</span>';
  }
  if (status >= 200 && status < 300) {
    return `<span class="forward-status success">${status}</span>`;
  }
  return `<span class="forward-status error">${status}</span>`;
}

function formatForwardStatusDetail(status) {
  if (status === null || status === undefined) {
    return '<span class="forward-status pending">Not forwarded</span>';
  }
  if (status >= 200 && status < 300) {
    return `<span class="forward-status success">Success (${status})</span>`;
  }
  return `<span class="forward-status error">Failed (${status})</span>`;
}

function formatJson(obj) {
  return escapeHtml(JSON.stringify(obj, null, 2));
}

function tryFormatJson(str) {
  try {
    return escapeHtml(JSON.stringify(JSON.parse(str), null, 2));
  } catch {
    return escapeHtml(str);
  }
}

function renderEventItem(ev, index) {
  const type = typeof ev === 'object' && ev !== null ? (ev.type || 'unknown') : 'unknown';
  const summary = getEventSummary(ev, type);
  const json = escapeHtml(JSON.stringify(ev, null, 2));
  return `
    <div class="event-item" id="event-${index}">
      <div class="event-item-header" onclick="toggleEvent(${index})">
        <span class="event-toggle">▶</span>
        <span class="event-type-pill event-type-${type.replace(/_/g, '-')}">${type}</span>
        <span class="event-summary">${summary}</span>
      </div>
      <div class="event-item-body" style="display:none">
        <pre class="json-view">${json}</pre>
      </div>
    </div>
  `;
}

function getEventSummary(ev, type) {
  if (!ev || typeof ev !== 'object') return '';
  const v = ev.view;
  if (type === 'view' || type === 'view_update') {
    return escapeHtml(v?.name || v?.url || '');
  }
  if (type === 'action') return escapeHtml(ev.action?.target?.name || ev.action?.type || '');
  if (type === 'error') return escapeHtml(ev.error?.message || '');
  if (type === 'resource') return escapeHtml(ev.resource?.url || '');
  if (type === 'long_task') return `${ev.long_task?.duration ?? ''}ns`;
  return '';
}

function toggleEvent(index) {
  const item = document.getElementById(`event-${index}`);
  const body = item.querySelector('.event-item-body');
  const arrow = item.querySelector('.event-toggle');
  const expanded = body.style.display !== 'none';
  body.style.display = expanded ? 'none' : 'block';
  arrow.textContent = expanded ? '▶' : '▼';
}

function formatEventTypes(types) {
  return Object.entries(types)
    .sort((a, b) => b[1] - a[1])
    .map(([type, count]) => `<span class="event-type-pill event-type-${type.replace('_', '-')}">${type}: ${count}</span>`)
    .join(' ');
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}
