export interface Project {
  id: number;
  key: string;
  name: string;
  description?: string;
  avatarUrl?: string;
  leadUserId?: number;
  active: boolean;
}
