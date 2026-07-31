import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listComments, addComment } from '../api/comments';
import { getCurrentUser } from '../api/users';
import type { User } from '../types/user';
import { Avatar } from './Avatar';

interface Props {
  taskId: number;
  usersById: Map<number, User>;
}

export const CommentThread: React.FC<Props> = ({ taskId, usersById }) => {
  const [body, setBody] = useState('');
  const queryClient = useQueryClient();
  const queryKey = ['comments', taskId];

  const { data: comments, isLoading } = useQuery({ queryKey, queryFn: () => listComments(taskId) });
  const { data: currentUser } = useQuery({ queryKey: ['me'], queryFn: getCurrentUser });

  const mutation = useMutation({
    mutationFn: (text: string) => addComment(taskId, text),
    onSuccess: () => {
      setBody('');
      queryClient.invalidateQueries({ queryKey });
    },
  });

  const submit = () => {
    const trimmed = body.trim();
    if (trimmed) mutation.mutate(trimmed);
  };

  return (
    <div className="pt-2">
      <div className="text-xs font-medium text-gray-500 mb-2">Comments</div>

      {isLoading ? (
        <div className="text-sm text-gray-400">Loading comments...</div>
      ) : (
        <div className="space-y-3 mb-3">
          {comments?.map((c) => (
            <div key={c.id} className="flex gap-2">
              <Avatar user={usersById.get(c.authorId)} size={24} />
              <div className="flex-1">
                <div className="flex items-baseline gap-2">
                  <span className="text-sm font-medium text-gray-800">
                    {usersById.get(c.authorId)?.username ?? `user #${c.authorId}`}
                  </span>
                  <span className="text-xs text-gray-400">{new Date(c.createdAt).toLocaleString()}</span>
                </div>
                <div className="text-sm text-gray-700 whitespace-pre-wrap">{c.body}</div>
              </div>
            </div>
          ))}
          {comments?.length === 0 && <div className="text-sm text-gray-400 italic">No comments yet.</div>}
        </div>
      )}

      <div className="flex gap-2 items-start">
        <Avatar user={currentUser} size={24} />
        <div className="flex-1">
          <textarea
            className="w-full text-sm border border-gray-200 rounded p-2 focus:outline-none focus:ring-1 focus:ring-blue-400"
            rows={2}
            placeholder="Add a comment..."
            value={body}
            onChange={(e) => setBody(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) submit();
            }}
          />
          <div className="flex justify-end mt-1">
            <button
              onClick={submit}
              disabled={!body.trim() || mutation.isPending}
              className="px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
            >
              {mutation.isPending ? 'Posting...' : 'Comment'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
