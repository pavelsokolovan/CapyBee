import { useCallback, useRef } from 'react';
import { capybeePhrases, type CapyBeePhrasePoolKey } from '../data/capybeePhrases';

type Locale = 'en' | 'pl';

export function useCapyBeePhrase(locale: Locale = 'en') {
  const lastUsedIndexByPool = useRef<Partial<Record<CapyBeePhrasePoolKey, number>>>({});

  return useCallback(
    (poolKey: CapyBeePhrasePoolKey) => {
      const pool = capybeePhrases[poolKey];
      if (!pool || pool.length === 0) return '';

      let index = 0;
      do {
        index = Math.floor(Math.random() * pool.length);
      } while (pool.length > 1 && index === lastUsedIndexByPool.current[poolKey]);

      lastUsedIndexByPool.current[poolKey] = index;
      return pool[index][locale] ?? pool[index].en;
    },
    [locale]
  );
}
