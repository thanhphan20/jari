import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { updateTask, deleteTask } from '../api/tasks';
import { listUsers } from '../api/users';
import type { Task } from '../types/kanban';
import { IssueTypeIcon } from './icons/IssueTypeIcon';
import { PriorityIcon } from './icons/PriorityIcon';
import { Avatar } from './Avatar';

// KanbanService.STANDARD_COLUMNS is exactly these three; moveTask throws on
// anything else, and PUT /tasks/{id} has no such guard of its own - sending
// a status outside this list would desync the task from every board column.
const STATUSES = ['TODO', 'IN_PROGRESS', 'DONE'];
const TYPES = [1, 2, 3, 4];
const PRIORITIES = [1, 2, 3, 4, 5];

interface Props {
  task: Task;
  onClose: () => void;
}

export const IssueDetail: React.FC<Props> = ({ task: initialTask, onClose }) => {
  // Held in local state rather than read from the prop directly. The parent
  // only refetches the board list on a successful mutation; it does not (and
  // has no way to) hand this panel a fresh Task back. Without local state, a
  // select's `value` would keep pointing at the prop from the moment the
  // panel opened, so a change would render, then immediately appear to
  // revert on the next parent render - the click would look like it didn't
  // register even though the request succeeded.
  const [task, setTask] = useState(initialTask);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const queryClient = useQueryClient();

  const { data: users } = useQuery({ queryKey: ['users'], queryFn: listUsers });

  const invalidateBoard = () => queryClient.invalidateQueries({ queryKey: ['kanban', task.projectId] });

  // Every field change goes through the same full-record PUT, since
  // TaskService.updateTask overwrites rather than merges (see api/tasks.ts).
  // Spreading the current local `task` keeps every other field intact.
  const update = useMutation({
    mutationFn: (patch: Partial<Task>) => updateTask(task.id, { ...task, ...patch }),
    onSuccess: (updated) => {
      setTask(updated);
      invalidateBoard();
    },
  });

  const remove = useMutation({
    mutationFn: () => deleteTask(task.id),
    onSuccess: () => {
      invalidateBoard();
      onClose();
    },
  });

  // Summary and description save on blur, which normally fires before a
  // click on another element registers. Relying on that ordering for the
  // close button specifically is the wrong tradeoff: "edit a field, then
  // immediately click Close" is an ordinary thing to do, and if a browser,
  // extension, or synthetic input event ever delivers blur and click out of
  // the order this assumes, the edit is lost silently with no error shown.
  // Flushing explicitly before closing removes the assumption entirely.
  const handleClose = () => {
    if (task.summary !== initialTask.summary) update.mutate({ summary: task.summary });
    if (task.description !== initialTask.description) update.mutate({ description: task.description });
    onClose();
  };

  const assignee = users?.find((u) => u.id === task.assigneeId);
  const reporter = users?.find((u) => u.id === task.reporterId);

  return (
    <>
      <div className="fixed inset-0 bg-black/20 z-10" onClick={handleClose} />
      <div className="fixed top-0 right-0 h-full w-[420px] bg-white shadow-lg z-20 flex flex-col">
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200">
          <span className="text-xs text-gray-500">{task.key}</span>
          <button onClick={handleClose} className="text-gray-400 hover:text-gray-700" aria-label="Close">
            ✕
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          <textarea
            className="w-full text-lg font-semibold border-none resize-none focus:outline-none focus:ring-1 focus:ring-blue-400 rounded p-1 -m-1"
            value={task.summary}
            rows={2}
            onChange={(e) => setTask({ ...task, summary: e.target.value })}
            onBlur={() => task.summary !== initialTask.summary && update.mutate({ summary: task.summary })}
          />

          <div>
            <div className="text-xs font-medium text-gray-500 mb-1">Description</div>
            <textarea
              className="w-full text-sm border border-gray-200 rounded p-2 focus:outline-none focus:ring-1 focus:ring-blue-400"
              rows={4}
              value={task.description ?? ''}
              onChange={(e) => setTask({ ...task, description: e.target.value })}
              onBlur={() => task.description !== initialTask.description && update.mutate({ description: task.description })}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Status</div>
              <select
                className="w-full text-sm border border-gray-200 rounded p-1.5"
                value={task.status}
                onChange={(e) => update.mutate({ status: e.target.value })}
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>{s.replace('_', ' ')}</option>
                ))}
              </select>
            </div>

            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Type</div>
              <select
                className="w-full text-sm border border-gray-200 rounded p-1.5"
                value={task.type}
                onChange={(e) => update.mutate({ type: Number(e.target.value) })}
              >
                {TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>

            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Priority</div>
              <select
                className="w-full text-sm border border-gray-200 rounded p-1.5"
                value={task.priority}
                onChange={(e) => update.mutate({ priority: Number(e.target.value) })}
              >
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>{p}</option>
                ))}
              </select>
            </div>

            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Assignee</div>
              <select
                className="w-full text-sm border border-gray-200 rounded p-1.5"
                value={task.assigneeId ?? ''}
                onChange={(e) => update.mutate({ assigneeId: e.target.value ? Number(e.target.value) : undefined })}
              >
                <option value="">Unassigned</option>
                {users?.map((u) => (
                  <option key={u.id} value={u.id}>{u.username}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex items-center gap-3 pt-2 border-t border-gray-100">
            <IssueTypeIcon type={task.type} />
            <PriorityIcon priority={task.priority} />
            <Avatar user={assignee} size={22} />
            <span className="text-xs text-gray-500">{assignee ? assignee.username : 'Unassigned'}</span>
          </div>

          <dl className="text-xs text-gray-500 space-y-1 pt-2 border-t border-gray-100">
            <div className="flex justify-between"><dt>Reporter</dt><dd>{reporter?.username ?? '-'}</dd></div>
            <div className="flex justify-between"><dt>Created</dt><dd>{task.createdAt ? new Date(task.createdAt).toLocaleString() : '-'}</dd></div>
            <div className="flex justify-between"><dt>Updated</dt><dd>{task.updatedAt ? new Date(task.updatedAt).toLocaleString() : '-'}</dd></div>
          </dl>

          {update.isError && <div className="text-sm text-red-600">Could not save the change.</div>}
        </div>

        <div className="p-4 border-t border-gray-200">
          {confirmingDelete ? (
            <div className="flex items-center gap-2">
              <span className="text-sm text-gray-700 flex-1">Delete this issue?</span>
              <button onClick={() => setConfirmingDelete(false)} className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 rounded">
                Cancel
              </button>
              <button
                onClick={() => remove.mutate()}
                disabled={remove.isPending}
                className="px-3 py-1.5 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50"
              >
                {remove.isPending ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          ) : (
            <button onClick={() => setConfirmingDelete(true)} className="text-sm text-red-600 hover:underline">
              Delete issue
            </button>
          )}
        </div>
      </div>
    </>
  );
};
