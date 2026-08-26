// Application Entry Point & Bootstrapper
import '../css/variables.css';
import '../css/reset.css';
import '../css/typography.css';
import '../css/animations.css';
import '../css/components.css';
import '../css/home.css';
import '../css/scanner.css';
import '../css/garden.css';
import '../css/settings.css';
import '../css/responsive.css';

import { Router } from './router.js';
import { initFloatingLeaves, initTypewriter, initRippleEffect, animateCountUp } from './animation.js';
import { ScannerModule } from './scanner.js';
import { GardenModule } from './garden.js';
import { SettingsModule } from './settings.js';
import { WeatherWidget } from './weather.js';
import { FAQS, CARE_TIPS, TESTIMONIALS } from './data.js';
import { AuthModule } from './auth.js';
import './main.js';

function startApp() {
  // 1. AI Boot / Splash Screen Sequence
  initBootScreen();

  // 2. Initialize Router immediately to render initial view
  Router.init({
    home: () => {
      initHomeView();
    },
    scanner: () => {
      // Scanner already listening
    },
    garden: () => {
      GardenModule.init();
    },
    settings: () => {
      // Settings already bound
    }
  });

  // 3. Initialize modules
  ScannerModule.init();
  SettingsModule.init();
  AuthModule.init();

  // 4. Defer non-critical animations and SW management
  const deferWork = () => {
    initFloatingLeaves();
    initRippleEffect();
    initScrollTopBtn();

    // Auto-clean stale Service Workers on localhost to prevent broken tabs
    if ('serviceWorker' in navigator) {
      if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        navigator.serviceWorker.getRegistrations().then((registrations) => {
          for (const registration of registrations) {
            registration.unregister();
            console.log('[Dev Mode] Unregistered Service Worker:', registration.scope);
          }
        });
        if ('caches' in window) {
          caches.keys().then((keys) => {
            keys.forEach((key) => {
              caches.delete(key);
              console.log('[Dev Mode] Cleared CacheStorage:', key);
            });
          });
        }
      } else {
        // Production PWA Service Worker Registration
        navigator.serviceWorker.register('/sw.js').catch(err => console.log('SW Registration failed:', err));
      }
    }
  };

  if ('requestIdleCallback' in window) {
    window.requestIdleCallback(deferWork);
  } else {
    setTimeout(deferWork, 500);
  }

  // 5. Navigation Controls & Theme Toggle Button in Header
  initHeaderControls();
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', startApp);
} else {
  startApp();
}

// AI Splash Screen Bootsequence
function initBootScreen() {
  const bootScreen = document.getElementById('boot-screen');
  const progressFill = document.getElementById('boot-progress-fill');
  const statusText = document.getElementById('boot-status-text');
  if (!bootScreen) return;

  const dismissBoot = () => {
    bootScreen.classList.add('hidden');
    setTimeout(() => {
      bootScreen.style.display = 'none';
    }, 600);
  };

  if (!progressFill) {
    dismissBoot();
    return;
  }

  const stages = [
    { pct: '25%', text: 'Initializing AI Vision Engine...' },
    { pct: '60%', text: 'Loading Botanical Database & Disease Models...' },
    { pct: '85%', text: 'Preparing Neural Diagnostic Pipeline...' },
    { pct: '100%', text: 'AI Engine Ready' }
  ];

  let stageIdx = 0;
  const timer = setInterval(() => {
    if (stageIdx < stages.length) {
      progressFill.style.width = stages[stageIdx].pct;
      if (statusText) statusText.textContent = stages[stageIdx].text;
      stageIdx++;
    } else {
      clearInterval(timer);
      setTimeout(dismissBoot, 300);
    }
  }, 250);

  // Unconditional fallback failsafe to guarantee dismissal
  setTimeout(() => {
    clearInterval(timer);
    dismissBoot();
  }, 1500);

  window.addEventListener('load', () => {
    setTimeout(dismissBoot, 500);
  });
}

