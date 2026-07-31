import { useState } from 'react';

interface Props {
  trigger: React.ReactNode;
  triggerClassName?: string;
  triggerTitle?: string;
  menuClassName?: string;
  children: (close: () => void) => React.ReactNode;
}

export const Dropdown: React.FC<Props> = ({
  trigger,
  triggerClassName,
  triggerTitle,
  menuClassName = 'left-0 mt-1 min-w-[9rem]',
  children,
}) => {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative inline-block">
      <button type="button" onClick={() => setOpen((o) => !o)} className={triggerClassName} title={triggerTitle}>
        {trigger}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className={`absolute z-40 bg-white rounded-lg shadow-xl border border-gray-200 py-1 ${menuClassName}`}>
            {children(() => setOpen(false))}
          </div>
        </>
      )}
    </div>
  );
};

export function DropdownItem({
  onClick,
  active,
  children,
}: {
  onClick: () => void;
  active?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`w-full flex items-center gap-2 px-3 py-1.5 text-sm text-left whitespace-nowrap hover:bg-gray-50 ${
        active ? 'bg-blue-50 text-blue-700 font-medium' : 'text-gray-700'
      }`}
    >
      {children}
    </button>
  );
}
