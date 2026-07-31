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

// TaskService.updateTask overwrites summary, description, order, priority,
// type, status, and assigneeId from the request body - it is not a merge.
// status in particular has no null-fallback the way createTask's does, so an
// omitted status can null out the column a task is in. Callers must submit
// the full record (spread the current task and override only what changed),
// which is why this takes Task, not Partial<Task> - the same shape of
// footgun as ProjectService.updateProject, avoided the same way.
export const updateTask = async (id: number, task: Task): Promise<Task> => {
  const response = await api.put(`/tasks/${id}`, task);
  return response.data.data;
};

export const deleteTask = async (id: number): Promise<void> => {
  await api.delete(`/tasks/${id}`);
};
