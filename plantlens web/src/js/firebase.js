import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";
import { getAuth, GoogleAuthProvider } from "firebase/auth";
import { getStorage } from "firebase/storage";

const firebaseConfig = {
  apiKey: "AIzaSyC8O8nu_PdWT3Icivsm6fqeCS4BpovEkT0",
  authDomain: "plantlens-ai.firebaseapp.com",
  projectId: "plantlens-ai",
  storageBucket: "plantlens-ai.firebasestorage.app",
  messagingSenderId: "7465551549",
  appId: "1:7465551549:web:fd3bc52aa35290fdb519d4",
  measurementId: "G-K6NXQK921Y"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const db = getFirestore(app);
const auth = getAuth(app);
auth.useDeviceLanguage();
const storage = getStorage(app);
const googleProvider = new GoogleAuthProvider();

// Direct exports
export { app, db, auth, storage, googleProvider };

// Compatibility helper getters
export async function getFirebaseApp() {
  return app;
}

export async function getFirebaseAuth() {
  return auth;
}

export async function getFirebaseDb() {
  return db;
}

export async function getFirebaseStorage() {
  return storage;
}
