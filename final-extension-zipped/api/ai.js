const AiApi = {
  generate: async (requestData, provider, signal) => {
    const token = await TokenUtil.getValidAccessToken();
    if (!token) {
      throw new Error('User is not authenticated. Please log in.');
    }

    const endpoint = provider === 'gemini' ? '/api/ai/gemini/generate' : '/api/ai/generate';
    return await ApiBridge.request(endpoint, {
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
