const RevisionApi = {
  getQueue: async () => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      return await ApiBridge.request('/api/revision/queue', {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${token}` }
      });
    } catch (error) {
      console.error('Fetch revision queue failed:', error);
      return { success: false, message: error.message };
    }
  },

  complete: async (revisionQueueId) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      return await ApiBridge.request('/api/revision/complete', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: { revisionQueueId }
      });
    } catch (error) {
      console.error('Complete revision failed:', error);
      return { success: false, message: error.message };
    }
  },

  getPendingStatus: async () => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      return await ApiBridge.request('/api/revision/pending', {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${token}` }
      });
    } catch (error) {
      console.error('Fetch pending revisions failed:', error);
      return { success: false, message: error.message };
    }
  }
};
