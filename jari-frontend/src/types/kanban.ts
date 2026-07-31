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
