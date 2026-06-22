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

AiApi.prefetch = async (problemSlug, problemTitle, problemDescription, language) => {
  try {
    const token = await TokenUtil.getValidAccessToken();
    if (!token) return;
    await ApiBridge.request('/api/ai/prefetch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: { problemSlug, problemTitle, problemDescription, language }
    });
  } catch (e) {
    console.warn('Prefetch trigger failed:', e.message);
  }
};

AiApi.getPrefetchStatus = async (slug, language = 'JAVA') => {
  try {
    const token = await TokenUtil.getValidAccessToken();
    if (!token) return null;
    return await ApiBridge.request(
      `/api/ai/prefetch-status?slug=${encodeURIComponent(slug)}&language=${language}`,
      { headers: { 'Authorization': `Bearer ${token}` } }
    );
  } catch (e) {
    console.warn('Prefetch status check failed:', e.message);
    return null;
  }
};
