import { motion } from 'framer-motion';

export interface HiveBuilderTapGameProps {
  locale: 'en' | 'pl';
  text: Record<string, string>;
  stage: 'intro' | 'playing' | 'complete';
  phase: 'showing-sequence' | 'waiting-for-input';
  sequence: string[];
  position: number;
  longestSequence: number;
  totalTapsAttempted: number;
  startedAt: number | null;
  hasGameHexToday: boolean;
  onClose: () => void;
  onStart: () => void;
  onFinish: () => void;
  onSave: () => void;
  onTileClick: (tileId: string) => void;
}

function HexTile({
  id,
  isCurrent,
  isLit,
  onClick
}: {
  id: string;
  isCurrent: boolean;
  isLit: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={['hive-builder-tile', isCurrent ? 'current' : '', isLit ? 'lit' : ''].filter(Boolean).join(' ')}
      onClick={onClick}
      aria-label={id}
      style={{
        transform: isLit ? 'scale(1.04)' : 'scale(1)',
        boxShadow: isLit ? '0 0 0 2px rgba(224, 154, 30, 0.7), 0 0 20px rgba(242, 178, 51, 0.42)' : undefined
      }}
    >
      <svg viewBox="0 0 84 94" aria-hidden="true" className="hive-builder-tile__svg">
        <polygon points="42,3 81,23 81,71 42,91 3,71 3,23" />
      </svg>
      <span className="hive-builder-tile__dot" aria-hidden="true">🐝</span>
    </button>
  );
}

export function HiveBuilderTapGame({
  locale,
  text,
  stage,
  phase,
  sequence,
  position,
  longestSequence,
  totalTapsAttempted,
  startedAt,
  hasGameHexToday,
  onClose,
  onStart,
  onFinish,
  onSave,
  onTileClick
}: HiveBuilderTapGameProps) {
  const displayLongest = Math.max(longestSequence, sequence.length);
  const durationSeconds = startedAt ? Math.max(1, Math.round((Date.now() - startedAt) / 1000)) : 1;
  const isSequencePlayback = phase === 'showing-sequence';

  if (stage === 'intro') {
    return (
      <div className="game-overlay" role="dialog" aria-modal="true" aria-label={text.hiveBuilderTapTitle ?? 'Hive Builder'}>
        <div className="game-panel">
          <button type="button" className="game-close-button" onClick={onClose} aria-label={text.close}>×</button>
          <div className="game-intro-panel">
            <CapyBeeAvatarInline />
            <h3>{text.hiveBuilderTapTitle}</h3>
            <p>{text.hiveBuilderTapIntro}</p>
            <button type="button" className="primary-button" onClick={onStart}>{text.hiveBuilderTapStart}</button>
          </div>
        </div>
      </div>
    );
  }

  if (stage === 'complete') {
    return (
      <div className="game-overlay" role="dialog" aria-modal="true" aria-label={text.hiveBuilderTapTitle ?? 'Hive Builder'}>
        <div className="game-panel">
          <button type="button" className="game-close-button" onClick={onClose} aria-label={text.close}>×</button>
          <div className="game-complete-panel">
            <CapyBeeAvatarInline />
            <h3>{(text.hiveBuilderTapDone ?? 'You got to {longestSequence}!').replace('{longestSequence}', String(displayLongest))}</h3>
            <p>
              {text.hiveBuilderTapRoundSummary}: {displayLongest} · {durationSeconds}s
            </p>

            {hasGameHexToday ? (
              <button type="button" className="primary-button" onClick={onClose}>{text.nice}</button>
            ) : (
              <>
                <button type="button" className="primary-button" onClick={onSave}>{text.addToHive}</button>
                <button type="button" className="secondary-button" onClick={onStart}>{text.playMore}</button>
                <button type="button" className="secondary-button" onClick={onClose}>{text.close}</button>
              </>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="game-overlay" role="dialog" aria-modal="true" aria-label={text.hiveBuilderTapTitle ?? 'Hive Builder'}>
      <div className="game-panel">
        <button type="button" className="game-close-button" onClick={onClose} aria-label={text.close}>×</button>
        <div className="game-playing-panel">
          <div className="game-header-row">
            <div>
              <h3>{text.hiveBuilderTapTitle}</h3>
              <p>{text.hiveBuilderTapRoundSummary}</p>
            </div>
            <span className="game-flip-count">{displayLongest}</span>
          </div>

          <div className="hive-builder-board" aria-label={text.hiveBuilderTapTitle}>
            {sequence.map((tileId, index) => (
              <HexTile
                key={tileId}
                id={tileId}
                isCurrent={index === position}
                isLit={isSequencePlayback ? index <= position : index < position}
                onClick={() => onTileClick(tileId)}
              />
            ))}
          </div>

          <div className="game-meta-row">
            <span>{locale === 'pl' ? 'Próby' : 'Attempts'}: {totalTapsAttempted}</span>
            <span>{locale === 'pl' ? 'Najdłużej' : 'Best'}: {displayLongest}</span>
            <span>{isSequencePlayback ? (locale === 'pl' ? 'Pokaż sekwencję' : 'Show sequence') : (locale === 'pl' ? 'Odtwórz sekwencję' : 'Repeat the sequence')}</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function CapyBeeAvatarInline() {
  return (
    <div className="capybee-avatar-inline" aria-hidden="true">
      <span>🐝</span>
    </div>
  );
}
