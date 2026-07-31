import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createTask, listTasksByProject } from '../api/tasks';
import { listUsers, getCurrentUser } from '../api/users';
import type { Project } from '../types/project';

const TYPES = [1, 2, 3, 4];
const TYPE_LABELS: Record<number, string> = { 1: 'Task', 2: 'Bug', 3: 'Story', 4: 'Epic' };
const PRIORITIES = [1, 2, 3, 4, 5];

interface Props {
  project: Project;
  onClose: () => void;
}

// TaskService.createTask never generates a key - it stores whatever key.ts
// sends. tasks.key also has no unique constraint (V1__baseline.sql records
// that deliberately as a known defect for a later phase). This derivation is
// therefore genuinely racy: two clients creating an issue for the same
// project at the same moment can compute the same next suffix and collide.
// Accepted rather than worked around client-side - the real fix is a
// server-side per-project counter, and faking uniqueness here would hide the
// defect instead of leaving it visible for that later change to find.
function nextKey(projectKey: string, existingKeys: string[]): string {
  const prefix = `${projectKey}-`;
  const maxSuffix = existingKeys
    .filter((k) => k.startsWith(prefix))
    .map((k) => Number(k.slice(prefix.length)))
    .filter((n) => Number.isFinite(n))
    .reduce((max, n) => Math.max(max, n), 0);
  return `${prefix}${maxSuffix + 1}`;
}

export const CreateIssueDialog: React.FC<Props> = ({ project, onClose }) => {
  const [summary, setSummary] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState(1);
  const [priority, setPriority] = useState(3);
  const [assigneeId, setAssigneeId] = useState<number | ''>('');
  const queryClient = useQueryClient();

  const { data: users } = useQuery({ queryKey: ['users'], queryFn: listUsers });
  const { data: existingTasks } = useQuery({
    queryKey: ['tasks', 'project', project.id],
    queryFn: () => listTasksByProject(project.id),
  });

  const mutation = useMutation({
    mutationFn: async () => {
      const me = await getCurrentUser();
      const key = nextKey(project.key, existingTasks?.map((t) => t.key) ?? []);
      return createTask({
        key,
        summary,
        description,
        type,
        priority,
        status: 'TODO',
        projectId: project.id,
        reporterId: me.id,
        assigneeId: assigneeId === '' ? undefined : assigneeId,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kanban', project.id] });
      onClose();
    },
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate();
  };

  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-20" onClick={onClose}>
      <form onSubmit={handleSubmit} onClick={(e) => e.stopPropagation()} className="w-[420px] bg-white p-6 rounded-lg shadow-lg">
        <h2 className="text-lg font-semibold mb-4">Create issue</h2>

        <label className="block text-sm text-gray-700 mb-1" htmlFor="ci-summary">
          Summary <span className="text-gray-400">(5-100 characters)</span>
        </label>
        <input
          id="ci-summary"
          className="w-full mb-3 px-3 py-2 border border-gray-300 rounded text-sm"
          value={summary}
          onChange={(e) => setSummary(e.target.value)}
          minLength={5}
          maxLength={100}
          required
        />

        <label className="block text-sm text-gray-700 mb-1" htmlFor="ci-desc">Description</label>
        <textarea
          id="ci-desc"
          className="w-full mb-3 px-3 py-2 border border-gray-300 rounded text-sm"
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <div className="grid grid-cols-3 gap-3 mb-4">
          <div>
            <label className="block text-sm text-gray-700 mb-1" htmlFor="ci-type">Type</label>
            <select id="ci-type" className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm" value={type} onChange={(e) => setType(Number(e.target.value))}>
              {TYPES.map((t) => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm text-gray-700 mb-1" htmlFor="ci-priority">Priority</label>
            <select id="ci-priority" className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm" value={priority} onChange={(e) => setPriority(Number(e.target.value))}>
              {PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm text-gray-700 mb-1" htmlFor="ci-assignee">Assignee</label>
            <select
              id="ci-assignee"
              className="w-full px-2 py-1.5 border border-gray-300 rounded text-sm"
              value={assigneeId}
              onChange={(e) => setAssigneeId(e.target.value ? Number(e.target.value) : '')}
            >
              <option value="">Unassigned</option>
              {users?.map((u) => <option key={u.id} value={u.id}>{u.username}</option>)}
            </select>
          </div>
        </div>

        {mutation.isError && <div className="mb-3 text-sm text-red-600">Could not create the issue.</div>}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 rounded">
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {mutation.isPending ? 'Creating...' : 'Create issue'}
          </button>
        </div>
      </form>
    </div>
  );
};
