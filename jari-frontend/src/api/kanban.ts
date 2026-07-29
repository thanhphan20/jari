import axios from 'axios';
import type { KanbanBoard } from '../types/kanban';

// Assuming API gateway is at localhost:8080
const API_URL = 'http://localhost:8080/api';

export const getKanbanBoard = async (projectId: number): Promise<KanbanBoard> => {
  const response = await axios.get(`${API_URL}/tasks/kanban/${projectId}`);
  return response.data.data;
};

export const moveTask = async (taskId: number, targetStatus: string, targetIndex?: number): Promise<void> => {
  await axios.post(`${API_URL}/tasks/kanban/move`, {
    taskId,
    targetStatus,
    targetIndex,
  });
};
