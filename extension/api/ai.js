const AiApi = {
  generateStream: async (requestData, signal, onToken, onComplete, onError) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) {
        throw new Error('User is not authenticated. Please log in.');
      }

      while (true) {
        if (signal.aborted) {
          console.log('AI generation poll was aborted.');
          return;
        }

        const response = await fetch(`${BASE_URL}/api/ai/generate`, {
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
        if (data.status === 'SUCCESS') {
          onToken(data.content);
          onComplete();
          return;
        } else if (data.status === 'FAILED') {
          throw new Error('All AI providers failed to generate this suggestion.');
        } else {
          // Status is PENDING. Wait 2 seconds before polling again.
          await new Promise((resolve, reject) => {
            const timeout = setTimeout(resolve, 2000);
            signal.addEventListener('abort', () => {
              clearTimeout(timeout);
              reject(new DOMException('Aborted', 'AbortError'));
            });
          });
        }
      }
    } catch (error) {
      if (error.name === 'AbortError') {
        console.log('AI generation stream was aborted intentionally.');
        return;
      }
      console.error('AI Stream failed:', error);
      onError(error);
    }
  }
};

AiApi.prefetch = async (problemSlug, problemTitle, problemDescription, language) => {
  try {
    const token = await TokenUtil.getValidAccessToken();
    if (!token) return;
    await fetch(`${BASE_URL}/api/ai/prefetch`, {
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
};

AiApi.getPrefetchStatus = async (slug, language = 'JAVA') => {
  try {
    const token = await TokenUtil.getValidAccessToken();
    if (!token) return null;
    const res = await fetch(
      `${BASE_URL}/api/ai/prefetch-status?slug=${encodeURIComponent(slug)}&language=${language}`,
      { headers: { 'Authorization': `Bearer ${token}` } }
    );
    return await res.json();
  } catch (e) {
    console.warn('Prefetch status check failed:', e.message);
    return null;
  }
};
