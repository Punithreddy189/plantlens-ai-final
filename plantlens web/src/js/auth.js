// Firebase Authentication Controller Module
import { auth, db, googleProvider } from './firebase.js';
import {
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  updateProfile,
  onAuthStateChanged
} from "firebase/auth";
import { doc, setDoc, getDoc, onSnapshot } from "firebase/firestore";
import { showToast } from './utils.js';
import { StorageManager } from './storage.js';

let userProfileUnsubscribe = null;

// Global convenience methods for quick testing & inline handlers
window.signup = async function () {
  const emailElem = document.getElementById("email") || document.getElementById("auth-email-input");
  const passwordElem = document.getElementById("password") || document.getElementById("auth-password-input");
  const nameElem = document.getElementById("auth-name-input");

  if (!emailElem || !passwordElem) {
    alert("Email and Password fields are required!");
    return;
  }

  const email = emailElem.value.trim();
  const password = passwordElem.value;
  const name = nameElem ? nameElem.value.trim() : "Botanist Member";

  try {
    const userCredential = await createUserWithEmailAndPassword(auth, email, password);
    const user = userCredential.user;

    if (name) {
      await updateProfile(user, { displayName: name });
    }

    // Create Root User Document in Firestore with stats
    await setDoc(doc(db, "users", user.uid), {
      name: name || "Botanist Member",
      email: email,
      totalScans: 0,
      createdAt: new Date().toISOString(),
      role: "Botanist Member"
    }, { merge: true });

    alert("Signup successful ✅");
    showToast("Account created successfully!", "success");
    const authModal = document.getElementById("auth-modal");
    if (authModal) authModal.classList.remove("active");
  } catch (error) {
    console.error("Signup Error:", error);
    alert("Signup Failed ❌: " + (error.message || error.code));
  }
};

window.login = async function () {
  const emailElem = document.getElementById("email") || document.getElementById("auth-email-input");
  const passwordElem = document.getElementById("password") || document.getElementById("auth-password-input");

  if (!emailElem || !passwordElem) {
    alert("Email and Password fields are required!");
    return;
  }

  const email = emailElem.value.trim();
  const password = passwordElem.value;

  try {
    const userCredential = await signInWithEmailAndPassword(auth, email, password);
    const user = userCredential.user;

    // Ensure user profile document exists
    const userDocRef = doc(db, "users", user.uid);
    const userDoc = await getDoc(userDocRef);
    if (!userDoc.exists()) {
      await setDoc(userDocRef, {
        name: user.displayName || "Botanist Member",
        email: user.email,
        totalScans: 0,
        createdAt: new Date().toISOString(),
        role: "Botanist Member"
      }, { merge: true });
    }

    alert("Login successful ✅");
    showToast("Welcome back to PlantLens AI!", "success");
    const authModal = document.getElementById("auth-modal");
    if (authModal) authModal.classList.remove("active");
  } catch (error) {
    console.error("Login Error:", error);
    alert("Login Failed ❌: " + (error.message || error.code));
  }
};

window.logout = async function () {
  try {
    await signOut(auth);
    localStorage.clear();
    sessionStorage.clear();
    showToast("Logged out successfully.", "info");
    window.location.hash = "#home";
  } catch (error) {
    console.error("Logout Error:", error);
    showToast("Logout failed: " + (error.message || error.code), "danger");
  }
};

