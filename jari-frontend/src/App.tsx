import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getKanbanBoard, moveTask } from './api/kanban';
import { listProjects } from './api/projects';
import { listUsers, getCurrentUser } from './api/users';
import { getToken, onUnauthorized } from './api/client';
import { KanbanBoard, type MoveArgs } from './components/KanbanBoard';
import { IssueDetail } from './components/IssueDetail';
import { Login } from './components/Login';
import { Sidebar } from './components/Sidebar';
import { ProjectSettings } from './components/ProjectSettings';
import { CreateProjectDialog } from './components/CreateProjectDialog';
import { CreateIssueDialog } from './components/CreateIssueDialog';
import { FilterBar } from './components/FilterBar';
import type { Project } from './types/project';
import type { KanbanBoard as KanbanBoardType, Task } from './types/kanban';
import { EMPTY_FILTERS, type Filters } from './types/filters';

// Applied client-side over the already-fetched board: the board arrives as
// one payload of every issue in the project, so filtering it server-side
// would mean more requests for less responsiveness, and there is no endpoint
// for it anyway. Stops being the right call once a project has enough issues
// that fetching them all is itself the problem - not a concern at demo scale.
function applyFilters(board: KanbanBoardType, filters: Filters, currentUserId?: number): KanbanBoardType {
  const text = filters.text.trim().toLowerCase();
  return {
    columns: board.columns.map((c) => ({
      ...c,
      tasks: c.tasks.filter((t) => {
        if (text && !t.summary.toLowerCase().includes(text)) return false;
        if (filters.assigneeId !== null && t.assigneeId !== filters.assigneeId) return false;
        if (filters.type !== null && t.type !== filters.type) return false;
        if (filters.onlyMine && t.assigneeId !== currentUserId) return false;
        return true;
      }),
    })),
  };
}

function Board({ projectId }: { projectId: number }) {
  const queryKey = ['kanban', projectId];
  const queryClient = useQueryClient();

  const { data, isLoading, isError, error } = useQuery({
    queryKey,
    queryFn: () => getKanbanBoard(projectId),
    // A 401 is handled globally by signing the user out; retrying it would
    // just burn requests before that happens.
    retry: false,
  });

  // The assignee list is every user in the system - project membership does
  // not exist until Phase 3, so there is no smaller set to draw from yet.
  const { data: users } = useQuery({ queryKey: ['users'], queryFn: listUsers, retry: false });
  const usersById = useMemo(() => new Map(users?.map((u) => [u.id, u])), [users]);
  const { data: currentUser } = useQuery({ queryKey: ['me'], queryFn: getCurrentUser, retry: false });

  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [moveError, setMoveError] = useState(false);
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);

  // Optimistic: the card moves in the cache the instant the drop happens,
  // rather than waiting for the round trip - a drag that visibly hangs before
  // settling reads as broken. onMutate snapshots the board so onError can put
  // it back exactly as it was; onSettled always refetches afterward, so the
  // client never has the last word over the server.
  const move = useMutation({
    mutationFn: (args: MoveArgs) => moveTask(args.taskId, args.targetStatus, args.targetIndex),
    onMutate: async (args: MoveArgs) => {
      setMoveError(false);
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<KanbanBoardType>(queryKey);

      queryClient.setQueryData<KanbanBoardType>(queryKey, (board) => {
        if (!board) return board;
        let moved: Task | undefined;
        const withoutTask = board.columns.map((c) => ({
          ...c,
          tasks: c.tasks.filter((t) => {
            if (t.id === args.taskId) moved = t;
            return t.id !== args.taskId;
          }),
        }));
        if (!moved) return board;
        const movedTask: Task = { ...moved, status: args.targetStatus };
        return {
          columns: withoutTask.map((c) =>
            c.id === args.targetStatus
              ? { ...c, tasks: [...c.tasks.slice(0, args.targetIndex), movedTask, ...c.tasks.slice(args.targetIndex)] }
              : c,
          ),
        };
      });

      return { previous };
    },
    onError: (_err, _args, context) => {
      if (context?.previous) queryClient.setQueryData(queryKey, context.previous);
      setMoveError(true);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey }),
  });

  if (isError) {
    return (
      <div className="p-6 text-sm text-red-600">
        Could not load the board: {error instanceof Error ? error.message : 'unknown error'}
      </div>
    );
  }

  const filteredBoard = data ? applyFilters(data, filters, currentUser?.id) : data;

  return (
    <>
      <FilterBar filters={filters} onChange={setFilters} users={users} currentUserId={currentUser?.id} />
      {moveError && (
        <div className="mx-4 mt-2 p-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded">
          Could not move the issue. It has been put back.
        </div>
      )}
      <KanbanBoard
        board={filteredBoard ?? null}
        isLoading={isLoading}
        usersById={usersById}
        onSelectTask={setSelectedTask}
        onMove={(args) => move.mutate(args)}
      />
      {selectedTask && <IssueDetail task={selectedTask} onClose={() => setSelectedTask(null)} />}
    </>
  );
}

function ProjectShell() {
  const { data: projects, isLoading, refetch } = useQuery({
    queryKey: ['projects'],
    queryFn: listProjects,
  });
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);

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
        <header className="px-4 py-3 bg-white border-b border-gray-200 flex items-center justify-between">
          <h1 className="text-lg font-bold text-blue-600">Jari</h1>
          <button
            onClick={() => setCreateOpen(true)}
            className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            Create issue
          </button>
        </header>
        <main className="flex-1 min-h-0">
          <Board projectId={project.id} />
        </main>
      </div>
      {settingsOpen && <ProjectSettings project={project} onClose={() => setSettingsOpen(false)} />}
      {createOpen && <CreateIssueDialog project={project} onClose={() => setCreateOpen(false)} />}
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
