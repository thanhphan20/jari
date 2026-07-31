import { api } from './client';
import type { Notification } from '../types/notification';

export const getUnreadCount = async (userId: number): Promise<number> => {
  const response = await api.get(`/notifications/user/${userId}/unread/count`);
  return response.data.data;
};

export const listNotifications = async (userId: number): Promise<Notification[]> => {
  const response = await api.get(`/notifications/user/${userId}`);
  return response.data.data;
};

export const markRead = async (id: number): Promise<void> => {
  await api.put(`/notifications/${id}/read`);
};
