import { useCallback, useRef } from 'react';
import { friendshipAddedPhrases, type FriendshipStagePhraseKey } from '../data/friendshipAddedPhrases';

type Locale = 'en' | 'pl';

export function useFriendshipAddedPhrase(locale: Locale = 'en') {
  const lastUsedIndexByStage = useRef<Partial<Record<FriendshipStagePhraseKey, number>>>({});

  return useCallback((stage: FriendshipStagePhraseKey, name: string) => {
    const pool = friendshipAddedPhrases[stage][locale];
    if (!pool) return '';

    let index = 0;
    do {
      index = Math.floor(Math.random() * pool.length);
    } while (pool.length > 1 && index === lastUsedIndexByStage.current[stage]);

    lastUsedIndexByStage.current[stage] = index;
    return pool[index].replace('{name}', name);
  }, [locale]);
}