// Auth Module Object
export const AuthModule = {
  currentUser: null,
  activeMode: 'login', // 'login' or 'signup'

  async init() {
    this.bindEvents();
    this.listenToAuthState();
  },

  bindEvents() {
    const authModal = document.getElementById('auth-modal');
    const userBtn = document.getElementById('nav-user-btn');
    const form = document.getElementById('auth-form');
    
    const tabLogin = document.getElementById('auth-tab-login');
    const tabSignup = document.getElementById('auth-tab-signup');
    const nameGroup = document.getElementById('auth-name-group');
    const btnSubmit = document.getElementById('btn-auth-submit');
    const errorMsg = document.getElementById('auth-error-msg');

    // Open Login Page / Modal when clicking Auth / User Button
    const btnSettingsLogin = document.getElementById('btn-settings-login');
    if (btnSettingsLogin) {
      btnSettingsLogin.addEventListener('click', () => {
        window.location.hash = '#login';
      });
    }

    if (userBtn) {
      userBtn.addEventListener('click', () => {
        if (this.currentUser) {
          window.location.hash = '#settings';
        } else {
          window.location.hash = '#login';
        }
      });
    }

    // Close Modals
    authModal?.querySelectorAll('.btn-close-modal').forEach(btn => {
      btn.addEventListener('click', () => {
        authModal.classList.remove('active');
        if (errorMsg) errorMsg.style.display = 'none';
      });
    });

    // Tab switching
    tabLogin?.addEventListener('click', () => this.switchTab('login'));
    tabSignup?.addEventListener('click', () => this.switchTab('signup'));

    // Google Sign-In in Modal
    const btnGoogle = document.getElementById('btn-google-auth');
    if (btnGoogle) {
      btnGoogle.addEventListener('click', () => this.handleGoogleSignIn(authModal, errorMsg));
    }

    // Wire Login Page
    this.bindLoginPage();

    // Handle Form Submit
    if (form) {
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (errorMsg) errorMsg.style.display = 'none';

        const email = document.getElementById('auth-email-input').value.trim();
        const password = document.getElementById('auth-password-input').value;
        const name = document.getElementById('auth-name-input').value.trim();

        btnSubmit.disabled = true;
        btnSubmit.textContent = this.activeMode === 'login' ? 'Signing In...' : 'Registering...';

        try {
          if (this.activeMode === 'login') {
            const userCredential = await signInWithEmailAndPassword(auth, email, password);
            const user = userCredential.user;
            
            // Ensure root document exists
            const userDocRef = doc(db, "users", user.uid);
            const userDoc = await getDoc(userDocRef);
            if (!userDoc.exists()) {
              await setDoc(userDocRef, {
                name: user.displayName || name || "Botanist Member",
                email: user.email,
                totalScans: 0,
                createdAt: new Date().toISOString()
              }, { merge: true });
            }
            showToast('Welcome back to PlantLens AI!', 'success');
          } else {
            const userCredential = await createUserWithEmailAndPassword(auth, email, password);
            const user = userCredential.user;

            if (name) {
              await updateProfile(user, { displayName: name });
            }

            // Save user document in Firestore with totalScans: 0
            await setDoc(doc(db, "users", user.uid), {
              name: name || "Botanist Member",
              email: email,
              totalScans: 0,
              createdAt: new Date().toISOString(),
              role: "Botanist Member"
            }, { merge: true });

            showToast('Account registered successfully!', 'success');
          }
          authModal.classList.remove('active');
          form.reset();
          window.location.hash = '#home';
        } catch (err) {
          console.error("Auth Error:", err);
          if (errorMsg) {
            errorMsg.textContent = `${this.formatError(err.code || err.message)}`;
            errorMsg.style.display = 'block';
          }
        } finally {
          btnSubmit.disabled = false;
          btnSubmit.textContent = this.activeMode === 'login' ? 'Sign In' : 'Create Account';
        }
      });
    }

    // Wire Settings Logout Buttons
    document.getElementById('btn-logout')?.addEventListener('click', () => this.handleSignOut());
    document.getElementById('btn-profile-card-logout')?.addEventListener('click', () => this.handleSignOut());
  },

  async handleSignOut() {
    if (confirm('Are you sure you want to log out of PlantLens AI?')) {
      try {
        await signOut(auth);
        localStorage.removeItem('plantlens_garden_db');
        sessionStorage.clear();
        showToast('Logged out successfully.', 'info');
        window.location.hash = '#home';
      } catch (err) {
        console.error("Logout Error:", err);
        showToast('Failed to logout: ' + (err.message || err.code), 'danger');
      }
    }
  },

  bindLoginPage() {
    const tabSignin = document.getElementById('login-page-tab-signin');
    const tabSignup = document.getElementById('login-page-tab-signup');
    const nameGroup = document.getElementById('login-page-name-group');
    const btnSubmit = document.getElementById('btn-login-page-submit');
    const errorMsg = document.getElementById('login-page-error-msg');
    const form = document.getElementById('login-page-form');
    const btnGoogle = document.getElementById('btn-login-page-google');

    let mode = 'login';

    const setMode = (m) => {
      mode = m;
      if (errorMsg) errorMsg.style.display = 'none';
      if (m === 'login') {
        if (tabSignin) {
          tabSignin.style.color = 'var(--primary-color)';
          tabSignin.style.borderBottom = '3px solid var(--primary-color)';
          tabSignin.style.fontWeight = '700';
        }
        if (tabSignup) {
          tabSignup.style.color = 'var(--text-secondary)';
          tabSignup.style.borderBottom = 'none';
          tabSignup.style.fontWeight = '500';
        }
        if (nameGroup) nameGroup.style.display = 'none';
        if (btnSubmit) btnSubmit.textContent = 'Sign In';
      } else {
        if (tabSignup) {
          tabSignup.style.color = 'var(--primary-color)';
          tabSignup.style.borderBottom = '3px solid var(--primary-color)';
          tabSignup.style.fontWeight = '700';
        }
        if (tabSignin) {
          tabSignin.style.color = 'var(--text-secondary)';
          tabSignin.style.borderBottom = 'none';
          tabSignin.style.fontWeight = '500';
        }
        if (nameGroup) nameGroup.style.display = 'flex';
        if (btnSubmit) btnSubmit.textContent = 'Create Account';
      }
    };

    tabSignin?.addEventListener('click', () => setMode('login'));
    tabSignup?.addEventListener('click', () => setMode('signup'));

    if (btnGoogle) {
      btnGoogle.addEventListener('click', () => this.handleGoogleSignIn(null, errorMsg));
    }

    if (form) {
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (errorMsg) errorMsg.style.display = 'none';

        const email = document.getElementById('login-page-email-input').value.trim();
        const password = document.getElementById('login-page-password-input').value;
        const name = document.getElementById('login-page-name-input')?.value.trim();

        if (btnSubmit) {
          btnSubmit.disabled = true;
          btnSubmit.textContent = mode === 'login' ? 'Signing In...' : 'Registering...';
        }

        try {
          if (mode === 'login') {
            await signInWithEmailAndPassword(auth, email, password);
            showToast('Welcome back to PlantLens AI!', 'success');
          } else {
            const userCredential = await createUserWithEmailAndPassword(auth, email, password);
            const user = userCredential.user;
            if (name) await updateProfile(user, { displayName: name });
            await setDoc(doc(db, "users", user.uid), {
              name: name || "Botanist Member",
              email: email,
              totalScans: 0,
              createdAt: new Date().toISOString(),
              role: "Botanist Member"
            }, { merge: true });
            showToast('Account registered successfully!', 'success');
          }
          window.location.hash = '#home';
        } catch (err) {
          console.error("Page Auth Error:", err);
          if (errorMsg) {
            errorMsg.textContent = `${this.formatError(err.code || err.message)}`;
            errorMsg.style.display = 'block';
          }
        } finally {
          if (btnSubmit) {
            btnSubmit.disabled = false;
            btnSubmit.textContent = mode === 'login' ? 'Sign In' : 'Create Account';
          }
        }
      });
    }
  },

  async handleGoogleSignIn(modalToClose, errorDisplay) {
    try {
      const result = await signInWithPopup(auth, googleProvider);
      const user = result.user;
      const userDocRef = doc(db, "users", user.uid);
      const userDoc = await getDoc(userDocRef);
      if (!userDoc.exists()) {
        await setDoc(userDocRef, {
          name: user.displayName || "Botanist Member",
          email: user.email,
          totalScans: 0,
          createdAt: new Date().toISOString(),
          role: "Botanist Member"
        }, { merge: true });
      }
      showToast(`Welcome ${user.displayName || user.email}!`, 'success');
      if (modalToClose) modalToClose.classList.remove('active');
      window.location.hash = '#home';
    } catch (err) {
      console.error("Google Auth Error:", err);
      if (errorDisplay) {
        errorDisplay.textContent = `${this.formatError(err.code || err.message)}`;
        errorDisplay.style.display = 'block';
      }
    }
  },

  switchTab(mode) {
    this.activeMode = mode;
    const tabLogin = document.getElementById('auth-tab-login');
    const tabSignup = document.getElementById('auth-tab-signup');
    const nameGroup = document.getElementById('auth-name-group');
    const btnSubmit = document.getElementById('btn-auth-submit');
    const errorMsg = document.getElementById('auth-error-msg');

    if (errorMsg) errorMsg.style.display = 'none';

    if (mode === 'login') {
      tabLogin.style.color = 'var(--primary-color)';
      tabLogin.style.borderBottom = '3px solid var(--primary-color)';
      tabSignup.style.color = 'var(--text-secondary)';
      tabSignup.style.borderBottom = 'none';
      nameGroup.style.display = 'none';
      btnSubmit.textContent = 'Sign In';
    } else {
      tabSignup.style.color = 'var(--primary-color)';
      tabSignup.style.borderBottom = '3px solid var(--primary-color)';
      tabLogin.style.color = 'var(--text-secondary)';
      tabLogin.style.borderBottom = 'none';
      nameGroup.style.display = 'flex';
      btnSubmit.textContent = 'Create Account';
    }
  },

  listenToAuthState() {
    onAuthStateChanged(auth, (user) => {
      this.currentUser = user;
      const userBtn = document.getElementById('nav-user-btn');
      const loggedInView = document.getElementById('profile-logged-in-view');
      const loggedOutView = document.getElementById('profile-logged-out-view');
      const profileName = document.getElementById('profile-user-name');
      const profileEmail = document.getElementById('profile-user-email');
      const profileAvatar = document.getElementById('profile-user-avatar');
      const profileScans = document.getElementById('profile-user-scans');
      const profileUid = document.getElementById('profile-user-uid');
      const btnLogout = document.getElementById('btn-logout');

      if (userProfileUnsubscribe) {
        userProfileUnsubscribe();
        userProfileUnsubscribe = null;
      }

      if (user) {
        console.log("Logged in User UID:", user.uid, "| Email:", user.email);
        
        // Show Logged-in profile card, hide Logged-out card
        if (loggedInView) loggedInView.style.display = 'flex';
        if (loggedOutView) loggedOutView.style.display = 'none';
        if (btnLogout) btnLogout.style.display = 'inline-flex';

        const initials = user.displayName ? 
          user.displayName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : 
          (user.email ? user.email[0].toUpperCase() : 'U');

        if (userBtn) {
          userBtn.textContent = initials;
          userBtn.title = `Signed in as ${user.displayName || user.email}`;
        }

        if (profileName) profileName.textContent = user.displayName || 'Botanist Member';
        if (profileEmail) profileEmail.textContent = user.email;
        if (profileAvatar) profileAvatar.textContent = initials;
        if (profileUid) {
          profileUid.textContent = `UID: ${user.uid.substring(0, 8)}...`;
          profileUid.title = `Click to copy full UID: ${user.uid}`;
          profileUid.onclick = () => {
            navigator.clipboard.writeText(user.uid);
            showToast("Firebase UID copied to clipboard!", "info");
          };
        }

        // Real-time Firestore user metadata listener for totalScans & profile info
        userProfileUnsubscribe = onSnapshot(doc(db, "users", user.uid), (docSnap) => {
          if (docSnap.exists()) {
            const data = docSnap.data();
            if (profileName && data.name) profileName.textContent = data.name;
            if (profileScans) {
              profileScans.innerHTML = `📊 Total Scans: <b>${data.totalScans || 0}</b>`;
            }
          }
        }, (err) => console.warn("User profile sync error:", err));

        StorageManager.syncFromFirestore();
      } else {
        console.log("No user logged in (Guest Mode)");
        
        // Show Logged-out card, hide Logged-in profile card
        if (loggedInView) loggedInView.style.display = 'none';
        if (loggedOutView) loggedOutView.style.display = 'flex';
        if (btnLogout) btnLogout.style.display = 'none';

        if (userBtn) {
          userBtn.textContent = '👤';
          userBtn.title = 'Sign In / Register';
        }

        if (profileName) profileName.textContent = 'Guest Botanist';
        if (profileEmail) profileEmail.textContent = 'Sign in to sync your garden cloud database';
        if (profileAvatar) profileAvatar.textContent = 'G';
        if (profileScans) profileScans.textContent = '📊 Total Scans: 0';
        if (profileUid) profileUid.textContent = '';
        
        // Clear storage & garden cache on logout
        StorageManager.clearGarden();
        window.dispatchEvent(new CustomEvent('garden-sync-completed'));

        // Clear real-time cloud garden list
        const plantList = document.getElementById('plant-list');
        if (plantList) {
          plantList.innerHTML = `
            <div style="grid-column: 1 / -1; padding: 20px; text-align: center; color: var(--text-secondary);">
              <p>🔒 Please <a href="#login" data-route="login" style="color: var(--primary-color); font-weight: 600; text-decoration: underline;">Sign In</a> to view and sync your cloud garden.</p>
            </div>
          `;
        }
      }
    });
  },

  formatError(code) {
    switch (code) {
      case 'auth/invalid-email':
        return 'Invalid email address format.';
      case 'auth/user-not-found':
      case 'auth/wrong-password':
      case 'auth/invalid-credential':
        return 'Incorrect email or password.';
      case 'auth/email-already-in-use':
        return 'This email address is already registered.';
      case 'auth/weak-password':
        return 'Password must be at least 6 characters.';
      default:
        return 'Authentication failed. Please try again.';
    }
  }
};