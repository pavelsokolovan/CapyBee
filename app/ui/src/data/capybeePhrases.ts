export type CapyBeePhrasePoolKey =
  | 'moodHeavy'
  | 'moodOkay'
  | 'moodGood'
  | 'missionComplete'
  | 'missionSkip'
  | 'friendshipAdded'
  | 'memoryOldWorld'
  | 'memoryNewWorld';

export type CapyBeePhrase = {
  en: string;
  pl: string;
};

export const capybeePhrases: Record<CapyBeePhrasePoolKey, CapyBeePhrase[]> = {
  moodHeavy: [
    { en: "That sounds really hard. I'm here.", pl: 'To brzmi naprawde ciezko. Jestem tu.' },
    { en: 'Heavy days happen. You showed up anyway.', pl: 'Ciezkie dni sie zdarzaja. I tak tu jestes.' },
    { en: "I heard you. That's enough for today.", pl: 'Slysze cie. To wystarczy na dzis.' },
    { en: 'Missing things is not weakness. It just hurts.', pl: 'Tesknota to nie slabosc. Po prostu boli.' },
    { en: "Okay. Let's just sit with that for a moment.", pl: 'Okej. Usiadzmy z tym przez chwile.' },
    { en: "You don't have to fix it today.", pl: 'Nie musisz tego dzis naprawiac.' },
    { en: 'That took courage to write down.', pl: 'Odwaga - napisac to.' },
    { en: 'Heavy is honest. I respect that.', pl: 'Ciezko - to szczere. Szanuje to.' },
    { en: 'Even capybaras have bad days. 🍯', pl: 'Nawet kapibary maja gorsze dni. 🍯' },
    { en: 'Thanks for telling me. Really.', pl: 'Dzieki, ze mi powiedziales. Serio.' }
  ],
  moodOkay: [
    { en: 'Okay is something. Good job showing up.', pl: 'Okej to tez jest cos. Dobra robota, ze tu jestes.' },
    { en: "Solid okay. Honestly, that's fine.", pl: 'Solidne okej. Serio, to jest w porzadku.' },
    { en: 'Not every day has to be great. Okay works.', pl: 'Nie kazdy dzien musi byc super. Okej wystarczy.' },
    { en: 'Noted. Okay is a perfectly good day.', pl: 'Przyjeto. Okej to calkowicie dobry dzien.' },
    { en: 'Okay today. Maybe something small tomorrow.', pl: 'Okej dzis. Moze cos malego jutro.' },
    { en: "Middle of the road. I'll take it. 🍯", pl: 'Srodek drogi. Biore to. 🍯' },
    { en: 'You checked in. That alone counts.', pl: 'Zameldowales sie. To samo w sobie sie liczy.' },
    { en: 'Okay and honest beats fake good any day.', pl: 'Szczere okej bije udawane dobrze kazdego dnia.' },
    { en: 'Cool. No pressure to make it more than that.', pl: 'Spoko. Nie musisz robic z tego wiecej niz to.' },
    { en: 'Okay noted. See you tomorrow?', pl: 'Okej, przyjeto. Do jutra?' }
  ],
  moodGood: [
    { en: "That's great! I'm happy with you.", pl: 'To swietnie! Ciesze sie razem z toba.' },
    { en: 'Good! Add it to the hive. 🍯', pl: 'Dobrze! Dodajmy to do ula. 🍯' },
    { en: 'Something worked today. Remember that feeling.', pl: 'Cos dzis zadzialalo. Zapamietaj to uczucie.' },
    { en: 'Good days exist. This is proof.', pl: 'Dobre dni istnieja. Masz na to dowod.' },
    { en: 'Look at you, having a good one.', pl: 'No prosze, masz dobry dzien.' },
    { en: 'Storing this one in the hive. 🍯', pl: 'Zapisuje to w ulu. 🍯' },
    { en: 'Good! Tell me more sometime.', pl: 'Dobrze! Opowiedz mi o tym kiedys wiecej.' },
    { en: 'Something went right. That matters.', pl: 'Cos poszlo dobrze. To sie liczy.' },
    { en: 'Happy to hear it. Genuinely.', pl: 'Milo to slyszec. Naprawde.' },
    { en: 'Yes! Good days deserve to be noticed.', pl: 'Tak! Dobre dni zasluguja na uwage.' }
  ],
  missionComplete: [
    { en: 'Mission done! A new cell in your hive. 🍯', pl: 'Misja wykonana! Nowa komorka w ulu. 🍯' },
    { en: 'You actually did it. That\'s the whole point.', pl: 'Naprawde to zrobiles. O to wlasnie chodzi.' },
    { en: 'Small step. Real step. Big difference.', pl: 'Maly krok. Prawdziwy krok. Duza roznica.' },
    { en: "One more thing that's yours in this new place.", pl: 'Jeszcze jedna rzecz, ktora nalezy do ciebie w tym nowym miejscu.' },
    { en: "Hive is growing. Slowly, but it's growing.", pl: 'Ul rosnie. Powoli, ale rosnie.' },
    { en: "That was brave. Even if it didn't feel like it.", pl: 'To bylo odwazne. Nawet jesli tak nie czules.' },
    { en: 'Done! I saw that. It counts.', pl: 'Zrobione! Widzialem. Liczy sie.' },
    { en: 'You did something today that mattered.', pl: 'Dzis zrobiles cos, co ma znaczenie.' },
    { en: 'Look at the hive filling up. Look at that.', pl: 'Popatrz, jak ul sie zapelnia. No popatrz.' },
    { en: "Okay I'm a little proud of you right now.", pl: 'Wiesz co, jestem teraz z ciebie troche dumny.' }
  ],
  missionSkip: [
    { en: 'Okay. Not every day is a mission day.', pl: 'Okej. Nie kazdy dzien musi byc na misje.' },
    { en: "No worries - it'll be here tomorrow.", pl: 'Spoko, wroci innym razem.' },
    { en: "That's fine. You don't owe me this one.", pl: 'W porzadku. Nie musisz mi nic udowadniac.' },
    { en: 'Skipping counts as taking care of yourself too.', pl: 'Odpuszczenie sobie tez czasem znaczy dbac o siebie.' },
    { en: "Some days just aren't mission days. That's okay.", pl: 'Sa dni bez misji. To normalne.' },
    { en: "Noted. I'm not going anywhere.", pl: 'Zanotowane. Nigdzie sie nie wybieram.' },
    { en: 'Fair enough. Rest counts too.', pl: 'Jasne. Odpoczynek tez sie liczy.' },
    { en: "All good. We'll try again another day.", pl: 'Wszystko gra. Sprobujemy innym razem.' }
  ],
  friendshipAdded: [
    { en: 'Got it! Every step counts.', pl: 'Zapamietalem! Kazdy krok sie liczy.' },
    { en: 'You noticed someone. That\'s how it starts.', pl: 'Zauwazyles kogos. Tak to sie zaczyna.' },
    { en: 'A name in the hive. Good.', pl: 'Imie w ulu. Dobrze.' },
    { en: "Your people are out there. You're finding them.", pl: 'Twoja paczka gdzies tam jest. Wlasnie jej szukasz.' },
    { en: 'One person closer to finding your hive.', pl: 'O jedna osobe blizej do znalezienia swojego ula.' },
    { en: 'Saved. Small things matter more than they look.', pl: 'Zapisane. Male rzeczy maja wieksze znaczenie niz wygladaja.' },
    { en: 'Friendships start exactly like this.', pl: 'Przyjaznie zaczynaja sie dokladnie tak.' },
    { en: "I see it. I'm keeping track with you.", pl: 'Widze to. Sledze to razem z toba.' },
    { en: "You're paying attention. That's a skill.", pl: 'Zwracasz uwage. To jest umiejetnosc.' },
    { en: 'The hive knows. 🍯', pl: 'Ul wie. 🍯' }
  ],
  memoryOldWorld: [
    { en: 'Saved. This will always be yours.', pl: 'Zapisane. To zawsze bedzie twoje.' },
    { en: 'The old home stays here. Safe.', pl: 'Stary dom zostaje tutaj. Bezpieczny.' },
    { en: "It's okay to miss it. It was real and it mattered.", pl: 'Wolno ci za tym tesknic. To bylo prawdziwe i wazne.' },
    { en: 'Keeping it. You can come back anytime.', pl: 'Przechowuje to. Mozesz tu wracac kiedy chcesz.' },
    { en: 'That memory is sealed in the hive now. 🍯', pl: 'To wspomnienie jest teraz zapieczetowane w ulu. 🍯' },
    { en: "Old doesn't mean gone. It's right here.", pl: 'Stare nie znaczy znikniete. Jest tu.' },
    { en: "I'm glad you wrote that down.", pl: 'Ciesze sie, ze to zapisales.' },
    { en: "Home is more than one place. You're allowed that.", pl: 'Dom to wiecej niz jedno miejsce. Masz do tego prawo.' },
    { en: 'Stored safely. Nobody takes this from you.', pl: 'Przechowane bezpiecznie. Nikt ci tego nie zabierze.' },
    { en: "Even far away, it's still yours.", pl: 'Nawet daleko - wciaz twoje.' }
  ],
  memoryNewWorld: [
    { en: 'A new moment in the hive! 🍯', pl: 'Nowa chwila w ulu! 🍯' },
    { en: 'Look - something new is yours here now.', pl: 'Patrz - cos nowego nalezy tu teraz do ciebie.' },
    { en: 'The hive is getting real.', pl: 'Ul staje sie prawdziwy.' },
    { en: "One more piece of the new place that's actually yours.", pl: 'Jeszcze jeden kawalek nowego miejsca, ktory jest naprawde twoj.' },
    { en: 'New things can be good things. This is proof.', pl: 'Nowe rzeczy moga byc dobrymi rzeczami. Masz na to dowod.' },
    { en: 'Building it, one memory at a time.', pl: 'Budujesz to, jedno wspomnienie na raz.' },
    { en: "That's yours. Nobody can tell you otherwise.", pl: 'To twoje. Nikt ci nie powie inaczej.' },
    { en: 'The new hive has a new cell. I love that.', pl: 'Nowy ul ma nowa komorke. Uwielbiam to.' },
    { en: 'Somewhere, this place just got a little more yours.', pl: 'Gdzies, to miejsce stalo sie troche bardziej twoje.' },
    { en: 'Saved. The hive remembers. 🍯', pl: 'Zapisane. Ul pamieta. 🍯' }
  ]
};
