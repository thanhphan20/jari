// Task.priority: 1=Lowest, 2=Low, 3=Medium, 4=High, 5=Highest (see Task.java).
// Arrow direction + count encodes magnitude, colour encodes urgency - the two
// together are readable at card size without a label.
import { PRIORITY_META } from '../../types/kanban';

function Arrows({ priority }: { priority: number }) {
  const up = priority >= 4;
  const double = priority === 1 || priority === 5;

  if (priority === 3) {
    // Medium: an equals sign - neither up nor down.
    return (
      <>
        <line x1="4" y1="6.5" x2="12" y2="6.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
        <line x1="4" y1="9.5" x2="12" y2="9.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </>
    );
  }

  const chevron = (yOffset: number) => (
    <polyline
      points={up ? `4,${9 + yOffset} 8,${5 + yOffset} 12,${9 + yOffset}` : `4,${5 + yOffset} 8,${9 + yOffset} 12,${5 + yOffset}`}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  );

  return double ? (
    <>
      {chevron(up ? -1.5 : 1.5)}
      {chevron(up ? 2 : -2)}
    </>
  ) : (
    chevron(0)
  );
}

interface Props {
  priority: number;
  className?: string;
}

export const PriorityIcon: React.FC<Props> = ({ priority, className }) => {
  const meta = PRIORITY_META[priority] ?? PRIORITY_META[3];
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      className={className}
      style={{ color: meta.color }}
      aria-label={meta.label}
    >
      <Arrows priority={priority} />
    </svg>
  );
};
