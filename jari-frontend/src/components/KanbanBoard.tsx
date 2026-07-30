import React from 'react';
import type { KanbanBoard as KanbanBoardType } from '../types/kanban';

interface Props {
  board: KanbanBoardType | null;
  isLoading: boolean;
}

export const KanbanBoard: React.FC<Props> = ({ board, isLoading }) => {
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
        <div key={column.id} className="shrink-0 w-80 bg-gray-100 rounded-lg flex flex-col">
          <div className="p-3 font-semibold text-gray-700 border-b border-gray-200">
            {column.title} <span className="text-sm text-gray-400 ml-2">{column.tasks.length}</span>
          </div>
          <div className="flex-1 p-2 space-y-2 overflow-y-auto min-h-[100px]">
            {column.tasks.length === 0 ? (
              <div className="text-sm text-gray-400 italic px-1">No tasks</div>
            ) : (
              column.tasks.map((task) => (
                <div key={task.id} className="p-3 bg-white rounded shadow-sm border border-gray-200 cursor-pointer hover:shadow-md transition-shadow">
                  <div className="text-sm text-gray-800 font-medium mb-1">{task.summary}</div>
                  <div className="flex justify-between items-center mt-2">
                     <span className="text-xs bg-gray-100 px-2 py-1 rounded text-gray-600">{task.key}</span>
                     {/* Priority icon could go here */}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      ))}
    </div>
  );
};
