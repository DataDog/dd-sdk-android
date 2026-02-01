const fetch = require('node-fetch');
const FormData = require('form-data');

// Map feature types to their real Datadog endpoints
const DATADOG_ENDPOINTS = {
  'rum': 'https://rum.browser-intake-datadoghq.com/api/v2/rum',
  'logs': 'https://logs.browser-intake-datadoghq.com/api/v2/logs',
  'traces': 'https://trace.browser-intake-datadoghq.com/api/v2/spans',
  'session-replay': 'https://session-replay.browser-intake-datadoghq.com/api/v2/replay',
  'profiling': 'https://intake.profile.datadoghq.com/api/v2/profile',
  'feature-flags-exposures': 'https://browser-intake-datadoghq.com/api/v2/exposures',
  // Feature flags assignments use dynamic CDN URLs based on the original request
  'feature-flags-assignments': null  
};

// Headers to forward to Datadog
const HEADERS_TO_FORWARD = [
  'content-type',
  'content-encoding',
  'dd-api-key',
  'dd-evp-origin',
  'dd-evp-origin-version',
  'dd-request-id',
  'user-agent'
];

/**
 * Build headers object for forwarding
 */
function buildForwardHeaders(originalHeaders) {
  const headers = {};
  
  for (const key of HEADERS_TO_FORWARD) {
    if (originalHeaders[key]) {
      headers[key] = originalHeaders[key];
    }
  }
  
  return headers;
}

/**
 * Forward a POST request to Datadog
 */
async function forward(req, rawBody, feature) {
  const endpoint = DATADOG_ENDPOINTS[feature];
  
  if (!endpoint) {
    console.log(`⚠️ No forwarding endpoint configured for ${feature}`);
    return { 
      status: 202, 
      body: '{"status": "captured_only"}',
      error: 'No forwarding endpoint configured'
    };
  }
  
  try {
    // Build the URL with query parameters
    const url = new URL(endpoint);
    const originalUrl = new URL(req.originalUrl, `http://${req.headers.host}`);
    
    // Copy query parameters
    originalUrl.searchParams.forEach((value, key) => {
      url.searchParams.set(key, value);
    });
    
    const headers = buildForwardHeaders(req.headers);
    
    console.log(`📤 Forwarding to ${url.toString()}`);
    
    const response = await fetch(url.toString(), {
      method: 'POST',
      headers,
      body: rawBody
    });
    
    const responseBody = await response.text();
    
    console.log(`✅ Forward response: ${response.status}`);
    
    return {
      status: response.status,
      body: responseBody,
      error: null
    };
    
  } catch (error) {
    console.error(`❌ Forward error for ${feature}:`, error.message);
    return {
      status: 502,
      body: JSON.stringify({ error: 'Forward failed', message: error.message }),
      error: error.message
    };
  }
}

/**
 * Forward a GET request to Datadog (for Feature Flags assignments)
 */
async function forwardGet(req, feature) {
  // For feature flags, we need to extract the original URL from the request
  // The SDK would have been configured to point to us, but we need the real CDN URL
  // For now, we'll return a mock response since the CDN URL depends on customer config
  
  console.log(`⚠️ Feature Flags assignments GET request - forwarding not fully implemented`);
  console.log(`   Original path: ${req.originalUrl}`);
  
  // Return a mock "no flags" response so the SDK doesn't break
  return {
    status: 200,
    body: JSON.stringify({ flags: [] }),
    contentType: 'application/json',
    error: 'Forwarding not implemented for feature flags assignments - requires customer-specific CDN URL'
  };
}

/**
 * Forward a multipart request to Datadog
 */
async function forwardMultipart(req, files, feature) {
  const endpoint = DATADOG_ENDPOINTS[feature];
  
  if (!endpoint) {
    console.log(`⚠️ No forwarding endpoint configured for ${feature}`);
    return { 
      status: 202, 
      body: '{"status": "captured_only"}',
      error: 'No forwarding endpoint configured'
    };
  }
  
  try {
    // Reconstruct the multipart form
    const form = new FormData();
    
    for (const file of files) {
      form.append(file.fieldname, file.buffer, {
        filename: file.originalname,
        contentType: file.mimetype
      });
    }
    
    // Build the URL with query parameters
    const url = new URL(endpoint);
    const originalUrl = new URL(req.originalUrl, `http://${req.headers.host}`);
    
    // Copy query parameters
    originalUrl.searchParams.forEach((value, key) => {
      url.searchParams.set(key, value);
    });
    
    // Build headers (exclude content-type, let form-data set it)
    const headers = {};
    for (const key of HEADERS_TO_FORWARD) {
      if (key !== 'content-type' && req.headers[key]) {
        headers[key] = req.headers[key];
      }
    }
    
    console.log(`📤 Forwarding multipart to ${url.toString()}`);
    
    const response = await fetch(url.toString(), {
      method: 'POST',
      headers: {
        ...headers,
        ...form.getHeaders()
      },
      body: form
    });
    
    const responseBody = await response.text();
    
    console.log(`✅ Forward response: ${response.status}`);
    
    return {
      status: response.status,
      body: responseBody,
      error: null
    };
    
  } catch (error) {
    console.error(`❌ Forward error for ${feature}:`, error.message);
    return {
      status: 502,
      body: JSON.stringify({ error: 'Forward failed', message: error.message }),
      error: error.message
    };
  }
}

/**
 * Forward a multipart request to Datadog using the raw body
 */
async function forwardMultipartRaw(req, rawBody, feature) {
  const endpoint = DATADOG_ENDPOINTS[feature];
  
  if (!endpoint) {
    console.log(`⚠️ No forwarding endpoint configured for ${feature}`);
    return { 
      status: 202, 
      body: '{"status": "captured_only"}',
      error: 'No forwarding endpoint configured'
    };
  }
  
  try {
    // Build the URL with query parameters
    const url = new URL(endpoint);
    const originalUrl = new URL(req.originalUrl, `http://${req.headers.host}`);
    
    // Copy query parameters
    originalUrl.searchParams.forEach((value, key) => {
      url.searchParams.set(key, value);
    });
    
    // Forward with original content-type header
    const headers = buildForwardHeaders(req.headers);
    
    console.log(`📤 Forwarding multipart to ${url.toString()}`);
    
    const response = await fetch(url.toString(), {
      method: 'POST',
      headers,
      body: rawBody
    });
    
    const responseBody = await response.text();
    
    console.log(`✅ Forward response: ${response.status}`);
    
    return {
      status: response.status,
      body: responseBody,
      error: null
    };
    
  } catch (error) {
    console.error(`❌ Forward error for ${feature}:`, error.message);
    return {
      status: 502,
      body: JSON.stringify({ error: 'Forward failed', message: error.message }),
      error: error.message
    };
  }
}

module.exports = {
  forward,
  forwardGet,
  forwardMultipart,
  forwardMultipartRaw
};
