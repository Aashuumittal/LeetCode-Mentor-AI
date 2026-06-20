const CompanyApi = {
  getQuestions: async (company, difficulty, topic) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      const params = new URLSearchParams();
      if (company) params.append('company', company);
      if (difficulty) params.append('difficulty', difficulty);
      if (topic) params.append('topic', topic);

      const response = await fetch(`${BASE_URL}/api/company-questions?${params.toString()}`, {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      return await response.json();
    } catch (error) {
      console.error('Fetch company questions failed:', error);
      return { success: false, message: error.message };
    }
  },

  // description is optional but improves AI accuracy when DB has no data
  getFrequenciesForProblem: async (title, slug, description) => {
    try {
      const token = await TokenUtil.getValidAccessToken();
      if (!token) throw new Error('Unauthorized');

      const params = new URLSearchParams();
      params.append('title', title);
      params.append('slug', slug);
      if (description) params.append('description', description);

      const response = await fetch(`${BASE_URL}/api/company-questions/problem?${params.toString()}`, {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      return await response.json();
    } catch (error) {
      console.error('Fetch company frequencies failed:', error);
      return { success: false, message: error.message };
    }
  }
};
