// SPA Client-Side Router for exactly 4 views: Home, Scanner, Garden, Settings

export const Router = {
  activeRoute: 'home',

  init(routesCallbackMap) {
    this.routesCallbackMap = routesCallbackMap || {};

    // Navigation links listener
    document.addEventListener('click', (e) => {
      const link = e.target.closest('[data-route]');
      if (link) {
        e.preventDefault();
        const route = link.getAttribute('data-route');
        this.navigateTo(route);
      }
    });

    // Handle initial hash or default to home
    const initialHash = window.location.hash.replace('#', '') || 'home';
    this.navigateTo(['home', 'scanner', 'garden', 'settings', 'login', 'signup'].includes(initialHash) ? initialHash : 'home');

    window.addEventListener('popstate', () => {
      const hash = window.location.hash.replace('#', '') || 'home';
      this.navigateTo(hash, false);
    });

    window.addEventListener('hashchange', () => {
      const hash = window.location.hash.replace('#', '') || 'home';
      this.navigateTo(hash, false);
    });
  },

  navigateTo(route, updateHistory = true) {
    const validRoutes = ['home', 'scanner', 'garden', 'settings', 'login', 'signup'];
    if (!validRoutes.includes(route)) route = 'home';

    this.activeRoute = route;
    if (updateHistory && window.location.hash !== `#${route}`) {
      window.location.hash = route;
    }

    const targetViewId = (route === 'login' || route === 'signup') ? 'login-view' : `${route}-view`;
    const targetView = document.getElementById(targetViewId);
    if (!targetView) {
      console.error("View not found:", targetViewId);
      return;
    }

    // Toggle active view sections
    document.querySelectorAll('.page-view').forEach(view => {
      view.classList.remove('active');
    });

    targetView.classList.add('active');
    window.scrollTo({ top: 0, behavior: 'smooth' });

    // Toggle tabs on login page
    if (route === 'signup') {
      document.getElementById('login-page-tab-signup')?.click();
    } else if (route === 'login') {
      document.getElementById('login-page-tab-signin')?.click();
    }

    // Update active state in nav links
    document.querySelectorAll('[data-route]').forEach(link => {
      if (link.getAttribute('data-route') === route) {
        link.classList.add('active');
      } else {
        link.classList.remove('active');
      }
    });

    // Close mobile nav drawer if open
    const navLinks = document.getElementById('nav-links');
    if (navLinks) navLinks.classList.remove('mobile-open');

    // Trigger route handler callback
    if (this.routesCallbackMap[route]) {
      this.routesCallbackMap[route]();
    }
  }
};
