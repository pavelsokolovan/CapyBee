import type { HoneycombCellData } from '../hooks/useHoneycombCells';

interface HoneycombTooltipProps {
  cell: HoneycombCellData;
  icon: string;
  x: number;
  y: number;
}

export function HoneycombTooltip({ cell, icon, x, y }: HoneycombTooltipProps) {
  return (
    <div
      className="honeycomb-tooltip"
      role="status"
      aria-live="polite"
      style={{ left: `${x}px`, top: `${y}px` }}
    >
      {icon} {cell.title} {cell.date ? `· ${cell.date}` : ''}
      <div className="honeycomb-tooltip-arrow" />
    </div>
  );
}
