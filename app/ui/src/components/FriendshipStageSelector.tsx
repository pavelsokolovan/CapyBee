import { FRIENDSHIP_STAGES, STAGE_META, type FriendshipStage } from '../constants/friendshipStages';

interface FriendshipStageSelectorProps {
  value: FriendshipStage;
  onChange: (stage: FriendshipStage) => void;
  getLabel: (stage: FriendshipStage) => string;
}

export function FriendshipStageSelector({ value, onChange, getLabel }: FriendshipStageSelectorProps) {
  const activeIndex = FRIENDSHIP_STAGES.indexOf(value);

  return (
    <div className="friendship-stage-selector" role="group" aria-label="Friendship stage">
      {FRIENDSHIP_STAGES.map((stage, index) => {
        const isActive = value === stage;
        const showConnector = index < FRIENDSHIP_STAGES.length - 1 && (isActive || index < activeIndex);

        return (
          <div key={stage} className="friendship-stage-selector__step">
            <button
              type="button"
              className={`friendship-stage-chip ${isActive ? 'active' : ''}`}
              aria-pressed={isActive}
              onClick={() => onChange(stage)}
              style={isActive ? { backgroundColor: STAGE_META[stage].colorToken, color: '#2e2413' } : undefined}
            >
              <span className="friendship-stage-chip__icon">{STAGE_META[stage].icon}</span>
              <span className="friendship-stage-chip__label">{getLabel(stage)}</span>
            </button>
            {showConnector ? (
              <span className={`friendship-stage-selector__connector ${index < activeIndex ? 'active' : ''}`} />
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
