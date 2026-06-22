// Shared helper used by every api/*.js module to route network calls
// through the background service worker (see background.js for why).
const ApiBridge = {
  request: (path, { method = 'GET', headers = {}, body, signal } = {}) => {
    return new Promise((resolve, reject) => {
      const requestId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;

      const onAbort = () => {
        chrome.runtime.sendMessage({ action: 'abortRequest', requestId });
        const err = new Error('Request aborted.');
        err.name = 'AbortError';
        reject(err);
      };
      if (signal) {
        if (signal.aborted) {
          onAbort();
          return;
        }
        signal.addEventListener('abort', onAbort, { once: true });
      }

      chrome.runtime.sendMessage(
        {
          action: 'apiRequest',
          payload: { requestId, path, method, headers, body }
        },
        (response) => {
          if (signal) signal.removeEventListener('abort', onAbort);
          if (chrome.runtime.lastError) {
            reject(new Error(chrome.runtime.lastError.message));
            return;
          }
          if (response && response.aborted) {
            const err = new Error('Request aborted.');
            err.name = 'AbortError';
            reject(err);
            return;
          }
          resolve(response);
        }
      );
    });
  }
};
