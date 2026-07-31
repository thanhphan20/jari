import { MagnifyingGlass } from '@phosphor-icons/react';
import type { User } from '../types/user';
import { EMPTY_FILTERS, isActive, type Filters } from '../types/filters';
import { TYPE_META } from '../types/kanban';
import { Avatar } from './Avatar';

interface Props {
  filters: Filters;
  onChange: (filters: Filters) => void;
  users?: User[];
  currentUserId?: number;
}

export const FilterBar: React.FC<Props> = ({ filters, onChange, users, currentUserId }) => (
  <div className="flex items-center gap-3 px-4 py-2 bg-white border-b border-gray-200 flex-wrap">
    <div className="relative">
      <MagnifyingGlass size={14} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400" />
      <input
        aria-label="Search issues"
        className="pl-7 pr-2 py-1 text-sm border border-gray-300 rounded w-48"
        placeholder="Search issues"
        value={filters.text}
        onChange={(e) => onChange({ ...filters, text: e.target.value })}
      />
    </div>

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
      {Object.entries(TYPE_META).map(([v, meta]) => (
        <option key={v} value={v}>{meta.label}</option>
      ))}
    </select>

    <button
      onClick={() => onChange({ ...filters, onlyMine: !filters.onlyMine })}
      disabled={!currentUserId}
      className={`text-sm font-medium disabled:opacity-40 disabled:cursor-not-allowed ${
        filters.onlyMine ? 'text-blue-700 underline' : 'text-blue-600 hover:underline'
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
