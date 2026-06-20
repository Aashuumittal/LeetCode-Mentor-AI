const BASE_URL = 'http://localhost:9094';

const AuthApi = {
  register: async (name, email, password, preferredLanguage) => {
    try {
      const response = await fetch(`${BASE_URL}/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, password, preferredLanguage })
      });

      const json = await response.json();
      if (json.success && json.data) {
        await StorageUtil.setTokens(json.data.accessToken, json.data.refreshToken);
        await StorageUtil.setUser(json.data.user);
      }
      return json;
    } catch (error) {
      console.error('Registration failed:', error);
      return { success: false, message: 'Network error occurred during registration.' };
    }
  },

  login: async (email, password) => {
    try {
      const response = await fetch(`${BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });

      const json = await response.json();
      if (json.success && json.data) {
        await StorageUtil.setTokens(json.data.accessToken, json.data.refreshToken);
        await StorageUtil.setUser(json.data.user);
      }
      return json;
    } catch (error) {
      console.error('Login failed:', error);
      return { success: false, message: 'Network error occurred during login.' };
    }
  },

  logout: async () => {
    try {
      const tokens = await StorageUtil.getTokens();
      if (tokens.refreshToken) {
        await fetch(`${BASE_URL}/api/auth/logout`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: tokens.refreshToken })
        });
      }
    } catch (error) {
      console.error('Logout request failed:', error);
    } finally {
      await StorageUtil.clearTokens();
      await StorageUtil.setUser(null);
    }
  }
};
