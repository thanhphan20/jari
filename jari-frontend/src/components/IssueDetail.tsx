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
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-20 p-4" onClick={handleClose}>
      <div
        className="w-full max-w-3xl max-h-[85vh] bg-white rounded-lg shadow-xl flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-3 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <IssueTypeIcon type={task.type} />
            <span className="text-sm text-gray-500">{task.key}</span>
          </div>
          <div className="flex items-center gap-3">
            {confirmingDelete ? (
              <div className="flex items-center gap-2">
                <span className="text-sm text-gray-700">Delete this issue?</span>
                <button onClick={() => setConfirmingDelete(false)} className="px-2 py-1 text-sm text-gray-600 hover:bg-gray-50 rounded">
                  Cancel
                </button>
                <button
                  onClick={() => remove.mutate()}
                  disabled={remove.isPending}
                  className="px-2 py-1 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50"
                >
                  {remove.isPending ? 'Deleting...' : 'Delete'}
                </button>
              </div>
            ) : (
              <button onClick={() => setConfirmingDelete(true)} className="text-sm text-red-600 hover:underline">
                Delete
              </button>
            )}
            <button onClick={handleClose} className="text-gray-400 hover:text-gray-700" aria-label="Close">
              ✕
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto grid grid-cols-3 gap-6 p-6">
          <div className="col-span-2 space-y-4">
            <textarea
              className="w-full text-xl font-semibold border-none resize-none focus:outline-none focus:ring-1 focus:ring-blue-400 rounded p-1 -m-1"
              value={task.summary}
              rows={2}
              onChange={(e) => setTask({ ...task, summary: e.target.value })}
              onBlur={() => task.summary !== initialTask.summary && update.mutate({ summary: task.summary })}
            />

            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Description</div>
              <textarea
                className="w-full text-sm border border-gray-200 rounded p-2 focus:outline-none focus:ring-1 focus:ring-blue-400"
                rows={8}
                placeholder="Add a description..."
                value={task.description ?? ''}
                onChange={(e) => setTask({ ...task, description: e.target.value })}
                onBlur={() => task.description !== initialTask.description && update.mutate({ description: task.description })}
              />
            </div>

            {update.isError && <div className="text-sm text-red-600">Could not save the change.</div>}
          </div>

          <div className="col-span-1 space-y-4">
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
              <div className="text-xs font-medium text-gray-500 mb-1">Assignee</div>
              <div className="flex items-center gap-2 mb-1">
                <Avatar user={assignee} size={22} />
                <span className="text-sm text-gray-700">{assignee ? assignee.username : 'Unassigned'}</span>
              </div>
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

            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Reporter</div>
              <div className="flex items-center gap-2">
                <Avatar user={reporter} size={22} />
                <span className="text-sm text-gray-700">{reporter?.username ?? '-'}</span>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
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
                <div className="flex items-center gap-1">
                  <PriorityIcon priority={task.priority} />
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
              </div>
            </div>

            <dl className="text-xs text-gray-500 space-y-1 pt-3 border-t border-gray-100">
              <div className="flex justify-between"><dt>Created</dt><dd>{task.createdAt ? new Date(task.createdAt).toLocaleString() : '-'}</dd></div>
              <div className="flex justify-between"><dt>Updated</dt><dd>{task.updatedAt ? new Date(task.updatedAt).toLocaleString() : '-'}</dd></div>
            </dl>
          </div>
        </div>
      </div>
    </div>
  );
};
