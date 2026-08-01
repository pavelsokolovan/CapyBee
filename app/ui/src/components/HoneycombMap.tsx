import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { HoneycombTooltip } from './HoneycombTooltip';
import type { HoneycombCellData } from '../hooks/useHoneycombCells';
import capybeeFaceHappy from '../assets/avatars/capybee-face-happy_resize.png';
import capybeeFaceOkay from '../assets/avatars/capybee-face-okay_resize.png';
import capybeeFaceSad from '../assets/avatars/capybee-face-sad_resize.png';

// ─── Grid constants ─────────────────────────────────────────────────────────
const COLUMNS = 6;
const CELL_W = 64;
const CELL_H = 72;
const COL_STEP = 68;          // distance between cell centres horizontally
const ROW_STEP = 54;          // distance between row centres vertically
const ODD_ROW_OFFSET = 34;    // brick offset for odd rows
const POLYGON_POINTS = '32,2 62,18 62,54 32,70 2,54 2,18';

// col 5 on an odd row: x = 5*68 + 34 = 374, right edge = 374 + 64 = 438, +4 stroke padding
const MAP_WIDTH = 442;

// How many rows are visible without scrolling
const VISIBLE_ROWS = 3;
// Viewbox height for VISIBLE_ROWS
const VISIBLE_VB_HEIGHT = VISIBLE_ROWS * ROW_STEP + (CELL_H - ROW_STEP) + 4; // 184

// Initial extra empty rows shown below the last filled cell
const INITIAL_EMPTY_ROWS = 2;
// Extra empty rows appended each time the sentinel is reached
const LOAD_MORE_ROWS = 3;

// ─── Helpers ────────────────────────────────────────────────────────────────
function emptyCell(index: number): HoneycombCellData {
  return {
    id: `__empty_${index}`,
    type: 'empty',
    world: null,
    title: '',
    date: '',
    empty: true,
    timestamp: 0
  };
}

function getCellColors(cell: HoneycombCellData) {
  if (cell.empty) return { fill: '#EDE8DF', stroke: '#C4B99A', iconColor: null, dashed: true };
  if (cell.type === 'checkin') return { fill: '#F5C842', stroke: '#C8952A', iconColor: '#3d2b00', dashed: false };
  if (cell.world === 'old_world') return { fill: '#EDD28A', stroke: '#B8922A', iconColor: '#3d2b00', dashed: false };
  if (cell.world === 'new_world') return { fill: '#A8D5A2', stroke: '#5A9B52', iconColor: '#1a4718', dashed: false };
  return { fill: '#F5C842', stroke: '#C8952A', iconColor: '#3d2b00', dashed: false };
}

function getCellIcon(type: HoneycombCellData['type']): string {
  const icons: Record<string, string> = {
    mission: '⭐',
    memory: '📷',
    checkin: '',     // check-ins use capybee face image instead
    friendship: '🫶'
  };
  return icons[type] ?? '';
}

/** Returns the capybee face image URL for a check-in based on mood (stored in title). */
function getCheckInFace(mood: string): string {
  if (mood === 'heavy') return capybeeFaceSad;
  if (mood === 'okay') return capybeeFaceOkay;
  return capybeeFaceHappy;
}

function isDesktopHoverCapable(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
  return window.matchMedia('(hover: hover) and (pointer: fine)').matches;
}

// ─── Component ──────────────────────────────────────────────────────────────
interface HoneycombMapProps {
  /** Filled cells only, sorted oldest-first. Empty padding is managed internally. */
  cells: HoneycombCellData[];
  onCellTap?: (cell: HoneycombCellData) => void;
  ariaLabel: string;
  animatedCellId?: string | null;
}

