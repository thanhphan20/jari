import { useState } from 'react';
import type { KanbanBoard as KanbanBoardType, Task } from '../types/kanban';
import type { User } from '../types/user';
import { IssueTypeIcon } from './icons/IssueTypeIcon';
import { PriorityIcon } from './icons/PriorityIcon';
import { Avatar } from './Avatar';

export interface MoveArgs {
  taskId: number;
  targetStatus: string;
  targetIndex: number;
}

interface Props {
  board: KanbanBoardType | null;
  isLoading: boolean;
  usersById?: Map<number, User>;
  onSelectTask?: (task: Task) => void;
  onMove?: (args: MoveArgs) => void;
  // Disables drag entirely rather than translating indices between a
  // filtered view and the canonical (unfiltered) board a filter can hide
  // siblings from - see App.tsx's applyFilters. Reordering against a
  // partial view doesn't have an unambiguous meaning anyway.
  dragDisabled?: boolean;
}

// Computes the insertion index from pointer position by comparing against
// each existing card's vertical midpoint - the drop lands before the first
// card whose midpoint is below the pointer, or at the end otherwise. This
// index is relative to the rendered list, which still includes the dragged
// card itself (dimmed, not removed) - see the same-column adjustment in
// onDrop below for why that matters.
function dropIndexFor(container: HTMLElement, clientY: number): number {
  const cards = Array.from(container.querySelectorAll<HTMLElement>('[data-card]'));
  for (let i = 0; i < cards.length; i++) {
    const rect = cards[i].getBoundingClientRect();
    if (clientY < rect.top + rect.height / 2) return i;
  }
  return cards.length;
}

export const KanbanBoard: React.FC<Props> = ({ board, isLoading, usersById, onSelectTask, onMove, dragDisabled }) => {
  // Tracks where the drag started, not just which task - the column and
  // index at drag-start, so onDrop can correct for the array shift that
  // removing the dragged card causes when reordering within that same
  // column (see the comment at the adjustment below).
  const [dragSource, setDragSource] = useState<{ taskId: number; columnId: string; index: number } | null>(null);
  const [dragOver, setDragOver] = useState<{ columnId: string; index: number } | null>(null);

  if (isLoading) {
    return <div>Loading board...</div>;
  }

  if (!board) {
    return <div>No board data found.</div>;
  }

  if (board.columns.length === 0) {
    return <div>No columns configured for this board.</div>;
  }

  return (
    <div className="flex h-full gap-4 p-4 overflow-x-auto">
      {board.columns.map((column) => (
        <div
          key={column.id}
          className="shrink-0 w-80 bg-gray-100 rounded-lg flex flex-col"
          onDragOver={(e) => {
            if (dragSource === null) return;
            e.preventDefault();
            setDragOver({ columnId: column.id, index: dropIndexFor(e.currentTarget, e.clientY) });
          }}
          onDragLeave={(e) => {
            // Only clear when leaving the column entirely, not when moving
            // between its child cards (which also fire dragleave).
            if (!e.currentTarget.contains(e.relatedTarget as Node)) setDragOver(null);
          }}
          onDrop={(e) => {
            e.preventDefault();
            if (dragSource !== null && dragOver?.columnId === column.id) {
              let targetIndex = dragOver.index;
              // Reordering within the same column: dropIndexFor's result is
              // computed against the rendered list, which still includes the
              // dragged card at its original slot. Removing it (which the
              // move always does, before reinserting) shifts every later
              // index back by one - inserting at the raw computed index would
              // then land one slot too late. Example: [A,B,C], drag A to
              // "before C" computes index 2; without this adjustment,
              // removing A first gives [B,C] and inserting at 2 appends
              // (-> [B,C,A]) instead of landing between B and C (-> [B,A,C]).
              // Moving to a different column needs no adjustment, since
              // removing from column X doesn't shift column Y's indices.
              if (dragSource.columnId === column.id && dragSource.index < targetIndex) {
                targetIndex -= 1;
              }
              onMove?.({ taskId: dragSource.taskId, targetStatus: column.id, targetIndex });
            }
            setDragSource(null);
            setDragOver(null);
          }}
        >
          <div className="p-3 font-semibold text-gray-700 border-b border-gray-200">
            {column.title} <span className="text-sm text-gray-400 ml-2">{column.tasks.length}</span>
          </div>
          <div className="flex-1 p-2 space-y-2 overflow-y-auto min-h-[100px]">
            {column.tasks.length === 0 && dragOver?.columnId !== column.id ? (
              <div className="text-sm text-gray-400 italic px-1">No tasks</div>
            ) : (
              column.tasks.map((task, index) => (
                <div key={task.id}>
                  {dragOver?.columnId === column.id && dragOver.index === index && (
                    <div className="h-0.5 bg-blue-500 rounded my-1" />
                  )}
                  <div
                    data-card
                    draggable={!dragDisabled}
                    tabIndex={0}
                    role="button"
                    aria-label={`Open ${task.key}: ${task.summary}`}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onSelectTask?.(task);
                      }
                    }}
                    onDragStart={() => setDragSource({ taskId: task.id, columnId: column.id, index })}
                    onDragEnd={() => {
                      setDragSource(null);
                      setDragOver(null);
                    }}
                    onClick={() => onSelectTask?.(task)}
                    className="p-3 bg-white rounded shadow-sm border border-gray-200 cursor-pointer hover:shadow-md transition-shadow focus:outline-none focus:ring-2 focus:ring-blue-400"
                    style={{ opacity: dragSource?.taskId === task.id ? 0.4 : 1 }}
                  >
                    <div className="flex items-start gap-1.5 text-sm text-gray-800 font-medium mb-2">
                      <IssueTypeIcon type={task.type} className="mt-0.5 shrink-0" />
                      <span>{task.summary}</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <div className="flex items-center gap-2">
                        <span className="text-xs bg-gray-100 px-2 py-1 rounded text-gray-600">{task.key}</span>
                        <PriorityIcon priority={task.priority} />
                      </div>
                      <Avatar user={task.assigneeId ? usersById?.get(task.assigneeId) : undefined} size={22} />
                    </div>
                  </div>
                </div>
              ))
            )}
            {dragOver?.columnId === column.id && dragOver.index === column.tasks.length && (
              <div className="h-0.5 bg-blue-500 rounded my-1" />
            )}
          </div>
        </div>
      ))}
    </div>
  );
};
