const UserApi = {
  getMe: async () => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      return await ApiBridge.request('/api/user/me', {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${token}` }
      });
    } catch (error) {
      console.error('Fetch user profile failed:', error);
      return { success: false, message: error.message };
    }
  },

  resetProgress: async () => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      return await ApiBridge.request('/api/user/reset', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });
    } catch (error) {
      console.error('Reset progress failed:', error);
      return { success: false, message: error.message };
    }
  }
};
