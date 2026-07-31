import {
  CaretLeft,
  CaretRight,
  Kanban,
  Gear,
  Briefcase,
  ListChecks,
  FileText,
  ChartLineUp,
  Cube,
} from '@phosphor-icons/react';
import type { Project } from '../types/project';

interface Props {
  project: Project;
  collapsed: boolean;
  onOpenSettings: () => void;
}

// No backing route yet - muted and unclickable rather than dead links.
const PLANNED_ITEMS = [
  { label: 'Releases', icon: Briefcase },
  { label: 'Issues and filters', icon: ListChecks },
  { label: 'Pages', icon: FileText },
  { label: 'Reports', icon: ChartLineUp },
  { label: 'Components', icon: Cube },
];

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
      <nav className="p-2">
        <div className="flex items-center gap-2 px-2 py-1.5 rounded text-sm font-medium text-blue-700 bg-blue-50">
          <Kanban size={16} />
          Board
        </div>
        <button
          onClick={onOpenSettings}
          className="w-full flex items-center gap-2 px-2 py-1.5 mt-0.5 text-left text-sm text-gray-600 hover:bg-gray-50 rounded"
        >
          <Gear size={16} />
          Project settings
        </button>
      </nav>
      <div className="flex-1 p-2 pt-0 overflow-y-auto min-h-0">
        <div className="border-t border-gray-100 my-2" />
        {PLANNED_ITEMS.map(({ label, icon: Icon }) => (
          <div key={label} className="flex items-center gap-2 px-2 py-1.5 text-sm text-gray-400 select-none">
            <Icon size={16} />
            {label}
          </div>
        ))}
      </div>
    </div>
  </aside>
);

export const SidebarToggle: React.FC<{ collapsed: boolean; onClick: () => void }> = ({ collapsed, onClick }) => (
  <div className="relative w-0 shrink-0 z-10">
    <button
      onClick={onClick}
      title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      className="absolute -left-3 top-4 w-6 h-6 rounded-full bg-white border border-gray-200 shadow flex items-center justify-center text-gray-400 hover:text-gray-700 hover:bg-gray-50"
    >
      {collapsed ? <CaretRight size={12} /> : <CaretLeft size={12} />}
    </button>
  </div>
);
