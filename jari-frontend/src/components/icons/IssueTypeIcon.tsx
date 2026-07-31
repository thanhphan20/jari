// Task.type: 1=Task, 2=Bug, 3=Story, 4=Epic (see Task.java).
// Each is a coloured rounded square with a distinct glyph, so type is legible
// on a card without opening it - the whole point per design.md.
import { TYPE_META } from '../../types/kanban';

function Glyph({ type }: { type: number }) {
  switch (type) {
    case 2: // Bug: circle with legs
      return (
        <>
          <circle cx="8" cy="8.5" r="3" fill="white" />
          <line x1="8" y1="5" x2="8" y2="4" stroke="white" strokeWidth="1" />
          <line x1="5.5" y1="6.5" x2="4.3" y2="5.5" stroke="white" strokeWidth="1" />
          <line x1="10.5" y1="6.5" x2="11.7" y2="5.5" stroke="white" strokeWidth="1" />
          <line x1="5" y1="10" x2="3.8" y2="10.8" stroke="white" strokeWidth="1" />
          <line x1="11" y1="10" x2="12.2" y2="10.8" stroke="white" strokeWidth="1" />
        </>
      );
    case 3: // Story: bookmark
      return <path d="M5 4h6v9l-3-2-3 2z" fill="white" />;
    case 4: // Epic: bolt
      return <path d="M9 3.5 5 9h2.5l-1 4.5L11 8H8.5z" fill="white" />;
    default: // Task: checkmark
      return <path d="M4.5 8.2l2.2 2.2 4.8-4.8" stroke="white" strokeWidth="1.6" fill="none" strokeLinecap="round" strokeLinejoin="round" />;
  }
}

interface Props {
  type: number;
  className?: string;
}

export const IssueTypeIcon: React.FC<Props> = ({ type, className }) => {
  const meta = TYPE_META[type] ?? TYPE_META[1];
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" className={className} aria-label={meta.label}>
      <rect width="16" height="16" rx="3" fill={meta.color} />
      <Glyph type={type} />
    </svg>
  );
};
