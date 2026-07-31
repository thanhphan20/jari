import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowsInSimple, ArrowsOutSimple, CaretDown, Trash, X } from '@phosphor-icons/react';
import { updateTask, deleteTask } from '../api/tasks';
import { listUsers } from '../api/users';
import { TYPE_META, PRIORITY_META, TYPE_IDS, PRIORITY_IDS, STATUSES, STATUS_PILL, type Task } from '../types/kanban';
import { IssueTypeIcon } from './icons/IssueTypeIcon';
import { PriorityIcon } from './icons/PriorityIcon';
import { Avatar } from './Avatar';
import { Dropdown, DropdownItem } from './Dropdown';
import { Modal } from './Modal';
import { RichTextEditor } from './RichTextEditor';
import { CommentThread } from './CommentThread';

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
  const [fullscreen, setFullscreen] = useState(false);
  const queryClient = useQueryClient();

  const { data: users } = useQuery({ queryKey: ['users'], queryFn: listUsers });
  const usersById = useMemo(() => new Map((users ?? []).map((u) => [u.id, u])), [users]);

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
  const [closeSaveError, setCloseSaveError] = useState(false);

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
  //
  // One combined save, not two independent ones, and awaited before closing:
  // `task` already holds both fields merged locally, so a single PUT carries
  // everything that changed. Firing two separate mutate() calls here was
  // redundant rather than actually racy - both would send the same complete
  // snapshot - but the real bug was that onClose() ran unconditionally
  // regardless of outcome, so a failed save unmounted the panel (and its
  // error message) with no feedback at all. Awaiting the save and only
  // closing on success fixes that; a failure now keeps the panel open with
  // the error visible instead of silently discarding the edit.
  const handleClose = async () => {
    const dirty = task.summary !== initialTask.summary || task.description !== initialTask.description;
    if (!dirty) {
      onClose();
      return;
    }
    try {
      setCloseSaveError(false);
      await update.mutateAsync({ summary: task.summary, description: task.description });
      onClose();
    } catch {
      setCloseSaveError(true);
    }
  };

  const assignee = users?.find((u) => u.id === task.assigneeId);

  return (
    <Modal
      onClose={handleClose}
      panelClassName={`w-full bg-white rounded-lg shadow-xl flex flex-col ${
        fullscreen ? 'max-w-none h-full' : 'max-w-3xl max-h-[85vh]'
      }`}
    >
        <div className="flex items-center justify-between px-6 py-3 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <Dropdown
              trigger={<IssueTypeIcon type={task.type} className="cursor-pointer" />}
              menuClassName="left-0 mt-1 min-w-[8rem]"
            >
              {(close) =>
                TYPE_IDS.map((t) => (
                  <DropdownItem
                    key={t}
                    active={t === task.type}
                    onClick={() => {
                      update.mutate({ type: t });
                      close();
                    }}
                  >
                    <IssueTypeIcon type={t} />
                    <span>{TYPE_META[t].label}</span>
                  </DropdownItem>
                ))
              }
            </Dropdown>
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
              <button
                onClick={() => setConfirmingDelete(true)}
                className="text-gray-400 hover:text-red-600"
                aria-label="Delete issue"
                title="Delete issue"
              >
                <Trash size={18} />
              </button>
            )}
            <button
              onClick={() => setFullscreen((f) => !f)}
              className="text-gray-400 hover:text-gray-700"
              aria-label={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
              title={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
            >
              {fullscreen ? <ArrowsInSimple size={18} /> : <ArrowsOutSimple size={18} />}
            </button>
            <button onClick={handleClose} className="text-gray-400 hover:text-gray-700" aria-label="Close">
              <X size={18} />
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
              <RichTextEditor
                content={task.description ?? ''}
                onBlur={(html) => {
                  setTask((t) => ({ ...t, description: html }));
                  if (html !== (task.description ?? '')) update.mutate({ description: html });
                }}
              />
            </div>

            {update.isError && <div className="text-sm text-red-600">Could not save the change.</div>}
            {closeSaveError && (
              <div className="text-sm text-red-600">
                Could not save your changes. The issue is still open so you can try again.
              </div>
            )}

            <CommentThread taskId={task.id} usersById={usersById} />
          </div>

          <div className="col-span-1 space-y-4">
            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Status</div>
              <Dropdown
                trigger={
                  <span
                    className={`inline-flex items-center gap-1.5 text-sm font-medium rounded px-2.5 py-1.5 ${
                      STATUS_PILL[task.status] ?? 'bg-gray-200 text-gray-700'
                    }`}
                  >
                    {task.status.replace('_', ' ')}
                    <CaretDown size={12} />
                  </span>
                }
              >
                {(close) =>
                  STATUSES.map((s) => (
                    <DropdownItem
                      key={s}
                      active={s === task.status}
                      onClick={() => {
                        update.mutate({ status: s });
                        close();
                      }}
                    >
                      {s.replace('_', ' ')}
                    </DropdownItem>
                  ))
                }
              </Dropdown>
            </div>

            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Assignee</div>
              <Dropdown
                triggerClassName="flex items-center gap-2 rounded p-1 -m-1 hover:bg-gray-50"
                trigger={
                  <>
                    <Avatar user={assignee} size={22} />
                    <span className="text-sm text-gray-700">{assignee ? assignee.username : 'Unassigned'}</span>
                    <CaretDown size={12} className="text-gray-400" />
                  </>
                }
              >
                {(close) => (
                  <>
                    <DropdownItem
                      active={!task.assigneeId}
                      onClick={() => {
                        update.mutate({ assigneeId: undefined });
                        close();
                      }}
                    >
                      <Avatar user={undefined} size={20} />
                      <span>Unassigned</span>
                    </DropdownItem>
                    {users?.map((u) => (
                      <DropdownItem
                        key={u.id}
                        active={u.id === task.assigneeId}
                        onClick={() => {
                          update.mutate({ assigneeId: u.id });
                          close();
                        }}
                      >
                        <Avatar user={u} size={20} />
                        <span>{u.username}</span>
                      </DropdownItem>
                    ))}
                  </>
                )}
              </Dropdown>
            </div>

            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">Priority</div>
              <Dropdown
                triggerClassName="flex items-center gap-1.5 rounded p-1 -m-1 hover:bg-gray-50"
                trigger={
                  <>
                    <PriorityIcon priority={task.priority} />
                    <span className="text-sm text-gray-700">{PRIORITY_META[task.priority].label}</span>
                    <CaretDown size={12} className="text-gray-400" />
                  </>
                }
              >
                {(close) =>
                  PRIORITY_IDS.map((p) => (
                    <DropdownItem
                      key={p}
                      active={p === task.priority}
                      onClick={() => {
                        update.mutate({ priority: p });
                        close();
                      }}
                    >
                      <PriorityIcon priority={p} />
                      <span>{PRIORITY_META[p].label}</span>
                    </DropdownItem>
                  ))
                }
              </Dropdown>
            </div>

            <dl className="text-xs text-gray-500 space-y-1 pt-3 border-t border-gray-100">
              <div className="flex justify-between"><dt>Created</dt><dd>{task.createdAt ? new Date(task.createdAt).toLocaleString() : '-'}</dd></div>
              <div className="flex justify-between"><dt>Updated</dt><dd>{task.updatedAt ? new Date(task.updatedAt).toLocaleString() : '-'}</dd></div>
            </dl>
          </div>
        </div>
    </Modal>
  );
};