export function HoneycombMap({
  cells,
  onCellTap,
  ariaLabel,
  animatedCellId = null
}: HoneycombMapProps) {
  const patternId = useId().replace(/:/g, '_');

  // Shell wraps everything; tooltip is positioned relative to it
  const shellRef = useRef<HTMLDivElement>(null);
  // Scroll container for vertical scrolling
  const scrollRef = useRef<HTMLDivElement>(null);
  // Sentinel div at the bottom triggers loading more empty rows
  const sentinelRef = useRef<HTMLDivElement>(null);

  const hideTimerRef = useRef<number | null>(null);
  const animTimerRef = useRef<number | null>(null);
  const animFrameRef = useRef<number | null>(null);
  const scrollFrameRef = useRef<number | null>(null);
  // Guard: don't trigger load-more while we are processing a previous add
  const loadingMoreRef = useRef(false);

  const [containerWidth, setContainerWidth] = useState(0);
  const [extraEmptyRows, setExtraEmptyRows] = useState(INITIAL_EMPTY_ROWS);
  const [tooltip, setTooltip] = useState<{ cell: HoneycombCellData; x: number; y: number } | null>(null);
  const [animState, setAnimState] = useState<{ id: string; phase: 'start' | 'active' } | null>(null);

  // ─── Build full cell array (filled + empty padding) ──────────────────────
  const allCells = useMemo(() => {
    const filled = cells.length;
    // pad to complete the current partial row
    const colsInLastRow = filled % COLUMNS;
    const padRow = colsInLastRow === 0 ? 0 : COLUMNS - colsInLastRow;
    // always have INITIAL_EMPTY_ROWS + any extra loaded rows of empty cells after padding
    const totalEmpty = padRow + (INITIAL_EMPTY_ROWS + Math.max(0, extraEmptyRows - INITIAL_EMPTY_ROWS)) * COLUMNS;
    const empties = Array.from({ length: Math.max(totalEmpty, INITIAL_EMPTY_ROWS * COLUMNS) }, (_, i) => emptyCell(i));
    return [...cells, ...empties];
  }, [cells, extraEmptyRows]);

  // ─── Cell positions (fixed COLUMNS, no responsive reduction) ─────────────
  const positions = useMemo(() =>
    allCells.map((cell, index) => {
      const col = index % COLUMNS;
      const row = Math.floor(index / COLUMNS);
      const x = col * COL_STEP + (row % 2 === 1 ? ODD_ROW_OFFSET : 0);
      const y = row * ROW_STEP;
      return { cell, x, y };
    }),
  [allCells]);

  // ─── SVG viewBox dimensions ────────────────────────────────────────────
  const mapHeight = useMemo(() => {
    const totalRows = Math.ceil(allCells.length / COLUMNS);
    return totalRows * ROW_STEP + (CELL_H - ROW_STEP) + 4;
  }, [allCells.length]);

  // ─── Scale factor (derived from actual container width) ─────────────────
  const scale = containerWidth > 0 ? containerWidth / MAP_WIDTH : 1;
  // Pixel height of the SVG = viewBox height × scale
  const svgHeightPx = Math.round(mapHeight * scale);
  // Fixed visible window height = exactly VISIBLE_ROWS rows in pixel space
  const visibleHeightPx = Math.round(VISIBLE_VB_HEIGHT * scale);

  // ─── ResizeObserver: track container width ────────────────────────────
  useEffect(() => {
    const el = shellRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      setContainerWidth(entries[0].contentRect.width);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // ─── Auto-scroll to bottom: on mount (if cells exist) and when new cells arrive
  useEffect(() => {
    const scroll = scrollRef.current;
    if (!scroll || cells.length === 0 || scale <= 0) return; // wait for scale to be measured

    // Calculate the row of the last filled cell
    const lastCellIndex = cells.length - 1;
    const lastRowIndex = Math.floor(lastCellIndex / COLUMNS);
    const lastRowY = lastRowIndex * ROW_STEP;

    // Position the last row near the bottom of the viewport with some margin
    // Keep about 0.5-1 row of space below for context
    const targetViewboxY = Math.max(0, lastRowY - (VISIBLE_VB_HEIGHT - ROW_STEP * 1.5));
    const targetScrollTop = targetViewboxY * scale;

    // Use a dedicated RAF ref so the animation effect can't cancel this scroll
    if (scrollFrameRef.current) window.cancelAnimationFrame(scrollFrameRef.current);
    scrollFrameRef.current = window.requestAnimationFrame(() => {
      scrollFrameRef.current = window.requestAnimationFrame(() => {
        scroll.scrollTop = targetScrollTop;
        scrollFrameRef.current = null;
      });
    });
  }, [cells.length, scale]);

  // ─── IntersectionObserver: sentinel triggers loading more empty rows ────
  useEffect(() => {
    const sentinel = sentinelRef.current;
    const root = scrollRef.current;
    if (!sentinel || !root) return;

    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !loadingMoreRef.current) {
          loadingMoreRef.current = true;
          setExtraEmptyRows((prev) => prev + LOAD_MORE_ROWS);
          // allow next trigger after a short delay
          setTimeout(() => { loadingMoreRef.current = false; }, 400);
        }
      },
      { root, threshold: 0.1 }
    );
    io.observe(sentinel);
    return () => io.disconnect();
  }, [containerWidth]); // re-attach after first width measurement so root is ready

  // ─── Animation effect ──────────────────────────────────────────────────
  useEffect(() => {
    if (!animatedCellId || !cells.some((c) => c.id === animatedCellId)) {
      setAnimState(null);
      return;
    }
    if (animTimerRef.current) window.clearTimeout(animTimerRef.current);
    if (animFrameRef.current) window.cancelAnimationFrame(animFrameRef.current);

    setAnimState({ id: animatedCellId, phase: 'start' });
    animFrameRef.current = window.requestAnimationFrame(() => {
      setAnimState({ id: animatedCellId, phase: 'active' });
    });
    animTimerRef.current = window.setTimeout(() => setAnimState(null), 700);
  }, [animatedCellId, cells]);

  // ─── Dismiss tooltip on outside tap ────────────────────────────────────
  useEffect(() => {
    const onPointerDown = (e: PointerEvent) => {
      if (!shellRef.current?.contains(e.target as Node)) setTooltip(null);
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, []);

  // ─── Cleanup timers ───────────────────────────────────────────────────
  useEffect(() => () => {
    if (hideTimerRef.current) window.clearTimeout(hideTimerRef.current);
    if (animTimerRef.current) window.clearTimeout(animTimerRef.current);
    if (animFrameRef.current) window.cancelAnimationFrame(animFrameRef.current);
    if (scrollFrameRef.current) window.cancelAnimationFrame(scrollFrameRef.current);
  }, []);

  // ─── Tooltip positioning (relative to shell, accounts for scroll offset) ──
  const showTooltipAt = (
    cell: HoneycombCellData,
    target: EventTarget & SVGGraphicsElement,
    autoHide: boolean
  ) => {
    if (cell.empty || !shellRef.current) return;
    const cellRect = target.getBoundingClientRect();
    const shellRect = shellRef.current.getBoundingClientRect();
    const x = cellRect.left - shellRect.left + cellRect.width / 2;
    const y = cellRect.top - shellRect.top;
    if (hideTimerRef.current) window.clearTimeout(hideTimerRef.current);
    setTooltip({ cell, x, y });
    onCellTap?.(cell);
    if (autoHide) {
      hideTimerRef.current = window.setTimeout(() => {
        setTooltip((cur) => (cur?.cell.id === cell.id ? null : cur));
      }, 3000);
    }
  };

  // ───────────────────────────────────────────────────────────────────────
  return (
    <div className="honeycomb-map-shell" ref={shellRef}>
      {/* Faint background tiling pattern — covers the visible scroll window */}
      <svg
        className="honeycomb-map-pattern"
        width="100%"
        height={containerWidth > 0 ? `${visibleHeightPx}px` : '100%'}
        aria-hidden="true"
      >
        <defs>
          <pattern id={patternId} x="0" y="0" width="68" height="78" patternUnits="userSpaceOnUse">
            <polygon points="34,2 64,18 64,52 34,68 4,52 4,18" fill="none" stroke="#C8952A" strokeWidth="1" />
            <polygon points="68,41 98,57 98,91 68,107 38,91 38,57" fill="none" stroke="#C8952A" strokeWidth="1" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill={`url(#${patternId})`} />
      </svg>

      {/* Scrollable cell grid */}
      <div
        ref={scrollRef}
        className="honeycomb-scroll-container"
        style={containerWidth > 0 ? { height: `${visibleHeightPx}px` } : undefined}
      >
        <svg
          width="100%"
          style={{
            height: containerWidth > 0 ? `${svgHeightPx}px` : 'auto',
            display: 'block'
          }}
          viewBox={`0 0 ${MAP_WIDTH} ${mapHeight}`}
          preserveAspectRatio="xMidYMin meet"
          role="img"
          aria-label={ariaLabel}
        >
          {positions.map(({ cell, x, y }) => {
            const colors = getCellColors(cell);
            const icon = getCellIcon(cell.type);
            const shouldAnimate = animState?.id === cell.id;
            const phase = shouldAnimate ? animState?.phase : null;

            const currentFill  = shouldAnimate && phase !== 'active' ? '#EDE8DF' : colors.fill;
            const currentStroke = shouldAnimate && phase !== 'active' ? '#C4B99A' : colors.stroke;
            const dashArray = (shouldAnimate ? phase !== 'active' : colors.dashed) ? '5 3' : undefined;

            const polyClass = [
              'cell-polygon',
              shouldAnimate ? 'cell-polygon-animate' : '',
              phase === 'active' ? 'cell-polygon-animate-active' : ''
            ].filter(Boolean).join(' ');

            const textClass = [
              'cell-icon',
              shouldAnimate ? 'cell-icon-animate' : '',
              phase === 'active' ? 'cell-icon-animate-active' : ''
            ].filter(Boolean).join(' ');

            return (
              <g
                key={cell.id}
                transform={`translate(${x}, ${y})`}
                className={cell.empty ? 'honeycomb-cell-group empty' : 'honeycomb-cell-group'}
                onClick={(e) => showTooltipAt(cell, e.currentTarget, true)}
                onMouseEnter={(e) => { if (isDesktopHoverCapable()) showTooltipAt(cell, e.currentTarget, false); }}
                onMouseLeave={() => { if (isDesktopHoverCapable()) setTooltip(null); }}
              >
                <polygon
                  points={POLYGON_POINTS}
                  className={polyClass}
                  fill={currentFill}
                  stroke={currentStroke}
                  strokeWidth={colors.dashed ? 1.5 : 2}
                  strokeDasharray={dashArray}
                />
                {!cell.empty && cell.type === 'checkin' ? (
                  <image
                    href={getCheckInFace(cell.title)}
                    x="10" y="14"
                    width="44" height="44"
                    className={textClass}
                  />
                ) : !cell.empty && icon ? (
                  <text
                    x="32" y="42"
                    textAnchor="middle"
                    fontSize="22"
                    fill={colors.iconColor ?? '#3d2b00'}
                    className={textClass}
                  >
                    {icon}
                  </text>
                ) : null}
              </g>
            );
          })}
        </svg>

        {/* Sentinel: when scrolled into view, appends more empty rows */}
        <div ref={sentinelRef} className="honeycomb-sentinel" aria-hidden="true" />
      </div>

      {/* Tooltip rendered outside scroll container so it doesn't clip */}
      {tooltip ? (
        <HoneycombTooltip
          cell={tooltip.cell}
          icon={getCellIcon(tooltip.cell.type)}
          x={tooltip.x}
          y={tooltip.y}
        />
      ) : null}
    </div>
  );
}
