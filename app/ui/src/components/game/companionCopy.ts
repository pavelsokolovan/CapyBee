export const companionCopy = {
  en: {
    current: 'Who is here', party: 'Along for the adventure', history: 'Characters', trait: 'Trait', need: 'Needs',
    helped: 'Helped with', joined: 'Joined this adventure', here: 'In this scene',
    empty: 'You can explore quietly, too. No companions have joined this story yet.',
    added: (names: string) => `${names} joined the adventure.`,
  },
  pl: {
    current: 'Kto jest tutaj', party: 'Towarzysze przygody', history: 'Postacie', trait: 'Cecha', need: 'Potrzeba',
    helped: 'Wspólna pomoc', joined: 'W tej przygodzie jest z tobą', here: 'W tej scenie',
    empty: 'Możesz też spokojnie odkrywać świat samodzielnie. W tej historii nikt jeszcze nie dołączył.',
    added: (names: string) => `${names} — od teraz razem w tej przygodzie.`,
  },
} as const;
