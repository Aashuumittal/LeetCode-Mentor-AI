const TokenUtil = {
  isTokenExpired: (token) => {
    if (!token) return true;
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return true;
      
      // Decode JWT payload (Base64URL)
      const payloadBase64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(atob(payloadBase64));
      
      const now = Math.floor(Date.now() / 1000);
      // Mark as expired if within 30 seconds of expiry
      return payload.exp < (now + 30);
    } catch (e) {
      return true;
    }
  },

  getValidAccessToken: async () => {
    const tokens = await StorageUtil.getTokens();
    if (!tokens.accessToken) {
      return null;
    }

    if (TokenUtil.isTokenExpired(tokens.accessToken)) {
      console.log('Access token expired. Requesting refresh from background...');
      return new Promise((resolve) => {
        chrome.runtime.sendMessage({ action: 'refreshToken' }, (response) => {
          if (response && response.accessToken) {
            resolve(response.accessToken);
          } else {
            console.warn('Failed to refresh token from background');
            resolve(null);
          }
        });
      });
    }

    return tokens.accessToken;
  }
};
