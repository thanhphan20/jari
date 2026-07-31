import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { createProject } from '../api/projects';
import type { Project } from '../types/project';

interface Props {
  onCreated: (project: Project) => void;
}

export const CreateProjectDialog: React.FC<Props> = ({ onCreated }) => {
  const [key, setKey] = useState('');
  const [name, setName] = useState('');

  const mutation = useMutation({
    mutationFn: () => createProject({ key: key.toUpperCase(), name, description: '' }),
    onSuccess: onCreated,
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate();
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <form onSubmit={handleSubmit} className="w-96 bg-white p-6 rounded-lg shadow-sm border border-gray-200">
        <h1 className="text-lg font-semibold mb-1">Create your first project</h1>
        <p className="text-sm text-gray-500 mb-4">There is nothing to show yet - projects start here.</p>

        <label className="block text-sm text-gray-700 mb-1" htmlFor="key">Project key</label>
        <input
          id="key"
          className="w-full mb-3 px-3 py-2 border border-gray-300 rounded text-sm uppercase"
          value={key}
          onChange={(e) => setKey(e.target.value.toUpperCase())}
          placeholder="JARI"
          maxLength={10}
          required
        />

        <label className="block text-sm text-gray-700 mb-1" htmlFor="name">Project name</label>
        <input
          id="name"
          className="w-full mb-4 px-3 py-2 border border-gray-300 rounded text-sm"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        {mutation.isError && <div className="mb-3 text-sm text-red-600">Could not create the project.</div>}

        <button
          type="submit"
          disabled={mutation.isPending}
          className="w-full bg-blue-600 text-white py-2 rounded text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          {mutation.isPending ? 'Creating...' : 'Create project'}
        </button>
      </form>
    </div>
  );
};
