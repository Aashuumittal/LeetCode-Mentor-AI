document.addEventListener('DOMContentLoaded', async () => {
  const statusCard = document.getElementById('status-card');

  try {
    const user = await StorageUtil.getUser();
    const tokens = await StorageUtil.getTokens();

    if (tokens.accessToken && user) {
      statusCard.innerHTML = `
        <p style="font-weight: 500; color: #f8fafc; margin-bottom: 8px;">Hi, ${user.name} 👋</p>
        <div class="streak-val">${user.currentStreak || 0} 🔥</div>
        <p style="font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px; color: #64748b; margin-bottom: 8px;">Daily Streak</p>
        <p style="font-size: 11px;">Solved: <strong>${user.totalSolved || 0}</strong> | Hints: <strong>${user.totalHintsUsed || 0}</strong></p>
      `;
    } else {
      statusCard.innerHTML = `
        <p style="color: #fb7185;">Not Signed In</p>
        <p style="font-size: 11px; margin-top: 6px;">Please open any LeetCode problem page and log in via the floating sidebar.</p>
      `;
    }
  } catch (e) {
    console.error('Error reading storage inside popup:', e);
    statusCard.innerHTML = `<p style="color: #f87171;">Storage load error.</p>`;
  }
});
