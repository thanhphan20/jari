interface Props {
  projectName: string;
}

export const Breadcrumb: React.FC<Props> = ({ projectName }) => (
  <div className="text-sm text-gray-500 flex items-center gap-1.5">
    <span>Projects</span>
    <span>/</span>
    <span>{projectName}</span>
    <span>/</span>
    <span className="text-gray-900 font-medium">Kanban Board</span>
  </div>
);
