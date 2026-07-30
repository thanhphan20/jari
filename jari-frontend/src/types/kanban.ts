export interface Task {
  id: number;
  key: string;
  summary: string;
  description?: string;
  status: string;
  priority: number;
  type: number;
  assigneeId?: number;
}

export interface KanbanColumn {
  id: string; // status
  title: string;
  tasks: Task[];
}

export interface KanbanBoard {
  columns: KanbanColumn[];
}
