import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getKanbanBoard } from './api/kanban';
import { listProjects } from './api/projects';
import { listUsers } from './api/users';
import { getToken, onUnauthorized } from './api/client';
import { KanbanBoard } from './components/KanbanBoard';
import { Login } from './components/Login';
import { Sidebar } from './components/Sidebar';
import { ProjectSettings } from './components/ProjectSettings';
import { CreateProjectDialog } from './components/CreateProjectDialog';
import type { Project } from './types/project';

function Board({ projectId }: { projectId: number }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['kanban', projectId],
    queryFn: () => getKanbanBoard(projectId),
    // A 401 is handled globally by signing the user out; retrying it would
    // just burn requests before that happens.
    retry: false,
  });

  // The assignee list is every user in the system - project membership does
  // not exist until Phase 3, so there is no smaller set to draw from yet.
  const { data: users } = useQuery({ queryKey: ['users'], queryFn: listUsers, retry: false });
  const usersById = useMemo(() => new Map(users?.map((u) => [u.id, u])), [users]);

  if (isError) {
    return (
      <div className="p-6 text-sm text-red-600">
        Could not load the board: {error instanceof Error ? error.message : 'unknown error'}
      </div>
    );
  }

  return <KanbanBoard board={data ?? null} isLoading={isLoading} usersById={usersById} />;
}

function ProjectShell() {
  const { data: projects, isLoading, refetch } = useQuery({
    queryKey: ['projects'],
    queryFn: listProjects,
  });
  const [settingsOpen, setSettingsOpen] = useState(false);

  if (isLoading) {
    return <div className="p-6 text-sm text-gray-500">Loading...</div>;
  }

  // There are zero rows in the projects table on a fresh clone - the board
  // only ever "worked" because KanbanService never checks the project
  // exists. This is the first-run path instead of a hardcoded project id.
  const project: Project | undefined = projects?.[0];
  if (!project) {
    return <CreateProjectDialog onCreated={() => refetch()} />;
  }

  return (
    <div className="min-h-screen bg-gray-50 flex">
      <Sidebar project={project} onOpenSettings={() => setSettingsOpen(true)} />
      <div className="flex-1 flex flex-col min-w-0">
        <header className="px-4 py-3 bg-white border-b border-gray-200">
          <h1 className="text-lg font-bold text-blue-600">Jari</h1>
        </header>
        <main className="flex-1 min-h-0">
          <Board projectId={project.id} />
        </main>
      </div>
      {settingsOpen && <ProjectSettings project={project} onClose={() => setSettingsOpen(false)} />}
    </div>
  );
}

function App() {
  const [authenticated, setAuthenticated] = useState(() => getToken() !== null);

  // The response interceptor clears the token on a 401; this puts the UI back
  // on the login screen rather than leaving a blank or half-rendered page.
  useEffect(() => onUnauthorized(() => setAuthenticated(false)), []);

  if (!authenticated) {
    return <Login onAuthenticated={() => setAuthenticated(true)} />;
  }

  return <ProjectShell />;
}

export default App;
