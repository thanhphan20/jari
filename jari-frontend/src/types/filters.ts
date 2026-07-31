export interface Filters {
  text: string;
  assigneeId: number | null;
  type: number | null;
  onlyMine: boolean;
}

export const EMPTY_FILTERS: Filters = { text: '', assigneeId: null, type: null, onlyMine: false };

export function isActive(f: Filters): boolean {
  return f.text !== '' || f.assigneeId !== null || f.type !== null || f.onlyMine;
}
