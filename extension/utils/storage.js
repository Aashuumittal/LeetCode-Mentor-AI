const StorageUtil = {
  get: (keys) => {
    return new Promise((resolve) => {
      chrome.storage.local.get(keys, (result) => {
        resolve(result);
      });
    });
  },

  set: (data) => {
    return new Promise((resolve) => {
      chrome.storage.local.set(data, () => {
        resolve();
      });
    });
  },

  remove: (keys) => {
    return new Promise((resolve) => {
      chrome.storage.local.remove(keys, () => {
        resolve();
      });
    });
  },

  clear: () => {
    return new Promise((resolve) => {
      chrome.storage.local.clear(() => {
        resolve();
      });
    });
  },

  getTokens: async () => {
    const data = await StorageUtil.get(['accessToken', 'refreshToken']);
    return {
      accessToken: data.accessToken || null,
      refreshToken: data.refreshToken || null
    };
  },

  setTokens: async (accessToken, refreshToken) => {
    await StorageUtil.set({ accessToken, refreshToken });
  },

  clearTokens: async () => {
    await StorageUtil.remove(['accessToken', 'refreshToken']);
  },

  getUser: async () => {
    const data = await StorageUtil.get('user');
    return data.user || null;
  },

  setUser: async (user) => {
    await StorageUtil.set({ user });
  }
};
