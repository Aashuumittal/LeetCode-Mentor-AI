const AiApi = {
  generate: async (requestData, signal) => {
    const token = await TokenUtil.getValidAccessToken();
    if (!token) {
      throw new Error('User is not authenticated. Please log in.');
    }

    return await ApiBridge.request('/api/ai/generate', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: requestData,
      signal
    });
  }
};
