import axios from 'axios';

// The identity service's auth routes sit outside the /api prefix, and the Vite
// dev server proxies /auth to the gateway alongside it.
const AUTH_URL = import.meta.env.VITE_AUTH_URL ?? '/auth';

export const login = async (username: string, password: string): Promise<string> => {
  const response = await axios.post(`${AUTH_URL}/token`, { username, password });
  // POST /auth/token returns the raw JWT as the response body - a bare string,
  // not a JSON object. There is no response.data.token to read.
  return response.data;
};
