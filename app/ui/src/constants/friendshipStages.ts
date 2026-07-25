export type FriendshipStage = 'noticed' | 'was_nice' | 'talked' | 'want_to_know_better';

export const FRIENDSHIP_STAGES: FriendshipStage[] = [
  'noticed',
  'was_nice',
  'talked',
  'want_to_know_better'
];

export const STAGE_META: Record<FriendshipStage, {
  labelKey: string;
  icon: string;
  colorToken: string;
  avatarExpression: string;
}> = {
  noticed: {
    labelKey: 'friendship.stage.noticed',
    icon: '👀',
    colorToken: 'var(--cell-empty-active)',
    avatarExpression: 'curious'
  },
  was_nice: {
    labelKey: 'friendship.stage.wasNice',
    icon: '🙂',
    colorToken: 'var(--cell-checkin)',
    avatarExpression: 'warm-smile'
  },
  talked: {
    labelKey: 'friendship.stage.talked',
    icon: '💬',
    colorToken: 'var(--cell-new-world)',
    avatarExpression: 'excited'
  },
  want_to_know_better: {
    labelKey: 'friendship.stage.wantToKnowBetter',
    icon: '🤝',
    colorToken: 'var(--cell-new-world-deep)',
    avatarExpression: 'hopeful'
  }
};
