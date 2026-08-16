export type MissionCategoryIconProps = { active?: boolean };

const strokeColor = (active?: boolean) => (active ? 'var(--accent-strong)' : 'var(--muted)');

export function CategoryAllIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path d="M12 3 L14 10 L21 12 L14 14 L12 21 L10 14 L3 12 L10 10 Z" fill={strokeColor(active)} />
    </svg>
  );
}

export function CategorySocialIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2.5" y="5" width="13" height="9.5" rx="4" />
      <rect x="8.5" y="10.5" width="13" height="9.5" rx="4" />
    </svg>
  );
}

export function CategoryNewWorldIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 21V11" />
      <path d="M12 12c0-4.5 3.2-6.8 6.5-6.8 0 4.5-2.3 6.8-6.5 6.8z" />
      <path d="M12 15.5c0-3.3-2.6-5.3-5.7-5.3 0 3.3 2.6 5.3 5.7 5.3z" />
    </svg>
  );
}

export function CategoryOldWorldIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 11L12 4L20 11" />
      <path d="M6 10V19H18V10" />
    </svg>
  );
}

export function CategoryReflectionIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="9" cy="10.5" r="1" fill={strokeColor(active)} stroke="none" />
      <circle cx="15" cy="10.5" r="1" fill={strokeColor(active)} stroke="none" />
      <path d="M8.3 14c1.2 1.6 6.2 1.6 7.4 0" />
    </svg>
  );
}

export function CategoryExploreIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="8.5" />
      <path d="M15.2 8.8L13 13L8.8 15.2L11 11Z" />
    </svg>
  );
}
