import { api } from './client';
import type { Project } from '../types/project';

export const listProjects = async (): Promise<Project[]> => {
  const response = await api.get('/projects');
  return response.data.data;
};

export const createProject = async (project: Pick<Project, 'key' | 'name' | 'description'>): Promise<Project> => {
  const response = await api.post('/projects', project);
  return response.data.data;
};

// Overwrites, not merges: omitting `active` deactivates the project. Send the
// full record - hence Project, not Partial<Project>.
export const updateProject = async (id: number, project: Project): Promise<Project> => {
  const response = await api.put(`/projects/${id}`, project);
  return response.data.data;
};
