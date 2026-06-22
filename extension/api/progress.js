const ProgressApi = {
  getProgress: async (problemSlug) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      const response = await fetch(`${BACKEND_URL}/api/progress/${problemSlug}`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      return await response.json();
    } catch (error) {
      console.error('Fetch progress failed:', error);
      return { success: false, message: error.message };
    }
  },

  updateProgress: async (problemSlug, approach, hintsUnlocked, solutionViewed, questionExplained) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      const response = await fetch(`${BACKEND_URL}/api/progress/update`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ problemSlug, approach, hintsUnlocked, solutionViewed, questionExplained })
      });

      return await response.json();
    } catch (error) {
      console.error('Update progress failed:', error);
      return { success: false, message: error.message };
    }
  },

  solveProblem: async (problemSlug, difficulty, language, approach, hintsUsed, solutionViewed) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      const response = await fetch(`${BACKEND_URL}/api/progress/solve`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ problemSlug, difficulty, language, approach, hintsUsed, solutionViewed })
      });

      const json = await response.json();
      if (json.success && json.data) {
        // Update local user stats cache (streak)
        const user = await StorageUtil.getUser();
        if (user) {
          user.currentStreak = json.data.currentStreak;
          user.totalSolved = (user.totalSolved || 0) + 1;
          user.totalHintsUsed = (user.totalHintsUsed || 0) + hintsUsed;
          await StorageUtil.setUser(user);
        }
      }
      return json;
    } catch (error) {
      console.error('Solve problem failed:', error);
      return { success: false, message: error.message };
    }
  }
};
