import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getKanbanBoard } from './api/kanban';
import { getToken, onUnauthorized } from './api/client';
import { KanbanBoard } from './components/KanbanBoard';
import { Login } from './components/Login';

// Hardcoded deliberately. A project picker needs a project list endpoint, a
// second screen, and selection state, none of which prove anything this does
// not. Kept as a named constant so the place to change is obvious.
const PROJECT_ID = 1;

function Board() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['kanban', PROJECT_ID],
    queryFn: () => getKanbanBoard(PROJECT_ID),
    // A 401 is handled globally by signing the user out; retrying it would
    // just burn requests before that happens.
    retry: false,
  });

  if (isError) {
    return (
      <div className="p-6 text-sm text-red-600">
        Could not load the board: {error instanceof Error ? error.message : 'unknown error'}
      </div>
    );
  }

  return <KanbanBoard board={data ?? null} isLoading={isLoading} />;
}

function App() {
  const [authenticated, setAuthenticated] = useState(() => getToken() !== null);

  // The response interceptor clears the token on a 401; this puts the UI back
  // on the login screen rather than leaving a blank or half-rendered page.
  useEffect(() => onUnauthorized(() => setAuthenticated(false)), []);

  if (!authenticated) {
    return <Login onAuthenticated={() => setAuthenticated(true)} />;
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <header className="px-4 py-3 bg-white border-b border-gray-200">
        <h1 className="text-lg font-bold text-blue-600">Jari</h1>
      </header>
      <main className="flex-1 min-h-0">
        <Board />
      </main>
    </div>
  );
}

export default App;
