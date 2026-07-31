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

// ProjectService.updateProject overwrites every field from the request body -
// it is not a merge. In particular `active` is a primitive boolean on the
// entity, so omitting it deserializes to false and silently deactivates the
// project. Callers must submit the full record (spread the current project
// and override only what changed), which is why this takes Project, not
// Partial<Project>.
export const updateProject = async (id: number, project: Project): Promise<Project> => {
  const response = await api.put(`/projects/${id}`, project);
  return response.data.data;
};
