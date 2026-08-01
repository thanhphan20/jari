export interface Task {
  id: number;
  key: string;
  summary: string;
  description?: string;
  status: string;
  order?: number;
  priority: number;
  type: number;
  projectId: number;
  reporterId?: number;
  assigneeId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface KanbanColumn {
  id: string; // status
  title: string;
  tasks: Task[];
}

export interface KanbanBoard {
  columns: KanbanColumn[];
}

export interface EnumMeta {
  label: string;
  color: string;
}

export const TYPE_META: Record<number, EnumMeta> = {
  1: { label: 'Task', color: '#2563eb' },
  2: { label: 'Bug', color: '#dc2626' },
  3: { label: 'Story', color: '#16a34a' },
  4: { label: 'Epic', color: '#7c3aed' },
};

export const PRIORITY_META: Record<number, EnumMeta> = {
  1: { label: 'Lowest', color: '#2563eb' },
  2: { label: 'Low', color: '#16a34a' },
  3: { label: 'Medium', color: '#ca8a04' },
  4: { label: 'High', color: '#ea580c' },
  5: { label: 'Highest', color: '#dc2626' },
};

export const TYPE_IDS = Object.keys(TYPE_META).map(Number);
export const PRIORITY_IDS = Object.keys(PRIORITY_META).map(Number);

// Must match KanbanService.STANDARD_COLUMNS - anything else desyncs the board.
export const STATUSES = ['TODO', 'IN_PROGRESS', 'DONE'];
export const STATUS_PILL: Record<string, string> = {
  TODO: 'bg-gray-200 text-gray-700',
  IN_PROGRESS: 'bg-blue-600 text-white',
  DONE: 'bg-green-600 text-white',
};
