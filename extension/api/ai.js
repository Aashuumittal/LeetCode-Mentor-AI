const AiApi = {
  generateStream: async (requestData, signal, onToken, onComplete, onError) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) {
        throw new Error('User is not authenticated. Please log in.');
      }

      const response = await fetch(`${BASE_URL}/api/ai/generate`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify(requestData),
        signal: signal
      });

      if (!response.ok) {
        const errJson = await response.json().catch(() => ({}));
        throw new Error(errJson.message || `Server responded with ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        
        // Process lines from stream
        let lines = buffer.split('\n');
        // Keep the last partial line in buffer
        buffer = lines.pop();

        for (const line of lines) {
          if (line.startsWith('data:')) {
            let rawValue = line.substring(5);
            // Strip trailing carriage return if present
            if (rawValue.endsWith('\r')) {
              rawValue = rawValue.slice(0, -1);
            }
            // The server JSON-encodes each token (e.g. "\" world\"") so that
            // spaces and whitespace-only tokens survive transport intact.
            // Do NOT heuristically strip a leading space here — a real token
            // can legitimately start with one, and stripping it corrupts text.
            let dataToken;
            try {
              dataToken = JSON.parse(rawValue.trim());
            } catch (e) {
              // Fallback for any legacy/non-JSON payload: trim only the
              // single mandatory space the SSE spec adds after "data:".
              dataToken = rawValue.startsWith(' ') ? rawValue.substring(1) : rawValue;
            }
            if (dataToken === '[DONE]') {
              onComplete();
              return;
            }
            onToken(dataToken);
          } else if (line.startsWith('event: done')) {
            onComplete();
            return;
          } else if (line.startsWith('event: error')) {
            onError(new Error('AI stream generation error.'));
            return;
          }
        }
      }

      onComplete();
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
