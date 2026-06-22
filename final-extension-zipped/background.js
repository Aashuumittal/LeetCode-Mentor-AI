const BACKEND_URL = 'http://16.192.43.42:9094';

// ─────────────────────────────────────────────────────────────────────────
// All network calls to the backend live here in the service worker.
// LeetCode is served over HTTPS, and content scripts execute inside that
// page's security context — Chrome's mixed-content policy blocks them from
// fetching plain http:// resources (our AWS backend is http://, not https://).
// The background service worker runs in the extension's own context, which
// is NOT subject to the page's mixed-content rules, so all fetch() calls to
// BACKEND_URL must happen here. Content scripts talk to this file via
// chrome.runtime.sendMessage (one-shot request/response) or
// chrome.runtime.connect (long-lived streaming).
// ─────────────────────────────────────────────────────────────────────────

// Track in-flight one-shot requests by id so they can be aborted on request.
const activeRequests = new Map();

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'refreshToken') {
    handleRefreshToken()
      .then(accessToken => sendResponse({ accessToken }))
      .catch(err => {
        console.error('Refresh token failed:', err);
        sendResponse({ accessToken: null });
      });
    return true; // Keep message channel open for asynchronous sendResponse
  }

  if (request.action === 'apiRequest') {
    handleApiRequest(request.payload)
      .then(sendResponse)
      .catch(err => {
        console.error('API request failed:', err);
        sendResponse({ success: false, message: err.message || 'Network error occurred.' });
      });
    return true;
  }

  if (request.action === 'abortRequest') {
    const controller = activeRequests.get(request.requestId);
    if (controller) {
      controller.abort();
      activeRequests.delete(request.requestId);
    }
    sendResponse({ aborted: true });
    return false;
  }
});

// Generic one-shot JSON request handler used by all api/*.js modules
// (auth, progress, revision, review, company-questions, user).
async function handleApiRequest(payload) {
  const { requestId, path, method, headers, body } = payload;
  const controller = new AbortController();
  if (requestId) activeRequests.set(requestId, controller);

  try {
    const response = await fetch(`${BACKEND_URL}${path}`, {
      method: method || 'GET',
      headers: headers || {},
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal
    });

    const status = response.status;
    let json;
    try {
      json = await response.json();
    } catch (e) {
      json = {};
    }

    if (!response.ok && json.success === undefined) {
      // Server returned a non-2xx without our standard envelope shape
      return { success: false, status, message: json.message || `Server responded with ${status}` };
    }

    if (json.status === undefined) json.status = status;
    return json;
  } catch (error) {
    if (error.name === 'AbortError') {
      return { success: false, aborted: true, message: 'Request aborted.' };
    }
    throw error;
  } finally {
    if (requestId) activeRequests.delete(requestId);
  }
}

async function handleRefreshToken() {
  const data = await chrome.storage.local.get(['refreshToken']);
  const refreshToken = data.refreshToken;

  if (!refreshToken) {
    throw new Error('No refresh token available');
  }

  try {
    const response = await fetch(`${BACKEND_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ refreshToken })
    });

    const json = await response.json();
    if (json.success && json.data) {
      await chrome.storage.local.set({
        accessToken: json.data.accessToken,
        refreshToken: json.data.refreshToken,
        user: json.data.user
      });
      return json.data.accessToken;
    } else {
      // Invalidate tokens
      await chrome.storage.local.remove(['accessToken', 'refreshToken', 'user']);
      throw new Error(json.message || 'Token refresh rejected by server');
    }
  } catch (error) {
    console.error('Network error during refresh token exchange:', error);
    await chrome.storage.local.remove(['accessToken', 'refreshToken', 'user']);
    throw error;
  }
}

