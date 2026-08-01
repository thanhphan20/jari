import { api } from './client';
import type { Task } from '../types/kanban';

export const listTasksByProject = async (projectId: number): Promise<Task[]> => {
  const response = await api.get(`/tasks/project/${projectId}`);
  return response.data.data;
};

export const createTask = async (task: Omit<Task, 'id'>): Promise<Task> => {
  const response = await api.post('/tasks', task);
  return response.data.data;
};

// Overwrites, not merges: omitting `status` nulls the task's board column. Send
// the full record - hence Task, not Partial<Task>.
export const updateTask = async (id: number, task: Task): Promise<Task> => {
  const response = await api.put(`/tasks/${id}`, task);
  return response.data.data;
};

export const deleteTask = async (id: number): Promise<void> => {
  await api.delete(`/tasks/${id}`);
};
