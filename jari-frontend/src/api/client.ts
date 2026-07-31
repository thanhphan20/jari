import axios from 'axios';

// Same-origin by default; the Vite dev server proxies to the gateway.
const API_URL = import.meta.env.VITE_API_URL ?? '/api';

const TOKEN_KEY = 'jari.token';

// localStorage rather than in-memory state so a page reload does not log you
// out - which during a demo reads as a bug rather than a design choice.
//
// The trade-off, stated rather than hidden: localStorage is readable by any
// script on this origin, so this is XSS-exposed in a way an HttpOnly cookie is
// not. Acceptable for a local-only demo. The real fix is a cookie set by the
// gateway, which makes the gateway a session participant instead of a
// stateless token validator - architecture, not a tweak. See the README's
// Known Limitations.
export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (token: string) => localStorage.setItem(TOKEN_KEY, token);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

export const api = axios.create({ baseURL: API_URL });

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Listeners are notified when the stored token stops being accepted, so the UI
// can fall back to the login screen instead of rendering a blank page.
const unauthorizedListeners = new Set<() => void>();

export const onUnauthorized = (listener: () => void) => {
  unauthorizedListeners.add(listener);
  // Returns void, not Set.delete's boolean, so it is usable directly as a
  // useEffect cleanup.
  return () => {
    unauthorizedListeners.delete(listener);
  };
};

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearToken();
      unauthorizedListeners.forEach((listener) => listener());
    }
    return Promise.reject(error);
  },
);