// Header & Mobile Nav Controls
function initHeaderControls() {
  const mobileBtn = document.getElementById('mobile-menu-btn');
  const navLinks = document.getElementById('nav-links');
  if (mobileBtn && navLinks) {
    mobileBtn.addEventListener('click', () => {
      navLinks.classList.toggle('mobile-open');
    });
  }

  const quickThemeToggle = document.getElementById('quick-theme-toggle');
  if (quickThemeToggle) {
    quickThemeToggle.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme');
      const next = current === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      const settings = SettingsModule.settings || {};
      settings.theme = next;
      SettingsModule.applyCurrentSettings();
    });
  }
}

// Home View Specific Renderers & Animations
function initHomeView() {
  // Hero Typewriter
  initTypewriter('hero-typewriter-text', [
    'Discover Plant Health Instantly',
    'Identify 10,000+ Plant Species',
    'AI-Powered Disease Diagnosis'
  ]);

  // Weather Hero Bar
  WeatherWidget.renderWidget('hero-weather-bar');

  // Animated Count-Up Stats
  animateCountUp('stat-plants', 105420, 2000, '+');
  animateCountUp('stat-diseases', 48200, 2000, '+');
  animateCountUp('stat-users', 32000, 2000, '+');
  animateCountUp('stat-accuracy', 98, 1500, '%');

  // Care Tips Carousel
  renderCareTips();

  // Testimonials Grid
  renderTestimonials();

  // FAQs Accordion
  renderFAQs();
}

function renderCareTips() {
  const container = document.getElementById('care-tips-container');
  if (!container) return;

  let currentTip = 0;
  function showTip(idx) {
    const tip = CARE_TIPS[idx];
    container.innerHTML = `
      <div class="tip-card-active">
        <span style="font-size: 3rem; display: block; margin-bottom: 12px;">${tip.icon}</span>
        <h3 style="margin-bottom: 8px;">${tip.title}</h3>
        <p class="subheading">${tip.text}</p>
        <div class="tip-controls">
          <button class="icon-btn" id="tip-prev">‹</button>
          <span style="font-weight: 600; font-size: 0.9rem; align-self: center;">${idx + 1} / ${CARE_TIPS.length}</span>
          <button class="icon-btn" id="tip-next">›</button>
        </div>
      </div>
    `;

    document.getElementById('tip-prev')?.addEventListener('click', () => {
      currentTip = (currentTip - 1 + CARE_TIPS.length) % CARE_TIPS.length;
      showTip(currentTip);
    });

    document.getElementById('tip-next')?.addEventListener('click', () => {
      currentTip = (currentTip + 1) % CARE_TIPS.length;
      showTip(currentTip);
    });
  }

  showTip(currentTip);
}

function renderTestimonials() {
  const grid = document.getElementById('testimonials-grid');
  if (!grid) return;

  grid.innerHTML = TESTIMONIALS.map(t => `
    <div class="card testimonial-card">
      <div style="font-size: 1.25rem; color: #FFC107; margin-bottom: 12px;">★★★★★</div>
      <p style="color: var(--text-secondary); font-style: italic;">"${t.text}"</p>
      <div class="user-author">
        <div class="user-avatar">${t.avatar}</div>
        <div>
          <div style="font-weight: 700; color: var(--text-primary);">${t.name}</div>
          <div class="text-muted" style="font-size: 0.825rem;">${t.role}</div>
        </div>
      </div>
    </div>
  `).join('');
}

function renderFAQs() {
  const container = document.getElementById('faq-accordion');
  if (!container) return;

  container.innerHTML = FAQS.map((faq, idx) => `
    <div class="faq-item">
      <button class="faq-question">
        <span>${faq.q}</span>
        <span class="faq-icon">▼</span>
      </button>
      <div class="faq-answer">
        <p>${faq.a}</p>
      </div>
    </div>
  `).join('');

  container.querySelectorAll('.faq-question').forEach(btn => {
    btn.addEventListener('click', () => {
      const item = btn.closest('.faq-item');
      item.classList.toggle('open');
    });
  });
}

function initScrollTopBtn() {
  const btn = document.getElementById('scroll-top-btn');
  if (!btn) return;

  window.addEventListener('scroll', () => {
    if (window.scrollY > 400) {
      btn.classList.add('visible');
    } else {
      btn.classList.remove('visible');
    }
  });

  btn.addEventListener('click', () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  });
}
