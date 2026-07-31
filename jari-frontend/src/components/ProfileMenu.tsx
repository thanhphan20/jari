import { clearToken } from '../api/client';
import { Avatar } from './Avatar';
import { Dropdown } from './Dropdown';
import type { User } from '../types/user';

interface Props {
  user: User;
  onSignedOut: () => void;
}

export const ProfileMenu: React.FC<Props> = ({ user, onSignedOut }) => (
  <div className="mt-auto">
    <Dropdown
      triggerTitle={user.username}
      trigger={<Avatar user={user} size={32} />}
      menuClassName="left-12 bottom-0 w-48"
    >
      {() => (
        <>
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
        </>
      )}
    </Dropdown>
  </div>
);
