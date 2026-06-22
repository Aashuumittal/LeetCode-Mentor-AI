const AiApi = {
  generate: async (requestData, provider, signal) => {
    const token = await TokenUtil.getValidAccessToken();
    if (!token) {
      throw new Error('User is not authenticated. Please log in.');
    }

    const endpoint = provider === 'gemini' ? '/api/ai/gemini/generate' : '/api/ai/generate';
    let retries = 0;
    const maxRetries = 20;

    while (true) {
      if (signal.aborted) {
        throw new DOMException('Aborted', 'AbortError');
      }

      const response = await fetch(`${BACKEND_URL}${endpoint}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(requestData),
        signal: signal
      });

      if (!response.ok) {
        const errJson = await response.json().catch(() => ({}));
        throw new Error(errJson.message || `Server responded with ${response.status}`);
      }

      const resJson = await response.json();
      if (!resJson.success) {
        throw new Error(resJson.message || 'Failed to retrieve AI response');
      }

      const data = resJson.data;
      if (data.status === 'COMPLETED' || data.status === 'SUCCESS') {
        return data.content;
      } else if (data.status === 'FAILED') {
        throw new Error('All AI providers failed to generate this suggestion.');
      } else {
        // Status is PENDING.
        retries++;
        if (retries > maxRetries) {
          throw new Error('AI generation timed out. Please try again.');
        }

        // Wait 1 second before polling again.
        await new Promise((resolve, reject) => {
          const timeout = setTimeout(resolve, 1000);
          signal.addEventListener('abort', () => {
            clearTimeout(timeout);
            reject(new DOMException('Aborted', 'AbortError'));
          });
        });
      }
    }
  },

  prefetch: async (problemSlug, problemTitle, problemDescription, language) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) return;
      await fetch(`${BACKEND_URL}/api/ai/prefetch`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ problemSlug, problemTitle, problemDescription, language })
      });
    } catch (e) {
      console.warn('Prefetch trigger failed:', e.message);
    }
  },

  getPrefetchStatus: async (slug, language = 'JAVA') => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) return null;
      const res = await fetch(
        `${BACKEND_URL}/api/ai/prefetch-status?slug=${encodeURIComponent(slug)}&language=${language}`,
        { headers: { 'Authorization': `Bearer ${token}` } }
      );
      return await res.json();
    } catch (e) {
      console.warn('Prefetch status check failed:', e.message);
      return null;
    }
  }
};
