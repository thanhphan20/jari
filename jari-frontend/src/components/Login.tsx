import { useState, type FormEvent } from 'react';
import { login } from '../api/auth';
import { setToken } from '../api/client';

interface Props {
  onAuthenticated: () => void;
}

export const Login: React.FC<Props> = ({ onAuthenticated }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const token = await login(username, password);
      // Only persisted on success, so a rejected attempt leaves nothing behind.
      setToken(token);
      onAuthenticated();
    } catch {
      // Deliberately not distinguishing "no such user" from "wrong password".
      setError('Login failed. Check your username and password.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <form onSubmit={handleSubmit} className="w-80 bg-white p-6 rounded-lg shadow-sm border border-gray-200">
        <h1 className="text-xl font-bold text-blue-600 mb-4">Jari</h1>

        <label className="block text-sm text-gray-700 mb-1" htmlFor="username">Username</label>
        <input
          id="username"
          className="w-full mb-3 px-3 py-2 border border-gray-300 rounded text-sm"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />

        <label className="block text-sm text-gray-700 mb-1" htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          className="w-full mb-4 px-3 py-2 border border-gray-300 rounded text-sm"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />

        {error && <div className="mb-3 text-sm text-red-600">{error}</div>}

        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-blue-600 text-white py-2 rounded text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          {submitting ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
    </div>
  );
};
