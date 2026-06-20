(function() {
  let rootElement = null;
  let shadow = null;
  let currentSlug = null;
  let activeAiController = null;
  
  // App states
  let currentApproach = 'BRUTEFORCE'; // Default approach
  let problemProgressList = []; // Progress states of approaches for current problem
  let cachedRevisionQueue = { day3: [], day7: [] };

  // SPA navigation detector
  let lastUrl = window.location.href;
  setInterval(() => {
    if (window.location.href !== lastUrl) {
      lastUrl = window.location.href;
      handleUrlChange();
    }
  }, 1000);

  function handleUrlChange() {
    const slug = ScraperUtil.getProblemSlug();
    if (slug) {
      currentSlug = slug;
      if (rootElement) {
        updateProblemContext();
      } else {
        injectFloatingWindow();
      }
    } else {
      if (rootElement) {
        rootElement.style.display = 'none';
      }
    }
  }

  // Initialize
  handleUrlChange();

  async function injectFloatingWindow() {
    if (document.getElementById('leetcode-mentor-root')) return;

    rootElement = document.createElement('div');
    rootElement.id = 'leetcode-mentor-root';
    document.body.appendChild(rootElement);

    shadow = rootElement.attachShadow({ mode: 'open' });

    // Load HTML layout
    try {
      const htmlUrl = chrome.runtime.getURL('ui/floating-window.html');
      const response = await fetch(htmlUrl);
      const html = await response.text();
      
      const containerDiv = document.createElement('div');
      containerDiv.innerHTML = html;
      shadow.appendChild(containerDiv);

      // Load CSS styling
      const styleLink = document.createElement('link');
      styleLink.rel = 'stylesheet';
      styleLink.href = chrome.runtime.getURL('styles.css');
      shadow.appendChild(styleLink);

      // Initialize UI controls
      setupWindowControls();
      setupAuthHandlers();
      setupAppHandlers();
      
      // Update Auth / App views
      await checkAuthenticationState();
      await updateProblemContext();
    } catch (e) {
      console.error('Failed to load LeetCode Mentor floating window:', e);
    }
  }

  async function checkAuthenticationState() {
    const tokens = await StorageUtil.getTokens();
    const user = await StorageUtil.getUser();
    
    const authPanel = shadow.getElementById('mentor-auth-panel');
    const panel = shadow.getElementById('panel');
    const dock = shadow.getElementById('dock');

    if (tokens.accessToken && user) {
      authPanel.style.display = 'none';
      panel.style.pointerEvents = 'auto';
      panel.style.opacity = '1';
      
      // Load user streak
      shadow.getElementById('stat-streak').innerText = (user.currentStreak || 0) + ' 🔥';

      // Load stats from server to sync
      try {
        const response = await fetch(`${BASE_URL}/api/user/me`, {
          method: 'GET',
          headers: {
            'Authorization': `Bearer ${tokens.accessToken}`
          }
        });
        const resJson = await response.json();
        if (resJson.success && resJson.data) {
          const data = resJson.data;
          shadow.getElementById('stat-streak').innerText = (data.currentStreak || 0) + ' 🔥';
          user.currentStreak = data.currentStreak;
          user.totalSolved = data.totalSolved;
          user.totalHintsUsed = data.totalHintsUsed;
          await StorageUtil.setUser(user);
        }
      } catch (err) {
        console.error('Failed to sync user profile stats:', err);
      }

      // Fetch pending revisions and notifications
      await loadRevisionQueue();
    } else {
      authPanel.style.display = 'flex';
      panel.style.pointerEvents = 'none';
      panel.style.opacity = '0.5';
      dock.classList.add('hidden');
    }
  }

  function setupWindowControls() {
    const container = shadow.querySelector('.mentor-window');
    const dragHandle = shadow.getElementById('mentor-drag-handle');
    const resizeHandle = shadow.getElementById('mentor-resize-se');
    const btnMin = shadow.getElementById('mentor-btn-minimize');
    const btnClose = shadow.getElementById('mentor-btn-close');

    // Drag-and-drop
    let isDragging = false;
    let startX, startY, initialLeft, initialTop;

    dragHandle.addEventListener('mousedown', (e) => {
      isDragging = true;
      startX = e.clientX;
      startY = e.clientY;
      initialLeft = container.offsetLeft;
      initialTop = container.offsetTop;
      e.preventDefault();
    });

    document.addEventListener('mousemove', (e) => {
      if (!isDragging) return;
      const deltaX = e.clientX - startX;
      const deltaY = e.clientY - startY;
      container.style.left = `${initialLeft + deltaX}px`;
      container.style.top = `${initialTop + deltaY}px`;
      container.style.right = 'auto';
    });

    document.addEventListener('mouseup', () => {
      isDragging = false;
    });

    // Resize
    let isResizing = false;
    let startWidth, startHeight;

    resizeHandle.addEventListener('mousedown', (e) => {
      isResizing = true;
      startX = e.clientX;
      startY = e.clientY;
      startWidth = container.offsetWidth;
      startHeight = container.offsetHeight;
      e.preventDefault();
      e.stopPropagation();
    });

    document.addEventListener('mousemove', (e) => {
      if (!isResizing) return;
      const newWidth = startWidth + (e.clientX - startX);
      const newHeight = startHeight + (e.clientY - startY);
      container.style.width = `${newWidth}px`;
      container.style.height = `${newHeight}px`;
    });

    document.addEventListener('mouseup', () => {
      isResizing = false;
    });

    // Minimize / Toggle
    btnMin.addEventListener('click', () => {
      container.classList.toggle('minimized');
      btnMin.innerText = container.classList.contains('minimized') ? '➕' : '−';
    });

    // Close
    btnClose.addEventListener('click', () => {
      rootElement.style.display = 'none';
    });
  }

  function setupAuthHandlers() {
    const tabLogin = shadow.getElementById('auth-tab-login-btn');
    const tabRegister = shadow.getElementById('auth-tab-register-btn');
    const formLogin = shadow.getElementById('mentor-login-form');
    const formRegister = shadow.getElementById('mentor-register-form');

    // Switch Auth Tabs
    tabLogin.addEventListener('click', () => {
      tabLogin.classList.add('active');
      tabRegister.classList.remove('active');
      formLogin.classList.add('active');
      formRegister.classList.remove('active');
    });

    tabRegister.addEventListener('click', () => {
      tabRegister.classList.add('active');
      tabLogin.classList.remove('active');
      formRegister.classList.add('active');
      formLogin.classList.remove('active');
    });

    // Login Form Submit
    formLogin.addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = shadow.getElementById('login-email').value;
      const password = shadow.getElementById('login-password').value;
      const errorMsg = shadow.getElementById('login-error');
      errorMsg.innerText = '';

      const res = await AuthApi.login(email, password);
      if (res.success) {
        await checkAuthenticationState();
        await updateProblemContext();
      } else {
        errorMsg.innerText = res.message || 'Login failed.';
      }
    });

    // Register Form Submit
    formRegister.addEventListener('submit', async (e) => {
      e.preventDefault();
      const name = shadow.getElementById('reg-name').value;
      const email = shadow.getElementById('reg-email').value;
      const password = shadow.getElementById('reg-password').value;
      const preferredLanguage = shadow.getElementById('reg-language').value;
      const errorMsg = shadow.getElementById('reg-error');
      errorMsg.innerText = '';

      const res = await AuthApi.register(name, email, password, preferredLanguage);
      if (res.success) {
        await checkAuthenticationState();
        await updateProblemContext();
      } else {
        errorMsg.innerText = res.message || 'Registration failed.';
      }
    });
  }

  function setupAppHandlers() {
    // Tree sections headers
    const headerSolve = shadow.getElementById('section-solve').querySelector('.tree-header');
    const headerRevise = shadow.getElementById('section-revise').querySelector('.tree-header');
    const tabCodeReview = shadow.getElementById('section-review').querySelector('.tree-header');

    const contentSolve = shadow.getElementById('solve-content');
    const contentRevise = shadow.getElementById('revise-content');

    headerSolve.addEventListener('click', () => {
      const isActive = contentSolve.classList.toggle('active');
      headerSolve.querySelector('.section-arrow').innerText = isActive ? '▲' : '▼';
      if (isActive) {
        contentRevise.classList.remove('active');
        headerRevise.querySelector('.section-arrow').innerText = '▼';
        // Select active approach by default when expanding Solve
        handleApproachSwitch(currentApproach, shadow.getElementById('btn-approach-' + currentApproach.toLowerCase()));
      }
    });

    headerRevise.addEventListener('click', () => {
      const isActive = contentRevise.classList.toggle('active');
      headerRevise.querySelector('.section-arrow').innerText = isActive ? '▲' : '▼';
      if (isActive) {
        contentSolve.classList.remove('active');
        headerSolve.querySelector('.section-arrow').innerText = '▼';
        // Select 3rd day revision item by default when expanding Revise
        const day3Btn = shadow.getElementById('btn-revise-day3');
        selectItem(day3Btn);
        switchDisplayView('view-revision');
        shadow.getElementById('revision-view-title').innerText = '3rd Day Revision Queue';
        renderRevisionView('DAY_3');
      }
    });

    tabCodeReview.addEventListener('click', () => {
      // Collapse accordion content
      contentSolve.classList.remove('active');
      headerSolve.querySelector('.section-arrow').innerText = '▼';
      contentRevise.classList.remove('active');
      headerRevise.querySelector('.section-arrow').innerText = '▼';

      // Mark code review tab header active
      shadow.querySelectorAll('.item').forEach(item => item.classList.remove('active'));
      shadow.querySelectorAll('.tree-header').forEach(header => header.classList.remove('active'));
      tabCodeReview.classList.add('active');

      switchDisplayView('view-code-review');

      // Autofill editor code
      const code = getMonacoCode();
      if (code) {
        shadow.getElementById('code-review-textarea').value = code;
      }
    });

    // Solve Items clicks
    shadow.getElementById('btn-ai-explain').addEventListener('click', () => {
      selectItem(shadow.getElementById('btn-ai-explain'));
      switchDisplayView('view-console');
      triggerAiHint('EXPLAIN');
    });

    shadow.getElementById('btn-approach-bruteforce').addEventListener('click', (e) => {
      handleApproachSwitch('BRUTEFORCE', e.currentTarget);
    });

    shadow.getElementById('btn-approach-optimized').addEventListener('click', (e) => {
      handleApproachSwitch('OPTIMIZED', e.currentTarget);
    });

    shadow.getElementById('btn-approach-optimal').addEventListener('click', (e) => {
      handleApproachSwitch('OPTIMAL', e.currentTarget);
    });

    // Revise Items clicks
    shadow.getElementById('btn-revise-day3').addEventListener('click', (e) => {
      selectItem(e.currentTarget);
      switchDisplayView('view-revision');
      shadow.getElementById('revision-view-title').innerText = '3rd Day Revision Queue';
      renderRevisionView('DAY_3');
    });

    shadow.getElementById('btn-revise-day7').addEventListener('click', (e) => {
      selectItem(e.currentTarget);
      switchDisplayView('view-revision');
      shadow.getElementById('revision-view-title').innerText = '7th Day Revision Queue';
      renderRevisionView('DAY_7');
    });

    // Logout
    shadow.getElementById('btn-profile-logout').addEventListener('click', async () => {
      await AuthApi.logout();
      await checkAuthenticationState();
    });

    // Reset Progress
    shadow.getElementById('btn-profile-reset').addEventListener('click', async () => {
      if (confirm('Are you absolutely sure you want to reset all progress? This will erase your streaks, revision queues, and problem logs forever.')) {
        try {
          const token = await TokenUtil.getValidAccessToken();
          const response = await fetch(`${BASE_URL}/api/user/reset`, {
            method: 'POST',
            headers: {
              'Authorization': `Bearer ${token}`
            }
          });
          const resJson = await response.json();
          if (resJson.success) {
            alert('All progress cleared successfully.');
            await checkAuthenticationState();
            await updateProblemContext();
          } else {
            alert('Reset failed: ' + resJson.message);
          }
        } catch (err) {
          alert('Network error while resetting progress.');
        }
      }
    });

    // Dock buttons listeners
    shadow.getElementById('btn-dock-hint1').addEventListener('click', () => triggerAiHint('HINT_1'));
    shadow.getElementById('btn-dock-hint2').addEventListener('click', () => triggerAiHint('HINT_2'));
    shadow.getElementById('btn-dock-hint3').addEventListener('click', () => triggerAiHint('HINT_3'));
    shadow.getElementById('btn-dock-hint4').addEventListener('click', () => triggerAiHint('HINT_4'));
    shadow.getElementById('btn-dock-solution').addEventListener('click', () => triggerAiHint('SOLUTION'));
    shadow.getElementById('btn-mark-solved-sidebar').addEventListener('click', triggerMarkSolved);

    // Clear output
    shadow.getElementById('btn-clear-output').addEventListener('click', () => {
      shadow.getElementById('ai-output-box').innerHTML = 'Console cleared.';
    });

    // Code Review Action
    shadow.getElementById('btn-run-code-review').addEventListener('click', triggerCodeReview);
    
    shadow.getElementById('btn-clear-review-output').addEventListener('click', () => {
      shadow.getElementById('code-review-output-box').innerHTML = 'Review report cleared.';
    });

    // Sidebar Slide Toggle
    const sidebarWrapper = shadow.getElementById('sidebar-wrapper');
    const t = shadow.getElementById('toggle');
    t.addEventListener('click', () => {
      const closed = sidebarWrapper.classList.toggle('closed');
      t.innerHTML = closed ? '❯' : '❮';
    });

    // Event delegation for copying code blocks inside Shadow DOM
    shadow.addEventListener('click', (e) => {
      const copyBtn = e.target.closest('.code-block-copy-btn');
      if (copyBtn) {
        const codeContainer = copyBtn.closest('.code-block-container');
        if (codeContainer) {
          const preElement = codeContainer.querySelector('pre');
          if (preElement) {
            const codeText = preElement.innerText;
            navigator.clipboard.writeText(codeText).then(() => {
              copyBtn.textContent = 'Copied!';
              copyBtn.classList.add('copied');
              setTimeout(() => {
                copyBtn.textContent = 'Copy';
                copyBtn.classList.remove('copied');
              }, 2000);
            }).catch(err => {
              console.error('Failed to copy code: ', err);
            });
          }
        }
      }
    });
  }

  function selectItem(clickedItemEl) {
    shadow.querySelectorAll('.item').forEach(item => item.classList.remove('active'));
    shadow.querySelectorAll('.tree-header').forEach(header => header.classList.remove('active'));
    clickedItemEl.classList.add('active');
  }

  function switchDisplayView(viewId) {
    shadow.querySelectorAll('.display-view').forEach(view => {
      view.classList.remove('active');
    });
    shadow.getElementById(viewId).classList.add('active');
    
    // Refresh dock visibility
    refreshBottomDock();
  }

  function handleApproachSwitch(approach, btnEl) {
    currentApproach = approach;
    selectItem(btnEl);
    switchDisplayView('view-console');
    refreshBottomDock();

    const outputBox = shadow.getElementById('ai-output-box');
    const existing = problemProgressList.find(p => p.approach === approach);
    const label = approach === 'BRUTEFORCE' ? 'Brute Force' : approach === 'OPTIMIZED' ? 'Optimised' : 'Optimal';
    
    if (existing && existing.hintsUnlocked > 0) {
      let msg = `<h3>${label} Approach</h3><p>Approach selected. You have unlocked up to Hint ${existing.hintsUnlocked}. Click the active buttons below to re-view the hints.</p>`;
      if (existing.solutionViewed) {
        msg += `<p>You have also viewed the full Solution for this approach.</p>`;
      }
      outputBox.innerHTML = msg;
    } else {
      outputBox.innerHTML = `<h3>${label} Approach</h3><p>Approach selected. Click <strong>Hint 1</strong> in the dock at the bottom to receive conceptual guidance.</p>`;
    }
  }

  async function updateProblemContext() {
    rootElement.style.display = 'block';
    
    const problem = ScraperUtil.scrapeAll();
    shadow.getElementById('current-problem-title').innerText = problem.problemTitle || 'LeetCode Problem';
    
    const diffBadge = shadow.getElementById('current-problem-difficulty');
    diffBadge.innerText = problem.difficulty || 'MEDIUM';
    diffBadge.className = 'badge ' + (problem.difficulty ? problem.difficulty.toLowerCase() : 'medium');

    // Reset approach back to default
    currentApproach = 'BRUTEFORCE';
    selectItem(shadow.getElementById('btn-approach-bruteforce'));

    // Fetch the new progress list
    if (currentSlug) {
      const res = await ProgressApi.getProgress(currentSlug);
      if (res.success && res.data) {
        problemProgressList = res.data;
      } else {
        problemProgressList = [];
      }
    }

    switchDisplayView('view-console');
    refreshBottomDock();

    // Default welcome screen
    const outputBox = shadow.getElementById('ai-output-box');
    outputBox.innerHTML = `<h3>Welcome to LeetCode Mentor!</h3><p>Select "Explain Question" or one of the approaches from the sidebar menu to begin.</p>`;

    // Fire-and-forget prefetch — cache all 16 content types in background
    triggerPrefetchAndTrack(problem);
  }

  function refreshBottomDock() {
    const isSolveActive = shadow.getElementById('view-console').classList.contains('active');
    const dock = shadow.getElementById('dock');
    if (!isSolveActive) {
      dock.classList.add('hidden');
      return;
    }
    dock.classList.remove('hidden');

    const progress = problemProgressList.find(p => p.approach === currentApproach) || {
      hintsUnlocked: 0,
      solutionViewed: false,
      isSolved: false
    };

    const hintsUnlocked = progress.hintsUnlocked || 0;
    const solutionViewed = progress.solutionViewed || false;
    const isSolved = progress.isSolved || false;

    const btnHint1 = shadow.getElementById('btn-dock-hint1');
    const btnHint2 = shadow.getElementById('btn-dock-hint2');
    const btnHint3 = shadow.getElementById('btn-dock-hint3');
    const btnHint4 = shadow.getElementById('btn-dock-hint4');
    const btnSolution = shadow.getElementById('btn-dock-solution');
    const btnSolved = shadow.getElementById('btn-mark-solved-sidebar');

    // Reset actives
    [btnHint1, btnHint2, btnHint3, btnHint4, btnSolution].forEach(btn => btn.classList.remove('active'));

    // Enable/disable locks
    btnHint1.disabled = false;
    btnHint1.className = 'dock-btn enabled';

    btnHint2.disabled = (hintsUnlocked < 1);
    updateLockStatus(btnHint2, hintsUnlocked >= 1, 'Hint 2');

    btnHint3.disabled = (hintsUnlocked < 2);
    updateLockStatus(btnHint3, hintsUnlocked >= 2, 'Hint 3');

    btnHint4.disabled = (hintsUnlocked < 3);
    updateLockStatus(btnHint4, hintsUnlocked >= 3, 'Hint 4');

    btnSolution.disabled = (hintsUnlocked < 4);
    updateLockStatus(btnSolution, hintsUnlocked >= 4 || solutionViewed, 'Solution');

    // solved styling
    if (isSolved) {
      btnSolved.classList.add('solved');
      btnSolved.innerHTML = `<span class="solve-action-icon">🎉</span> SOLVED`;
    } else {
      btnSolved.classList.remove('solved');
      btnSolved.innerHTML = `<span class="solve-action-icon">🏆</span> MARK AS SOLVED`;
    }
  }

  function updateLockStatus(btn, unlocked, label) {
    const icon = label === 'Solution' ? '✔️' : '💡';
    btn.innerHTML = `<span class="dock-btn-icon">${icon}</span> ${label}`;
    
    if (unlocked) {
      btn.className = 'dock-btn enabled';
    } else {
      btn.className = 'dock-btn';
      const lockSpan = document.createElement('span');
      lockSpan.className = 'lock-text';
      lockSpan.innerText = ' 🔒';
      btn.appendChild(lockSpan);
    }
  }

  let hintCounts = { 'HINT_1': 1, 'HINT_2': 2, 'HINT_3': 3, 'HINT_4': 4 };
  async function triggerAiHint(contentType) {
    if (activeAiController) {
      // Abort active AI request (stream or review) if user clicks another button
      activeAiController.abort();
    }
    const currentController = new AbortController();
    activeAiController = currentController;
    
    const problem = ScraperUtil.scrapeAll();
    const approach = currentApproach;
    
    const user = await StorageUtil.getUser();
    const language = user ? user.preferredLanguage : 'JAVA';

    const outputBox = shadow.getElementById('ai-output-box');
    outputBox.innerHTML = '<span class="pulse-text">AI is thinking...</span>';

    // Highlight active dock button
    shadow.querySelectorAll('.dock-btn').forEach(btn => btn.classList.remove('active'));
    
    if (contentType.startsWith('HINT_')) {
      const num = contentType.split('_')[1];
      shadow.getElementById('btn-dock-hint' + num).classList.add('active');
    } else if (contentType === 'SOLUTION') {
      shadow.getElementById('btn-dock-solution').classList.add('active');
    } else if (contentType === 'EXPLAIN') {
      selectItem(shadow.getElementById('btn-ai-explain'));
    }

    // Company Tags Integration
    if (contentType === 'EXPLAIN') {
      const companyTagsContainer = shadow.getElementById('company-tags-container');
      if (companyTagsContainer) {
        companyTagsContainer.innerHTML = '<span class="pulse-text" style="font-size: 11px;">Loading company data...</span>';
        companyTagsContainer.classList.remove('hidden');
        
        CompanyApi.getFrequenciesForProblem(
            problem.problemTitle,
            problem.problemSlug,
            problem.problemDescription
          )
          .then(res => {
            if (res && res.success && res.data && res.data.length > 0) {
              const companiesList = res.data.map(item => ({
                companyName: item.company,
                frequency: item.frequency
              }));
              companyTagsContainer.innerHTML = `
                <div class="company-tags-title">Target Companies:</div>
                <div class="company-tags-list">
                  ${companiesList.map(c => `
                    <span class="company-tag ${c.companyName.toLowerCase()}">
                      ${c.companyName} - Asked ${c.frequency}+ times
                    </span>
                  `).join('')}
                </div>
              `;
            } else {
              // AI returned no data for this problem
              companyTagsContainer.innerHTML = `
                <div class="company-tags-title">Target Companies:</div>
                <div class="company-tags-list">
                  <span style="font-size:11px;opacity:0.6;">No company data available for this problem.</span>
                </div>
              `;
            }
          })
          .catch(err => {
            console.error('Error fetching company tags:', err);
            companyTagsContainer.innerHTML = `
              <div class="company-tags-title">Target Companies:</div>
              <div class="company-tags-list">
                <span style="font-size:11px;opacity:0.6;">Could not load company data.</span>
              </div>
            `;
          });
      }
    } else {
      const companyTagsContainer = shadow.getElementById('company-tags-container');
      if (companyTagsContainer) {
        companyTagsContainer.classList.add('hidden');
        companyTagsContainer.innerHTML = '';
      }
    }

    const req = {
      problemSlug: problem.problemSlug,
      problemTitle: problem.problemTitle,
      problemDescription: problem.problemDescription,
      approach: approach,
      contentType: contentType,
      language: language
    };

    let fullText = '';

    try {
      await AiApi.generateStream(
        req,
        currentController.signal,
        (token) => {
          if (fullText === '') outputBox.innerHTML = '';
          fullText += token;
          outputBox.innerHTML = formatMarkdown(fullText);
          outputBox.scrollTop = outputBox.scrollHeight;
        },
        async () => {
          if (activeAiController === currentController) {
            activeAiController = null;
          }
          console.log('Stream completed for content type:', contentType);
          
          // Log progress update back to server
          if (contentType.startsWith('HINT_') || contentType === 'SOLUTION' || contentType === 'EXPLAIN') {
            const isHint = contentType.startsWith('HINT_');
            const num = isHint ? hintCounts[contentType] : 0;
            
            const prevProgress = problemProgressList.find(p => p.approach === approach) || {};
            const prevHintsUnlocked = prevProgress.hintsUnlocked || 0;
            const prevSolutionViewed = prevProgress.solutionViewed || false;
            const prevQuestionExplained = prevProgress.questionExplained || false;

            const updatedHintsUnlocked = Math.max(prevHintsUnlocked, num);
            const updatedSolutionViewed = prevSolutionViewed || (contentType === 'SOLUTION');
            const updatedQuestionExplained = prevQuestionExplained || (contentType === 'EXPLAIN');

            const res = await ProgressApi.updateProgress(
              problem.problemSlug,
              approach,
              updatedHintsUnlocked,
              updatedSolutionViewed,
              updatedQuestionExplained
            );
            
            if (res.success && res.data) {
              const idx = problemProgressList.findIndex(p => p.approach === approach);
              if (idx !== -1) {
                problemProgressList[idx] = res.data;
              } else {
                problemProgressList.push(res.data);
              }
              refreshBottomDock();
            }
          }
        },
        (err) => {
          if (activeAiController === currentController) {
            activeAiController = null;
          }
          if (err.name !== 'AbortError') {
            outputBox.innerHTML = `<span class="auth-error-msg">Error streaming suggestion: ${err.message}</span>`;
          }
        }
      );
    } catch (e) {
      if (activeAiController === currentController) {
        activeAiController = null;
      }
      outputBox.innerHTML = `<span class="auth-error-msg">Failed to connect: ${e.message}</span>`;
    }
  }

  function getMonacoCode() {
    const editorEl = document.querySelector('.monaco-editor');
    if (editorEl) {
      const textarea = editorEl.querySelector('textarea.textarea');
      if (textarea && textarea.value) return textarea.value;
    }

    const lines = Array.from(document.querySelectorAll('.view-lines .view-line'));
    if (lines.length > 0) {
      return lines.map(line => line.textContent).join('\n');
    }
    return null;
  }

  function getScrapedLanguage() {
    const langBtn = document.querySelector('button[id^="lang-select"]') || document.querySelector('.lang-select-btn');
    if (langBtn) {
      const text = langBtn.innerText.trim().toUpperCase();
      if (text.includes('C++') || text.includes('CPP')) return 'CPP';
      if (text.includes('PYTHON')) return 'PYTHON';
      if (text.includes('JAVA')) return 'JAVA';
      if (text.includes('JAVASCRIPT') || text.includes('JS')) return 'JAVASCRIPT';
    }
    return 'JAVA';
  }

  async function triggerCodeReview() {
    const code = shadow.getElementById('code-review-textarea').value.trim();
    if (!code) {
      alert('Please type or paste some code snippet in the textarea first!');
      return;
    }

    if (activeAiController) {
      activeAiController.abort();
    }
    const currentController = new AbortController();
    activeAiController = currentController;

    const lang = getScrapedLanguage();
    const problem = ScraperUtil.scrapeAll();

    const outputBox = shadow.getElementById('code-review-output-box');
    outputBox.innerHTML = '<span class="pulse-text">AI is performing a detailed Code Review...</span>';

    try {
      const res = await ReviewApi.reviewCode(code, lang, problem.problemSlug, currentController.signal);
      
      if (currentController.signal.aborted) {
        return;
      }
      if (activeAiController === currentController) {
        activeAiController = null;
      }

      if (res.success && res.data) {
        let formattedReview = `### Code Review Report\n\n`;
        
        formattedReview += `#### Complexity Analysis\n`;
        formattedReview += `- **Time Complexity:** Current: \`${res.data.timeComplexity.current}\` | Optimized: \`${res.data.timeComplexity.optimized}\`\n`;
        formattedReview += `  *Explanation:* ${res.data.timeComplexity.explanation}\n`;
        formattedReview += `- **Space Complexity:** Current: \`${res.data.spaceComplexity.current}\` | Optimized: \`${res.data.spaceComplexity.optimized}\`\n`;
        formattedReview += `  *Explanation:* ${res.data.spaceComplexity.explanation}\n\n`;

        if (res.data.syntaxIssues && res.data.syntaxIssues.length > 0) {
          formattedReview += `#### Syntax Issues\n`;
          res.data.syntaxIssues.forEach(issue => {
            formattedReview += `- **Line ${issue.line}:** ${issue.issue}\n  *Fix:* \`${issue.fix}\`\n`;
          });
          formattedReview += `\n`;
        } else {
          formattedReview += `#### Syntax Issues\n- No syntax issues detected.\n\n`;
        }

        if (res.data.logicIssues && res.data.logicIssues.length > 0) {
          formattedReview += `#### Logic Issues\n`;
          res.data.logicIssues.forEach(issue => {
            formattedReview += `- ${issue.description}\n  *Suggestion:* ${issue.suggestion}\n`;
          });
          formattedReview += `\n`;
        } else {
          formattedReview += `#### Logic Issues\n- No logic issues detected.\n\n`;
        }

        if (res.data.optimizations && res.data.optimizations.length > 0) {
          formattedReview += `#### Performance Optimizations\n`;
          res.data.optimizations.forEach(opt => {
            formattedReview += `- ${opt.description}\n  \`\`\`${lang.toLowerCase()}\n${opt.improvedCode}\n  \`\`\`\n`;
          });
          formattedReview += `\n`;
        } else {
          formattedReview += `#### Performance Optimizations\n- No optimizations suggested.\n\n`;
        }

        if (res.data.betterApproach && res.data.betterApproach.description) {
          formattedReview += `#### Architectural Recommendation\n`;
          formattedReview += `**Description:** ${res.data.betterApproach.description}\n`;
          formattedReview += `**Illustration/Example:** ${res.data.betterApproach.example}\n`;
        }

        outputBox.innerHTML = formatMarkdown(formattedReview);
      } else {
        outputBox.innerHTML = `<span class="auth-error-msg">Review failed: ${res.message || 'Verification Error'}</span>`;
      }
    } catch (err) {
      if (activeAiController === currentController) {
        activeAiController = null;
      }
      if (err.name !== 'AbortError' && !currentController.signal.aborted) {
        outputBox.innerHTML = `<span class="auth-error-msg">Review failed: ${err.message}</span>`;
      }
    }
  }

  async function triggerMarkSolved() {
    const problem = ScraperUtil.scrapeAll();
    const approach = currentApproach;
    const lang = getScrapedLanguage();

    const outputBox = shadow.getElementById('ai-output-box');
    outputBox.innerHTML = '<span class="pulse-text">Saving solution logs...</span>';

    // Find local progress details
    const approachProgress = problemProgressList.find(p => p.approach === approach);
    let hintsUsed = 0;
    let solutionViewed = false;

    if (approachProgress) {
      hintsUsed = approachProgress.hintsUnlocked || 0;
      solutionViewed = approachProgress.solutionViewed || false;
    }

    const res = await ProgressApi.solveProblem(
      problem.problemSlug,
      problem.difficulty,
      lang,
      approach,
      hintsUsed,
      solutionViewed
    );

    if (res.success) {
      // Show congrats overlay
      const congratsOverlay = shadow.getElementById('congrats-overlay');
      const streakValueEl = shadow.getElementById('congrats-streak-value');
      const streak = (res.data && res.data.currentStreak) || 0;
      
      if (congratsOverlay && streakValueEl) {
        streakValueEl.innerText = streak + ' Day' + (streak === 1 ? '' : 's');
        congratsOverlay.classList.remove('hidden');
        // Trigger CSS transition
        setTimeout(() => {
          congratsOverlay.classList.add('visible');
        }, 50);

        // Auto fade out after 4 seconds
        setTimeout(() => {
          congratsOverlay.classList.remove('visible');
          setTimeout(async () => {
            congratsOverlay.classList.add('hidden');
            // Update local profile view
            await checkAuthenticationState();
            await updateProblemContext();
          }, 500); // Wait for fade transition
        }, 4000);
      } else {
        // Fallback if overlay elements are missing
        await checkAuthenticationState();
        await updateProblemContext();
      }
    } else {
      outputBox.innerHTML = `<span class="auth-error-msg">Could not log solved state: ${res.message}</span>`;
    }
  }

  async function loadRevisionQueue() {
    const res = await RevisionApi.getQueue();
    if (res.success && res.data) {
      cachedRevisionQueue = res.data;
    } else {
      cachedRevisionQueue = { day3: [], day7: [] };
    }
    await updatePendingNotifications();
  }

  async function updatePendingNotifications() {
    const res = await RevisionApi.getPendingStatus();
    if (res.success && res.data) {
      const day3Pending = res.data.day3HasPending;
      const day7Pending = res.data.day7HasPending;

      shadow.getElementById('dot-revise-day3').style.display = day3Pending ? 'inline-block' : 'none';
      shadow.getElementById('dot-revise-day7').style.display = day7Pending ? 'inline-block' : 'none';
      shadow.getElementById('dot-revise-header').style.display = (day3Pending || day7Pending) ? 'inline-block' : 'none';
    }
  }

  function renderRevisionView(revisionType) {
    const listEl = shadow.getElementById('revision-items-list');
    const items = revisionType === 'DAY_3' ? cachedRevisionQueue.day3 : cachedRevisionQueue.day7;

    if (!items || items.length === 0) {
      listEl.innerHTML = '<p class="empty-msg">No revisions due in this queue.</p>';
      return;
    }

    listEl.innerHTML = '';
    items.forEach(item => {
      const card = document.createElement('div');
      card.className = 'revision-card';

      const title = document.createElement('span');
      title.className = 'revision-card-slug';
      title.innerText = item.problemSlug.replace(/-/g, ' ').toUpperCase();
      title.title = item.problemSlug;

      const row = document.createElement('div');
      row.className = 'revision-card-row';

      const diffBadge = document.createElement('span');
      diffBadge.className = 'badge ' + item.difficulty.toLowerCase();
      diffBadge.innerText = item.difficulty;

      const completeBtn = document.createElement('button');
      completeBtn.className = 'revision-card-btn';
      completeBtn.innerText = 'Complete';
      completeBtn.addEventListener('click', async () => {
        completeBtn.disabled = true;
        completeBtn.innerText = '...';
        const ok = await RevisionApi.complete(item.id);
        if (ok.success) {
          card.remove();
          await loadRevisionQueue();
          renderRevisionView(revisionType);
        } else {
          completeBtn.disabled = false;
          completeBtn.innerText = 'Complete';
        }
      });

      row.appendChild(diffBadge);
      row.appendChild(completeBtn);
      card.appendChild(title);
      card.appendChild(row);
      listEl.appendChild(card);
    });
  }

  function formatMarkdown(text) {
    if (!text) return '';
    let escaped = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // 1. Extract and preprocess code blocks to prevent splitting
    const codeBlocks = [];
    escaped = escaped.replace(/```(\w*)\n([\s\S]*?)```/g, (match, lang, code) => {
      const idx = codeBlocks.length;
      const language = lang || 'Code';
      codeBlocks.push(`
        <div class="code-block-container">
          <div class="code-block-header">
            <span class="code-block-lang">${language.toUpperCase()}</span>
            <button class="code-block-copy-btn">Copy</button>
          </div>
          <pre><code class="language-${language}">${code.trim()}</code></pre>
        </div>
      `);
      return `__CODE_BLOCK_PLACEHOLDER_${idx}__`;
    });

    // Helper to format block content (inline code, bold, lists, steps, tables, etc.)
    function formatBlockContent(blockText) {
      let content = blockText;
      
      // Inline code
      content = content.replace(/`([^`\n]+)`/g, '<code>$1</code>');
      
      // Bold
      content = content.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
      
      // Complexity Cards
      content = content.replace(/(?:^|\n)(?:[-*\s]*)(?:\*\*|###)?Time Complexity(?:\*\*|:)?\s*`?([^`\n]+)`?[\s\S]{0,150}?(?:[-*\s]*)(?:\*\*|###)?Space Complexity(?:\*\*|:)?\s*`?([^`\n]+)`?/gi, (match, timeVal, spaceVal) => {
        return `
          <div class="complexity-container">
            <div class="complexity-card">
              <span class="complexity-title">Time Complexity</span>
              <span class="complexity-value">${timeVal.trim()}</span>
            </div>
            <div class="complexity-card">
              <span class="complexity-title">Space Complexity</span>
              <span class="complexity-value">${spaceVal.trim()}</span>
            </div>
          </div>
        `;
      });
      
      // Custom Step containers
      content = content.replace(/^Step (\d+)[:\-][ \t]*(.*$)/gim, (match, stepNum, stepDesc) => {
        return `<div class="step-container">
          <span class="step-badge">Step ${stepNum}</span>
          <span class="step-desc">${stepDesc}</span>
        </div>`;
      });
      
      // Unordered lists
      content = content.replace(/^\s*[-*]\s+(.*$)/gim, '<li class="ul-li">$1</li>');
      content = content.replace(/(<li class="ul-li">.*<\/li>)/gim, '<ul>$1</ul>');
      content = content.replace(/<\/ul>\s*<ul>/g, '');

      // Ordered lists
      content = content.replace(/^\s*(\d+)\.\s+(.*$)/gim, '<li class="ol-li">$2</li>');
      content = content.replace(/(<li class="ol-li">.*<\/li>)/gim, '<ol>$1</ol>');
      content = content.replace(/<\/ol>\s*<ol>/g, '');

      // Tables
      let lines = content.split('\n');
      let inTable = false;
      let tableHtml = '';
      let formattedLines = [];
      for (let i = 0; i < lines.length; i++) {
        let line = lines[i].trim();
        if (line.startsWith('|')) {
          if (!inTable) {
            inTable = true;
            tableHtml = '<div class="table-container"><table>';
          }
          if (line.match(/^\|[\s-|-]*\|$/)) {
            continue;
          }
          let cols = line.split('|').map(c => c.trim()).filter((c, idx, arr) => idx > 0 && idx < arr.length - 1);
          tableHtml += '<tr>';
          for (let col of cols) {
            if (tableHtml.match(/<tr>/g).length === 1) {
              tableHtml += `<th>${col}</th>`;
            } else {
              tableHtml += `<td>${col}</td>`;
            }
          }
          tableHtml += '</tr>';
        } else {
          if (inTable) {
            inTable = false;
            tableHtml += '</table></div>';
            formattedLines.push(tableHtml);
            tableHtml = '';
          }
          formattedLines.push(lines[i]);
        }
      }
      if (inTable) {
        tableHtml += '</table></div>';
        formattedLines.push(tableHtml);
      }
      content = formattedLines.join('\n');

      // Paragraphs
      let paragraphs = content.split(/\n\n+/);
      content = paragraphs.map(p => {
        p = p.trim();
        if (!p) return '';
        if (p.startsWith('<pre') || p.startsWith('<h') || p.startsWith('<ul') || p.startsWith('<ol') || p.startsWith('<div') || p.startsWith('__CODE_BLOCK_PLACEHOLDER_')) {
          return p;
        }
        return `<p>${p}</p>`;
      }).join('\n');

      // Line breaks
      content = content.replace(/\n/g, '<br>');
      content = content.replace(/<br><div/g, '<div').replace(/<\/div><br>/g, '</div>');
      content = content.replace(/<br><pre/g, '<pre').replace(/<\/pre><br>/g, '</pre>');
      content = content.replace(/<br><ul/g, '<ul').replace(/<\/ul><br>/g, '</ul>');
      content = content.replace(/<br><ol/g, '<ol').replace(/<\/ol><br>/g, '</ol>');

      return content;
    }

    // Now split by markdown headings to form Cards
    const parts = escaped.split(/^(?=#{1,4}\s+)/gm);
    let outputHtml = '';

    for (let part of parts) {
      part = part.trim();
      if (!part) continue;

      const headingMatch = part.match(/^(#{1,4})\s+(.*$)/m);
      if (headingMatch) {
        const level = headingMatch[1].length;
        const rawTitle = headingMatch[2].trim();
        const contentStartIndex = headingMatch[0].length;
        const rawContent = part.substring(contentStartIndex).trim();

        const formattedContent = formatBlockContent(rawContent);

        if (level <= 2) {
          outputHtml += `
            <div class="ai-card main-card">
              <div class="ai-card-header main-header">${rawTitle}</div>
              <div class="ai-card-content">${formattedContent}</div>
            </div>
          `;
        } else {
          outputHtml += `
            <div class="ai-card">
              <div class="ai-card-header">${rawTitle}</div>
              <div class="ai-card-content">${formattedContent}</div>
            </div>
          `;
        }
      } else {
        const formattedContent = formatBlockContent(part);
        outputHtml += `
          <div class="ai-card intro-card">
            <div class="ai-card-content">${formattedContent}</div>
          </div>
        `;
      }
    }

    // Restore Code Blocks
    outputHtml = outputHtml.replace(/__CODE_BLOCK_PLACEHOLDER_(\d+)__/g, (match, idx) => {
      return codeBlocks[parseInt(idx)];
    });

    return outputHtml;
  }

  // ── Prefetch + status badge ───────────────────────────────────────────────

  let prefetchPollInterval = null;

  async function triggerPrefetchAndTrack(problem) {
    const user = await StorageUtil.getUser();
    const language = (user && user.preferredLanguage) || 'JAVA';
    const slug = problem.problemSlug;

    // Show badge immediately as "caching..."
    showPrefetchBadge('pending', '0/16');

    // Tell backend to start prefetching (fire and forget)
    await AiApi.prefetch(slug, problem.problemTitle, problem.problemDescription, language);

    // Clear any previous polling interval
    if (prefetchPollInterval) clearInterval(prefetchPollInterval);

    // Poll every 3 seconds until all 16 tasks are DONE or all have settled
    prefetchPollInterval = setInterval(async () => {
      const res = await AiApi.getPrefetchStatus(slug, language);
      if (!res || !res.success) return;

      const { done, failed, total, pending } = res.data;
      const settled = done + failed;

      if (failed > 0 && pending === 0) {
        // Some failed even after retries
        showPrefetchBadge('failed', `${done}/${total} cached, ${failed} failed`);
        clearInterval(prefetchPollInterval);
        prefetchPollInterval = null;
      } else if (done === total) {
        // All done
        showPrefetchBadge('ready', 'All cached');
        setTimeout(() => hidePrefetchBadge(), 3000); // auto-hide after 3s
        clearInterval(prefetchPollInterval);
        prefetchPollInterval = null;
      } else {
        // Still in progress
        showPrefetchBadge('pending', `${done}/${total} cached...`);
      }
    }, 3000);
  }

  function showPrefetchBadge(state, text) {
    let badge = shadow.getElementById('prefetch-status-badge');
    if (!badge) {
      badge = document.createElement('div');
      badge.id = 'prefetch-status-badge';
      badge.style.cssText = [
        'position:absolute', 'bottom:8px', 'left:8px',
        'font-size:10px', 'padding:3px 8px', 'border-radius:10px',
        'font-weight:600', 'letter-spacing:0.3px', 'z-index:9999',
        'transition:all 0.3s ease', 'pointer-events:none'
      ].join(';');
      const panel = shadow.getElementById('panel');
      if (panel) panel.appendChild(badge);
    }

    const styles = {
      pending: 'background:#2a2a3a;color:#a0a0c0;border:1px solid #3a3a5a',
      ready:   'background:#0d2b1a;color:#4ade80;border:1px solid #16a34a',
      failed:  'background:#2b0d0d;color:#f87171;border:1px solid #dc2626'
    };

    const icons = { pending: '⏳', ready: '✅', failed: '⚠️' };

    badge.style.cssText += ';' + styles[state];
    badge.innerText = `${icons[state]} ${text}`;
    badge.style.display = 'block';
  }

  function hidePrefetchBadge() {
    const badge = shadow.getElementById('prefetch-status-badge');
    if (badge) badge.style.display = 'none';
  }

})();
