import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getKanbanBoard, moveTask } from './api/kanban';
import { listProjects } from './api/projects';
import { listUsers, getCurrentUser } from './api/users';
import { getToken, onUnauthorized } from './api/client';
import { KanbanBoard, type MoveArgs } from './components/KanbanBoard';
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
        // A drop's target index is computed against whatever board is
        // rendered - which, while any filter is active, is filteredBoard, a
        // subset. The mutation always applies that index to the full
        // unfiltered cache, so a filtered index has no correct translation
        // back (hidden siblings mean the "same visual position" maps to a
        // different real position). Disabling drag is simpler and more
        // honest than a translation that would still be wrong whenever a
        // hidden task sits between the source and target slots.
        dragDisabled={isActive(filters)}
      />
      {selectedTask && <IssueDetail task={selectedTask} onClose={() => setSelectedTask(null)} />}
    </>
  );
}

function ProjectShell({ onSignedOut }: { onSignedOut: () => void }) {
  const { data: projects, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['projects'],
    queryFn: listProjects,
    retry: false,
  });
  // Shares the ['me'] cache entry with Board's own query below - React Query
  // dedupes by key, so this doesn't double the request.
  const { data: currentUser } = useQuery({ queryKey: ['me'], queryFn: getCurrentUser, retry: false });
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  if (isLoading) {
    return <div className="p-6 text-sm text-gray-500">Loading...</div>;
  }

  // Checked before the empty-project branch below, not after: without this,
  // a real failure (network down, 500, an expired-but-not-yet-401 token)
  // left `projects` undefined, which `projects?.[0]` turns into "no
  // project", which rendered the first-run "create your first project"
  // screen - misrepresenting an error as an empty database.
  if (isError) {
    return (
      <div className="p-6 text-sm text-red-600">
        Could not load your projects: {error instanceof Error ? error.message : 'unknown error'}
      </div>
    );
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
      <NavbarLeft onCreateIssue={() => setCreateOpen(true)} currentUser={currentUser} onSignedOut={onSignedOut} />
      <Sidebar
        project={project}
        collapsed={sidebarCollapsed}
        onOpenSettings={() => setSettingsOpen(true)}
      />
      <SidebarToggle collapsed={sidebarCollapsed} onClick={() => setSidebarCollapsed((c) => !c)} />
      <div className="flex-1 flex flex-col min-w-0">
        <header className="px-4 py-3 bg-white border-b border-gray-200 flex items-center justify-between">
          <Breadcrumb projectName={project.name} />
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
  const queryClient = useQueryClient();

  // Clears every cached query - not just the token - on the way out. Without
  // this, React Query's stale-while-revalidate behavior would briefly render
  // the previous session's board/tasks/comments from cache the instant a
  // different user logs in on the same tab, before the fresh fetch resolves.
  // Called from both sign-out paths: automatic (401, below) and manual
  // (ProfileMenu's Sign out, via onSignedOut).
  const signOut = () => {
    queryClient.clear();
    setAuthenticated(false);
  };

  // The response interceptor clears the token on a 401; this puts the UI back
  // on the login screen rather than leaving a blank or half-rendered page.
  // signOut is intentionally omitted from the deps: it's recreated every
  // render, but only closes over queryClient (stable for the component's
  // lifetime) and setAuthenticated (stable by React's own guarantee), so a
  // stale closure here can't reference stale state.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => onUnauthorized(signOut), []);

  if (!authenticated) {
    return <Login onAuthenticated={() => setAuthenticated(true)} />;
  }

  return <ProjectShell onSignedOut={signOut} />;
}

export default App;
