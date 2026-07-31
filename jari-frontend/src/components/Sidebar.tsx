import type { Project } from '../types/project';

interface Props {
  project: Project;
  onOpenSettings: () => void;
}

export const Sidebar: React.FC<Props> = ({ project, onOpenSettings }) => (
  <aside className="w-56 shrink-0 bg-white border-r border-gray-200 flex flex-col">
    <div className="p-4 border-b border-gray-200">
      <div className="text-xs uppercase tracking-wide text-gray-400">{project.key}</div>
      <div className="font-semibold text-gray-800 truncate">{project.name}</div>
    </div>
    <nav className="flex-1 p-2">
      <div className="px-2 py-1.5 rounded text-sm font-medium text-blue-700 bg-blue-50">Board</div>
    </nav>
    <button
      onClick={onOpenSettings}
      className="m-2 px-2 py-1.5 text-left text-sm text-gray-600 hover:bg-gray-50 rounded"
    >
      Project settings
    </button>
  </aside>
);
