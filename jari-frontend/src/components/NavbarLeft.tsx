// A slim icon rail, distinct from the collapsible project Sidebar. Kept to
// what actually does something today - the logo and a create-issue shortcut -
// rather than a row of icons pointing at destinations (releases, reports,
// backlog) that don't exist yet in this backend.
interface Props {
  onCreateIssue: () => void;
}

export const NavbarLeft: React.FC<Props> = ({ onCreateIssue }) => (
  <nav className="w-14 shrink-0 bg-[#0c2a52] flex flex-col items-center py-3 gap-2">
    <div className="w-9 h-9 rounded bg-blue-500 text-white flex items-center justify-center font-bold text-sm">
      J
    </div>
    <button
      onClick={onCreateIssue}
      title="Create issue"
      className="mt-4 w-9 h-9 rounded-full bg-white/10 text-white flex items-center justify-center text-xl hover:bg-white/20"
    >
      +
    </button>
  </nav>
);
