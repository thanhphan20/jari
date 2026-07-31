import type { Project } from '../types/project';

interface Props {
  project: Project;
  collapsed: boolean;
  onOpenSettings: () => void;
}

export const Sidebar: React.FC<Props> = ({ project, collapsed, onOpenSettings }) => (
  <aside
    className={`shrink-0 bg-white border-r border-gray-200 flex flex-col overflow-hidden transition-[width] duration-150 ${
      collapsed ? 'w-0' : 'w-56'
    }`}
  >
    <div className="w-56 flex flex-col h-full">
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
    </div>
  </aside>
);

// A separate small component (not part of the aside itself) so its toggle
// button stays visible and clickable at a fixed position regardless of
// whether the drawer is currently open or collapsed to zero width.
export const SidebarToggle: React.FC<{ collapsed: boolean; onClick: () => void }> = ({ collapsed, onClick }) => (
  <button
    onClick={onClick}
    title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
    className="w-6 h-12 shrink-0 flex items-center justify-center text-gray-400 hover:text-gray-700 hover:bg-gray-50 border-r border-gray-200 bg-white"
  >
    {collapsed ? '»' : '«'}
  </button>
);
