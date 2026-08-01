import axios from 'axios';

// Same-origin by default; the Vite dev server proxies to the gateway.
const API_URL = import.meta.env.VITE_API_URL ?? '/api';

const TOKEN_KEY = 'jari.token';

// localStorage survives reload but is XSS-readable - see spec.md's Known Gaps.
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

const unauthorizedListeners = new Set<() => void>();

export const onUnauthorized = (listener: () => void) => {
  unauthorizedListeners.add(listener);
  // Braces so this returns void, not Set.delete's boolean - usable as a useEffect cleanup.
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
