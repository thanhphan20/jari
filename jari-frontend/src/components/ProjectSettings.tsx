import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateProject } from '../api/projects';
import type { Project } from '../types/project';
import { Modal } from './Modal';

interface Props {
  project: Project;
  onClose: () => void;
}

export const ProjectSettings: React.FC<Props> = ({ project, onClose }) => {
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? '');
  const queryClient = useQueryClient();

  const mutation = useMutation({
    // Spread so fields this form omits (leadUserId, active) survive the overwrite.
    mutationFn: () => updateProject(project.id, { ...project, name, description }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      onClose();
    },
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate();
  };

  return (
    <Modal onClose={onClose} panelClassName="w-96 bg-white p-6 rounded-lg shadow-lg">
      <form onSubmit={handleSubmit}>
        <h2 className="text-lg font-semibold mb-4">Project settings</h2>

        <label className="block text-sm text-gray-700 mb-1" htmlFor="proj-name">Name</label>
        <input
          id="proj-name"
          className="w-full mb-3 px-3 py-2 border border-gray-300 rounded text-sm"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        <label className="block text-sm text-gray-700 mb-1" htmlFor="proj-desc">Description</label>
        <textarea
          id="proj-desc"
          className="w-full mb-4 px-3 py-2 border border-gray-300 rounded text-sm"
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        {mutation.isError && <div className="mb-3 text-sm text-red-600">Could not save changes.</div>}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 rounded">
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {mutation.isPending ? 'Saving...' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  );
};
