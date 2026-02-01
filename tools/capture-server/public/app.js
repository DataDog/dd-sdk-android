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
            ${req.eventCount > 0 ? `<span class="event-count">${req.eventCount} event${req.eventCount > 1 ? 's' : ''}</span>` : ''}
            ${req.isMultipart ? `<span>${req.partCount} parts</span>` : ''}
            ${formatForwardStatus(req.forwardStatus)}
          </div>
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
          <span class="detail-value">${req.eventCount || 0}</span>
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

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}
