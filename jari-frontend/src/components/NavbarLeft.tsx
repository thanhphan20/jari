import { Plus } from '@phosphor-icons/react';
import { NotificationBell } from './NotificationBell';
import { ProfileMenu } from './ProfileMenu';
import type { User } from '../types/user';

// A slim icon rail, distinct from the collapsible project Sidebar. Kept to
// what actually does something today - the logo, create-issue shortcut, a
// real notification bell, and the signed-in user's own profile/sign-out -
// rather than a row of icons pointing at destinations (releases, reports,
// backlog) that don't exist yet in this backend.
interface Props {
  onCreateIssue: () => void;
  currentUser?: User;
  onSignedOut: () => void;
}

export const NavbarLeft: React.FC<Props> = ({ onCreateIssue, currentUser, onSignedOut }) => (
  <nav className="w-14 shrink-0 bg-[#0c2a52] flex flex-col items-center py-3 gap-2">
    <div className="w-9 h-9 rounded bg-blue-500 text-white flex items-center justify-center font-bold text-sm">
      J
    </div>
    <button
      onClick={onCreateIssue}
      title="Create issue"
      className="mt-4 w-9 h-9 rounded-full bg-white/10 text-white flex items-center justify-center hover:bg-white/20"
    >
      <Plus size={18} weight="bold" />
    </button>
    {currentUser && <NotificationBell userId={currentUser.id} />}
    {currentUser && <ProfileMenu user={currentUser} onSignedOut={onSignedOut} />}
  </nav>
);
