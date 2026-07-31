import { api } from './client';
import type { Comment } from '../types/comment';

export const listComments = async (taskId: number): Promise<Comment[]> => {
  const response = await api.get(`/tasks/${taskId}/comments`);
  return response.data.data;
};

export const addComment = async (taskId: number, body: string): Promise<Comment> => {
  const response = await api.post(`/tasks/${taskId}/comments`, { body });
  return response.data.data;
};

export const deleteComment = async (taskId: number, commentId: number): Promise<void> => {
  await api.delete(`/tasks/${taskId}/comments/${commentId}`);
};
