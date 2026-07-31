import type { User } from '../types/user';

// A handful of distinct, readable-on-white colours. Picked by user id modulo
// length, not randomly and not by array index in a filtered list, so the
// same person is always the same colour - what makes an avatar scannable
// across cards and across reloads.
const COLORS = ['#2563eb', '#16a34a', '#dc2626', '#9333ea', '#ea580c', '#0891b2', '#c026d3', '#65a30d'];

function initials(user: Pick<User, 'firstName' | 'lastName' | 'username'>): string {
  if (user.firstName && user.lastName) {
    return (user.firstName[0] + user.lastName[0]).toUpperCase();
  }
  return user.username.slice(0, 2).toUpperCase();
}

interface Props {
  user?: Pick<User, 'id' | 'username' | 'firstName' | 'lastName' | 'avatarUrl'> | null;
  size?: number;
}

export const Avatar: React.FC<Props> = ({ user, size = 24 }) => {
  if (!user) {
    return (
      <div
        className="rounded-full border border-dashed border-gray-300 shrink-0"
        style={{ width: size, height: size }}
        title="Unassigned"
      />
    );
  }

  if (user.avatarUrl) {
    return (
      <img
        src={user.avatarUrl}
        alt={user.username}
        title={user.username}
        className="rounded-full shrink-0 object-cover"
        style={{ width: size, height: size }}
      />
    );
  }

  const color = COLORS[user.id % COLORS.length];
  return (
    <div
      className="rounded-full shrink-0 flex items-center justify-center text-white font-medium"
      style={{ width: size, height: size, backgroundColor: color, fontSize: size * 0.4 }}
      title={user.username}
    >
      {initials(user)}
    </div>
  );
};
