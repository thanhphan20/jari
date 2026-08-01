import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getKanbanBoard, moveTask } from './api/kanban';
import { listProjects } from './api/projects';
import { listUsers, getCurrentUser } from './api/users';
import { getToken, onUnauthorized } from './api/client';
import { KanbanBoard, BoardSkeleton, type MoveArgs } from './components/KanbanBoard';
import { IssueDetail } from './components/IssueDetail';
import { Login } from './components/Login';
import { Sidebar, SidebarToggle } from './components/Sidebar';
import { NavbarLeft } from './components/NavbarLeft';
import { Breadcrumb } from './components/Breadcrumb';
import { ProjectSettings } from './components/ProjectSettings';
import { CreateProjectDialog } from './components/CreateProjectDialog';
import { CreateIssueDialog } from './components/CreateIssueDialog';
import { FilterBar } from './components/FilterBar';
import type { Project } from './types/project';
import type { KanbanBoard as KanbanBoardType, Task } from './types/kanban';
import { EMPTY_FILTERS, isActive, type Filters } from './types/filters';

// Client-side: the board arrives as one payload and there is no filter endpoint.
// Revisit if a project ever holds enough issues that fetching them all is the problem.
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
    // A 401 signs the user out globally; retrying just burns requests first.
    retry: false,
  });

  // Every user in the system - project membership does not exist yet.
  const { data: users } = useQuery({ queryKey: ['users'], queryFn: listUsers, retry: false });
  const usersById = useMemo(() => new Map(users?.map((u) => [u.id, u])), [users]);
  const { data: currentUser } = useQuery({ queryKey: ['me'], queryFn: getCurrentUser, retry: false });

  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [moveError, setMoveError] = useState(false);
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);

  // Optimistic: onMutate snapshots the board so onError can restore it, and
  // onSettled refetches so the client never has the last word over the server.
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
        // Drop indices are computed against the rendered (filtered) board but
        // applied to the full one, so hidden siblings make them wrong.
        dragDisabled={isActive(filters)}
      />
      {selectedTask && <IssueDetail task={selectedTask} onClose={() => setSelectedTask(null)} />}
    </>
  );
}

function AppShellSkeleton() {
  return (
    <div className="h-screen bg-gray-50 flex overflow-hidden">
      <div className="w-14 shrink-0 bg-[#0c2a52]" />
      <div className="w-56 shrink-0 bg-white border-r border-gray-200 p-4 space-y-2">
        <div className="h-3 w-16 bg-gray-200 rounded animate-pulse" />
        <div className="h-4 w-32 bg-gray-200 rounded animate-pulse" />
      </div>
      <div className="flex-1 flex flex-col min-w-0">
        <header className="px-4 py-3 bg-white border-b border-gray-200">
          <div className="h-4 w-56 bg-gray-200 rounded animate-pulse" />
        </header>
        <main className="flex-1 min-h-0">
          <BoardSkeleton />
        </main>
      </div>
    </div>
  );
}

function ProjectShell({ onSignedOut }: { onSignedOut: () => void }) {
  const { data: projects, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['projects'],
    queryFn: listProjects,
    retry: false,
  });
  // Same ['me'] key as Board below; React Query dedupes, so this is one request.
  const { data: currentUser } = useQuery({ queryKey: ['me'], queryFn: getCurrentUser, retry: false });
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  if (isLoading) {
    return <AppShellSkeleton />;
  }

  // Must precede the empty-project branch: otherwise a fetch failure leaves
  // `projects` undefined and renders "create your first project" instead.
  if (isError) {
    return (
      <div className="p-6 text-sm text-red-600">
        Could not load your projects: {error instanceof Error ? error.message : 'unknown error'}
      </div>
    );
  }

  // A fresh database has no projects; this is the first-run path.
  const project: Project | undefined = projects?.[0];
  if (!project) {
    return <CreateProjectDialog onCreated={() => refetch()} />;
  }

  return (
    <div className="h-screen bg-gray-50 flex overflow-hidden">
      <NavbarLeft onCreateIssue={() => setCreateOpen(true)} currentUser={currentUser} onSignedOut={onSignedOut} />
      <Sidebar
        project={project}
        collapsed={sidebarCollapsed}
        onOpenSettings={() => setSettingsOpen(true)}
      />
      <SidebarToggle collapsed={sidebarCollapsed} onClick={() => setSidebarCollapsed((c) => !c)} />
      <div className="flex-1 flex flex-col min-w-0">
        <header className="px-4 pt-3 pb-2 bg-white border-b border-gray-200">
          <div className="flex items-center justify-between">
            <Breadcrumb projectName={project.name} />
            <button
              onClick={() => setCreateOpen(true)}
              className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
            >
              Create issue
            </button>
          </div>
          <h1 className="text-xl font-semibold text-gray-900 mt-1">Kanban board</h1>
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
  const queryClient = useQueryClient();

  // clear() the cache too, or the next user briefly sees the previous one's data.
  const signOut = () => {
    queryClient.clear();
    setAuthenticated(false);
  };

  // signOut is omitted from the deps deliberately: it only closes over stable
  // values, so it cannot go stale.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => onUnauthorized(signOut), []);

  if (!authenticated) {
    return <Login onAuthenticated={() => setAuthenticated(true)} />;
  }

  return <ProjectShell onSignedOut={signOut} />;
}

export default App;
