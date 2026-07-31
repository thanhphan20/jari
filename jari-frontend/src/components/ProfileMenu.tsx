import { useState } from 'react';
import { clearToken } from '../api/client';
import { Avatar } from './Avatar';
import type { User } from '../types/user';

interface Props {
  user: User;
  onSignedOut: () => void;
}

export const ProfileMenu: React.FC<Props> = ({ user, onSignedOut }) => {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative mt-auto">
      <button onClick={() => setOpen((o) => !o)} title={user.username}>
        <Avatar user={user} size={32} />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="absolute left-12 bottom-0 w-48 bg-white rounded-lg shadow-xl border border-gray-200 z-40 py-1">
            <div className="px-3 py-2 border-b border-gray-100">
              <div className="text-sm font-medium text-gray-800">{user.username}</div>
              <div className="text-xs text-gray-500 truncate">{user.email}</div>
            </div>
            <button
              onClick={() => {
                clearToken();
                onSignedOut();
              }}
              className="w-full text-left px-3 py-2 text-sm text-gray-600 hover:bg-gray-50"
            >
              Sign out
            </button>
          </div>
        </>
      )}
    </div>
  );
};
