/** Shell copy only; never translate accepted story content or expose raw provider messages. */
export const gameCopy = {
  en: { unsupported: 'This adventure needs an update. Try again after refreshing.', offline: 'Connect to the internet to continue.',
    unknown: 'We are checking whether your adventure was saved.', unavailable: 'Adventures are unavailable right now.',
    signIn: 'Sign in again to continue.', changed: 'Your adventure has changed. Check the saved game.' },
  pl: { unsupported: 'Ta przygoda wymaga aktualizacji. Odśwież i spróbuj ponownie.', offline: 'Połącz się z internetem, aby kontynuować.',
    unknown: 'Sprawdzamy, czy twoja przygoda została zapisana.', unavailable: 'Przygody są teraz niedostępne.',
    signIn: 'Zaloguj się ponownie, aby kontynuować.', changed: 'Twoja przygoda się zmieniła. Sprawdź zapisaną grę.' }
} as const;
