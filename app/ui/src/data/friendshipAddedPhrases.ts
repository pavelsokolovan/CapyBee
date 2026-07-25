export const friendshipAddedPhrases = {
  noticed: {
    en: [
      '{name} noticed you — that matters!',
      'You noticed {name}. Small step, real step.',
      '{name} is on your radar now. Nice.'
    ],
    pl: [
      '{name} cię zauważył — to się liczy!',
      'Zauważyłeś {name}. Mały krok, ale prawdziwy.',
      '{name} już jest na twojej mapie. Super.'
    ]
  },
  was_nice: {
    en: [
      '{name} was nice to you. That\'s worth remembering.',
      'A kind moment with {name} — keep that one.',
      '{name} made things a little easier today.'
    ],
    pl: [
      '{name} był dla ciebie miły. Warto to zapamiętać.',
      'Miła chwila z {name} — zatrzymaj ją.',
      '{name} sprawił, że dziś było trochę łatwiej.'
    ]
  },
  talked: {
    en: [
      'You talked to {name}! That takes courage.',
      'A real conversation with {name}. Growing.',
      'You and {name} talked. That\'s a real connection starting.'
    ],
    pl: [
      'Porozmawiałeś z {name}! To wymaga odwagi.',
      'Prawdziwa rozmowa z {name}. Rośniesz.',
      'Ty i {name} porozmawialiście. To początek czegoś prawdziwego.'
    ]
  },
  want_to_know_better: {
    en: [
      'You want to know {name} better — your hive is building.',
      '{name} might be someone special. Let\'s see.',
      'That\'s a real friendship spark with {name}.'
    ],
    pl: [
      'Chcesz lepiej poznać {name} — twój ul rośnie.',
      '{name} może być kimś ważnym. Zobaczymy.',
      'To prawdziwa iskra przyjaźni z {name}.'
    ]
  }
} as const;

export type FriendshipStagePhraseKey = keyof typeof friendshipAddedPhrases;

export function getFriendshipAddedPhrase(stage: FriendshipStagePhraseKey, name: string, locale: 'en' | 'pl') {
  const pool = friendshipAddedPhrases[stage][locale];
  const raw = pool[Math.floor(Math.random() * pool.length)];
  return raw.replace('{name}', name);
}
