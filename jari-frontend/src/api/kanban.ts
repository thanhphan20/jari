import { api } from './client';
import type { KanbanBoard } from '../types/kanban';

export const getKanbanBoard = async (projectId: number): Promise<KanbanBoard> => {
  // Endpoints return ResponseDto<T>, so the payload is one level down.
  const response = await api.get(`/tasks/kanban/${projectId}`);
  return response.data.data;
};

export const moveTask = async (taskId: number, targetStatus: string, targetIndex?: number): Promise<void> => {
  await api.post('/tasks/kanban/move', {
    taskId,
    targetStatus,
    targetIndex,
  });
};
