export type NavIconProps = { active?: boolean };

const strokeColor = (active?: boolean) => (active ? 'var(--accent-strong)' : 'var(--muted)');

export function HomeIcon({ active }: NavIconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="22"
      height="22"
      fill="none"
      stroke={strokeColor(active)}
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 2l7 4v6c0 5-3 8-7 10-4-2-7-5-7-10V6z" />
    </svg>
  );
}

export function MissionsIcon({ active }: NavIconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="22"
      height="22"
      fill="none"
      stroke={strokeColor(active)}
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M9 11l3 3 8-8" />
      <path d="M20 12v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h9" />
    </svg>
  );
}

export function FriendshipsIcon({ active }: NavIconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="22"
      height="22"
      fill="none"
      stroke={strokeColor(active)}
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M4 17c2-2 5-2 6 0M4 17v2M10 17v2" />
      <path d="M14 12c2-2 5-2 6 0M14 12v2M20 12v2" />
    </svg>
  );
}

export function MemoriesIcon({ active }: NavIconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="22"
      height="22"
      fill="none"
      stroke={strokeColor(active)}
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 2l2.6 6.3L21 9l-5 4.4L17.5 21 12 17.3 6.5 21 8 13.4 3 9l6.4-.7z" />
    </svg>
  );
}

export function ProfileIcon({ active }: NavIconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="22"
      height="22"
      fill="none"
      stroke={strokeColor(active)}
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c1.8-3.5 4.1-5 7-5s5.2 1.5 7 5" />
    </svg>
  );
}
