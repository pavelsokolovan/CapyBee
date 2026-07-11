import type { CSSProperties } from 'react';
import capybeeCelebrating from './assets/avatars/capybee-celebrating_resize.png';
import capybeeEmpathetic from './assets/avatars/capybee-empathetic_resize.png';
import capybeeFaceHappy from './assets/avatars/capybee-face-happy_resize.png';
import capybeeFaceOkay from './assets/avatars/capybee-face-okay_resize.png';
import capybeeFaceSad from './assets/avatars/capybee-face-sad_resize.png';
import capybeeMoodGood from './assets/avatars/capybee-mood-good_resize.png';
import capybeeMoodHeavy from './assets/avatars/capybee-mood-heavy_resize.png';
import capybeeMoodOkay from './assets/avatars/capybee-mood-okay_resize.png';
import capybeeSuggesting from './assets/avatars/capybee-suggesting_resize.png';
import capybeeWaving from './assets/avatars/capybee-waving_resize.png';
import capybeeDefault from './assets/avatars/capybee_main_resize.png';

export const capyBeeAvatar = {
  default: capybeeDefault,
  waving: capybeeWaving,
  empathetic: capybeeEmpathetic,
  celebrating: capybeeCelebrating,
  suggesting: capybeeSuggesting,
  faceHappy: capybeeFaceHappy,
  faceOkay: capybeeFaceOkay,
  faceSad: capybeeFaceSad,
  moodHeavy: capybeeMoodHeavy,
  moodOkay: capybeeMoodOkay,
  moodGood: capybeeMoodGood
} as const;

export type SupportedLocale = 'en' | 'pl';

export function CapyBeeAvatar({
  src,
  size,
  alt = '',
  className,
  style
}: {
  src: string;
  size: number;
  alt?: string;
  className?: string;
  style?: CSSProperties;
}) {
  return (
    <img
      src={src}
      alt={alt}
      draggable="false"
      className={className ? `capybee-avatar ${className}` : 'capybee-avatar'}
      style={{ width: size, height: size, ...style }}
    />
  );
}

export function CapyBeeBubble({ text }: { text: string }) {
  return <p className="capybee-bubble">{text}</p>;
}

export function sameCalendarDay(a: Date, b: Date) {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}
