const ScraperUtil = {
  getProblemSlug: () => {
    const path = window.location.pathname;
    const match = path.match(/\/problems\/([^/]+)/);
    return match ? match[1] : null;
  },

  getProblemTitle: () => {
    // 1. Try question-title selector (data attribute)
    let titleEl = document.querySelector('[data-cy="question-title"]');
    if (!titleEl) {
      // 2. Try class name used in new layouts
      titleEl = document.querySelector('div.text-title-large');
    }
    if (!titleEl) {
      // 3. Fallback to selector in other variants
      titleEl = document.querySelector('.css-v3d350');
    }

    if (titleEl) {
      let rawTitle = titleEl.innerText || titleEl.textContent;
      // Strip leading number if present (e.g. "1. Two Sum" -> "Two Sum")
      return rawTitle.replace(/^\d+\.\s*/, '').trim();
    }

    // Ultimate fallback: return slug converted to title case
    const slug = ScraperUtil.getProblemSlug();
    if (slug) {
      return slug.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
    }
    return 'LeetCode Problem';
  },

  getProblemDescription: () => {
    // Standard LeetCode description element
    let descEl = document.querySelector('[data-track-load="description_content"]');
    if (!descEl) {
      descEl = document.querySelector('.question-content__JfgR');
    }
    if (!descEl) {
      descEl = document.querySelector('.content__u3e1');
    }

    if (descEl) {
      return descEl.innerHTML || descEl.innerText;
    }
    return 'Description not found on page.';
  },

  getProblemDifficulty: () => {
    // 1. Check data-difficulty attribute or text-difficulty- classes
    const easyEl = document.querySelector('.text-difficulty-easy, .text-olive');
    const mediumEl = document.querySelector('.text-difficulty-medium, .text-yellow, .text-orange');
    const hardEl = document.querySelector('.text-difficulty-hard, .text-pink, .text-red');

    if (easyEl || document.body.innerText.includes('Easy')) return 'EASY';
    if (mediumEl || document.body.innerText.includes('Medium')) return 'MEDIUM';
    if (hardEl || document.body.innerText.includes('Hard')) return 'HARD';

    return 'MEDIUM'; // Default fallback
  },

  scrapeAll: () => {
    return {
      problemSlug: ScraperUtil.getProblemSlug(),
      problemTitle: ScraperUtil.getProblemTitle(),
      problemDescription: ScraperUtil.getProblemDescription(),
      difficulty: ScraperUtil.getProblemDifficulty()
    };
  }
};
