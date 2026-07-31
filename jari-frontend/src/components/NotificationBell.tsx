import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getUnreadCount, listNotifications, markRead } from '../api/notifications';

interface Props {
  userId: number;
}

export const NotificationBell: React.FC<Props> = ({ userId }) => {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();

  const { data: unreadCount } = useQuery({
    queryKey: ['notifications', 'unread-count', userId],
    queryFn: () => getUnreadCount(userId),
    refetchInterval: 30_000,
  });

  const { data: notifications } = useQuery({
    queryKey: ['notifications', userId],
    queryFn: () => listNotifications(userId),
    enabled: open,
  });

  const markReadMutation = useMutation({
    mutationFn: markRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="relative w-9 h-9 rounded-full bg-white/10 text-white flex items-center justify-center hover:bg-white/20"
        title="Notifications"
      >
        🔔
        {!!unreadCount && (
          <span className="absolute -top-0.5 -right-0.5 bg-red-500 text-white text-[10px] rounded-full min-w-[16px] h-4 flex items-center justify-center px-1">
            {unreadCount}
          </span>
        )}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="absolute left-12 top-0 w-80 bg-white rounded-lg shadow-xl border border-gray-200 z-40 max-h-96 overflow-y-auto">
            <div className="px-3 py-2 border-b border-gray-100 text-sm font-medium text-gray-700">Notifications</div>
            {notifications?.length ? (
              notifications.map((n) => (
                <button
                  key={n.id}
                  onClick={() => !n.read && markReadMutation.mutate(n.id)}
                  className={`w-full text-left px-3 py-2 border-b border-gray-50 hover:bg-gray-50 ${n.read ? 'opacity-60' : ''}`}
                >
                  <div className="text-sm font-medium text-gray-800">{n.title}</div>
                  <div className="text-xs text-gray-500">{n.message}</div>
                </button>
              ))
            ) : (
              <div className="px-3 py-4 text-sm text-gray-400 text-center">No notifications</div>
            )}
          </div>
        </>
      )}
    </div>
  );
};
