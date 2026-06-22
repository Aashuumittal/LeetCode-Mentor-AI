const ReviewApi = {
  reviewCode: async (code, language, problemSlug, signal) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      return await ApiBridge.request('/api/review/code', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: { code, language, problemSlug },
        signal: signal
      });
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
