import axios from 'axios';

const AUTH_URL = import.meta.env.VITE_AUTH_URL ?? '/auth';

export const login = async (username: string, password: string): Promise<string> => {
  const response = await axios.post(`${AUTH_URL}/token`, { username, password });
  // Returns the raw JWT string, not a ResponseDto - there is no .data.token.
  return response.data;
};
