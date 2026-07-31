export interface Notification {
  id: number;
  userId: number;
  title: string;
  message: string;
  type?: string;
  read: boolean;
  link?: string;
}
