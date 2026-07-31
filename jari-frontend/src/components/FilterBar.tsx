import type { User } from '../types/user';
import { EMPTY_FILTERS, isActive, type Filters } from '../types/filters';
import { Avatar } from './Avatar';

const TYPE_LABELS: Record<number, string> = { 1: 'Task', 2: 'Bug', 3: 'Story', 4: 'Epic' };

interface Props {
  filters: Filters;
  onChange: (filters: Filters) => void;
  users?: User[];
  currentUserId?: number;
}

export const FilterBar: React.FC<Props> = ({ filters, onChange, users, currentUserId }) => (
  <div className="flex items-center gap-3 px-4 py-2 bg-white border-b border-gray-200 flex-wrap">
    <input
      aria-label="Search issues"
      className="px-2 py-1 text-sm border border-gray-300 rounded w-48"
      placeholder="Search issues"
      value={filters.text}
      onChange={(e) => onChange({ ...filters, text: e.target.value })}
    />

    <div className="flex items-center -space-x-1">
      {users?.map((u) => (
        <button
          key={u.id}
          onClick={() => onChange({ ...filters, assigneeId: filters.assigneeId === u.id ? null : u.id })}
          className="rounded-full ring-2 ring-white hover:z-10"
          style={{ outline: filters.assigneeId === u.id ? '2px solid #2563eb' : 'none' }}
          title={`Filter by ${u.username}`}
        >
          <Avatar user={u} size={26} />
        </button>
      ))}
    </div>

    <select
      aria-label="Filter by issue type"
      className="px-2 py-1 text-sm border border-gray-300 rounded"
      value={filters.type ?? ''}
      onChange={(e) => onChange({ ...filters, type: e.target.value ? Number(e.target.value) : null })}
    >
      <option value="">All types</option>
      {Object.entries(TYPE_LABELS).map(([v, label]) => (
        <option key={v} value={v}>{label}</option>
      ))}
    </select>

    <button
      onClick={() => onChange({ ...filters, onlyMine: !filters.onlyMine })}
      disabled={!currentUserId}
      className={`px-2 py-1 text-sm rounded border ${
        filters.onlyMine ? 'bg-blue-50 border-blue-400 text-blue-700' : 'border-gray-300 text-gray-600'
      }`}
    >
      Only my issues
    </button>

    {isActive(filters) && (
      <button onClick={() => onChange(EMPTY_FILTERS)} className="text-sm text-gray-500 hover:underline">
        Clear filters
      </button>
    )}
  </div>
);
