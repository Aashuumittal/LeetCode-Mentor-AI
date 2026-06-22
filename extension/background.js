const BACKEND_URL = 'http://16.192.43.42:9094';

// Listen to runtime messages
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'refreshToken') {
    handleRefreshToken()
      .then(accessToken => {
        sendResponse({ accessToken });
      })
      .catch(err => {
        console.error('Refresh token failed:', err);
        sendResponse({ accessToken: null });
      });
    return true; // Keep message channel open for asynchronous sendResponse
  }
});

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
