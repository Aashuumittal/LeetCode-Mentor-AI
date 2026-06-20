const ReviewApi = {
  reviewCode: async (code, language, problemSlug, signal) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      const response = await fetch(`${BASE_URL}/api/review/code`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ code, language, problemSlug }),
        signal: signal
      });

      return await response.json();
    } catch (error) {
      if (error.name === 'AbortError') {
        console.log('Code review request was aborted intentionally.');
      } else {
        console.error('Code review request failed:', error);
      }
      throw error;
    }
  }
};
