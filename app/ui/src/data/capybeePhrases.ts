export type CapyBeePhrasePoolKey =
  | 'homeGreeting'
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
  homeGreeting: [
    { en: 'Hey! How was today?', pl: 'Hej! Jak było dziś?' },
    { en: "Hi there! What's today been like?", pl: 'Cześć! Jak minął dzisiejszy dzień?' },
    { en: "Hey, good to see you! How's today going?", pl: 'Hej, dobrze cię widzieć! Jak leci dzisiaj?' },
    { en: "Hi! I've been buzzing around waiting for you.", pl: 'Cześć! Bzyczałem tu na ciebie.' },
    { en: "Hey you! How's today feeling?", pl: 'Hej, to ty! Jak się dziś czujesz?' },
    { en: 'Hi! Ready to check in?', pl: 'Cześć! Gotowy na check-in?' },
    { en: "Hey! Glad you're here. How was today?", pl: 'Hej! Fajnie, że jesteś. Jak było dziś?' },
    { en: "Hi! What's going on in your hive today?", pl: 'Cześć! Co słychać w twoim ulu dzisiaj?' },
    { en: "Hey, welcome back! How's today shaping up?", pl: 'Hej, witaj z powrotem! Jak wygląda dzisiaj?' },
    { en: "Hi! I saved you a spot. How's today?", pl: 'Cześć! Zostawiłem ci miejsce. Jak dziś?' }
  ],
  moodHeavy: [
    { en: "That sounds really hard. I'm here.", pl: 'To brzmi naprawdę ciężko. Jestem obok.' },
    { en: 'Heavy days happen. You showed up anyway.', pl: 'Ciężkie dni się zdarzają. A ty i tak się pojawiłeś.' },
    { en: "I heard you. That's enough for today.", pl: 'Słyszę cię. Na dziś to w zupełności wystarczy.' },
    { en: 'Missing things is not weakness. It just hurts.', pl: 'Tęsknota to nie słabość. To po prostu boli.' },
    { en: "Okay. Let's just sit with that for a moment.", pl: 'Dobrze. Posiedźmy z tym chwilę.' },
    { en: "You don't have to fix it today.", pl: 'Nie musisz tego dziś naprawiać.' },
    { en: 'That took courage to write down.', pl: 'Samo zapisanie tego wymagało odwagi.' },
    { en: 'Heavy is honest. I respect that.', pl: 'To, że jest ciężko, jest szczere. Szanuję to.' },
    { en: 'Even capybaras have bad days. 🍯', pl: 'Nawet kapibary mają gorsze dni. 🍯' },
    { en: 'Thanks for telling me. Really.', pl: 'Dzięki, że mi o tym powiedziałeś. Naprawdę.' }
  ],
  moodOkay: [
    { en: 'Okay is something. Good job showing up.', pl: 'To, że jest po prostu okej, też się liczy. Dobrze, że jesteś.' },
    { en: "Solid okay. Honestly, that's fine.", pl: 'Całkiem okej. I szczerze: to wystarczy.' },
    { en: 'Not every day has to be great. Okay works.', pl: 'Nie każdy dzień musi być świetny. Okej też jest w porządku.' },
    { en: 'Noted. Okay is a perfectly good day.', pl: 'Przyjęte. Okej to też dobry dzień.' },
    { en: 'Okay today. Maybe something small tomorrow.', pl: 'Dziś okej. Może jutro zrobimy mały krok.' },
    { en: "Middle of the road. I'll take it. 🍯", pl: 'Tak pośrodku. Biorę to. 🍯' },
    { en: 'You checked in. That alone counts.', pl: 'Zrobiłeś meldunek dnia. To samo w sobie się liczy.' },
    { en: 'Okay and honest beats fake good any day.', pl: 'Szczere okej zawsze wygrywa z udawanym super.' },
    { en: 'Cool. No pressure to make it more than that.', pl: 'Spokojnie. Nie musisz robić z tego nic więcej.' },
    { en: 'Okay noted. See you tomorrow?', pl: 'Okej, zapisane. Do jutra?' }
  ],
  moodGood: [
    { en: "That's great! I'm happy with you.", pl: 'To świetnie! Cieszę się razem z tobą.' },
    { en: 'Good! Add it to the hive. 🍯', pl: 'Super! Dodajmy to do ula. 🍯' },
    { en: 'Something worked today. Remember that feeling.', pl: 'Dziś coś zadziałało. Zapamiętaj to uczucie.' },
    { en: 'Good days exist. This is proof.', pl: 'Dobre dni istnieją. To jest dowód.' },
    { en: 'Look at you, having a good one.', pl: 'No proszę, dobry dzień.' },
    { en: 'Storing this one in the hive. 🍯', pl: 'Zapisuję to w ulu. 🍯' },
    { en: 'Good! Tell me more sometime.', pl: 'Świetnie! Kiedyś opowiedz mi więcej.' },
    { en: 'Something went right. That matters.', pl: 'Coś poszło dobrze. To się liczy.' },
    { en: 'Happy to hear it. Genuinely.', pl: 'Miło to słyszeć. Naprawdę.' },
    { en: 'Yes! Good days deserve to be noticed.', pl: 'Tak! Dobre dni zasługują na zauważenie.' }
  ],
  missionComplete: [
    { en: 'Mission done! A new cell in your hive. 🍯', pl: 'Misja wykonana! Nowa komórka w ulu. 🍯' },
    { en: 'You actually did it. That\'s the whole point.', pl: 'Naprawdę to zrobiłeś. Właśnie o to chodzi.' },
    { en: 'Small step. Real step. Big difference.', pl: 'Mały krok. Prawdziwy krok. Duża różnica.' },
    { en: "One more thing that's yours in this new place.", pl: 'Jeszcze jedna rzecz, która należy do ciebie w tym nowym miejscu.' },
    { en: "Hive is growing. Slowly, but it's growing.", pl: 'Ul rośnie. Powoli, ale rośnie.' },
    { en: "That was brave. Even if it didn't feel like it.", pl: 'To było odważne, nawet jeśli tak tego nie czułeś.' },
    { en: 'Done! I saw that. It counts.', pl: 'Gotowe! Widziałem to. To się liczy.' },
    { en: 'You did something today that mattered.', pl: 'Dziś zrobiłeś coś, co ma znaczenie.' },
    { en: 'Look at the hive filling up. Look at that.', pl: 'Zobacz, jak zapełnia się ul. Widzisz to?' },
    { en: "Okay I'm a little proud of you right now.", pl: 'Wiesz co? Jestem teraz z ciebie trochę dumny.' }
  ],
  missionSkip: [
    { en: 'Okay. Not every day is a mission day.', pl: 'Okej. Nie każdy dzień musi być dniem na misję.' },
    { en: "No worries - it'll be here tomorrow.", pl: 'Spokojnie, to poczeka do jutra.' },
    { en: "That's fine. You don't owe me this one.", pl: 'W porządku. Niczego nie musisz tu udowadniać.' },
    { en: 'Skipping counts as taking care of yourself too.', pl: 'Odpuszczenie też bywa dbaniem o siebie.' },
    { en: "Some days just aren't mission days. That's okay.", pl: 'Są dni bez misji i to jest w porządku.' },
    { en: "Noted. I'm not going anywhere.", pl: 'Zapisane. Nigdzie się nie wybieram.' },
    { en: 'Fair enough. Rest counts too.', pl: 'Jasne. Odpoczynek też się liczy.' },
    { en: "All good. We'll try again another day.", pl: 'W porządku. Spróbujemy innym razem.' }
  ],
  friendshipAdded: [
    { en: 'Got it! Every step counts.', pl: 'Zapamiętałem! Każdy krok się liczy.' },
    { en: 'You noticed someone. That\'s how it starts.', pl: 'Zauważyłeś kogoś. Tak to się zaczyna.' },
    { en: 'A name in the hive. Good.', pl: 'Imię w ulu. Dobrze.' },
    { en: "Your people are out there. You're finding them.", pl: 'Twoja paczka gdzieś tam jest. Właśnie jej szukasz.' },
    { en: 'One person closer to finding your hive.', pl: 'O jedną osobę bliżej do znalezienia swojego ula.' },
    { en: 'Saved. Small things matter more than they look.', pl: 'Zapisane. Małe rzeczy znaczą więcej, niż się wydaje.' },
    { en: 'Friendships start exactly like this.', pl: 'Przyjaźnie zaczynają się właśnie tak.' },
    { en: "I see it. I'm keeping track with you.", pl: 'Widzę to. Śledzę to razem z tobą.' },
    { en: "You're paying attention. That's a skill.", pl: 'Uważnie patrzysz. To ważna umiejętność.' },
    { en: 'The hive knows. 🍯', pl: 'Ul już wie. 🍯' }
  ],
  memoryOldWorld: [
    { en: 'Saved. This will always be yours.', pl: 'Zapisane. To zawsze będzie twoje.' },
    { en: 'The old home stays here. Safe.', pl: 'Stary dom zostaje tutaj. Bezpiecznie.' },
    { en: "It's okay to miss it. It was real and it mattered.", pl: 'Wolno ci za tym tęsknić. To było prawdziwe i ważne.' },
    { en: 'Keeping it. You can come back anytime.', pl: 'Zachowuję to. Możesz wracać tu, kiedy chcesz.' },
    { en: 'That memory is sealed in the hive now. 🍯', pl: 'To wspomnienie jest już bezpiecznie zapisane w ulu. 🍯' },
    { en: "Old doesn't mean gone. It's right here.", pl: 'Stare nie znaczy zniknięte. To wciąż tu jest.' },
    { en: "I'm glad you wrote that down.", pl: 'Cieszę się, że to zapisałeś.' },
    { en: "Home is more than one place. You're allowed that.", pl: 'Dom to więcej niż jedno miejsce. Masz do tego prawo.' },
    { en: 'Stored safely. Nobody takes this from you.', pl: 'Przechowane bezpiecznie. Nikt ci tego nie odbierze.' },
    { en: "Even far away, it's still yours.", pl: 'Nawet daleko, to wciąż należy do ciebie.' }
  ],
  memoryNewWorld: [
    { en: 'A new moment in the hive! 🍯', pl: 'Nowa chwila w ulu! 🍯' },
    { en: 'Look - something new is yours here now.', pl: 'Spójrz, pojawiło się tu coś nowego, co jest twoje.' },
    { en: 'The hive is getting real.', pl: 'Twój ul staje się coraz bardziej prawdziwy.' },
    { en: "One more piece of the new place that's actually yours.", pl: 'Jeszcze jeden kawałek nowego miejsca, który naprawdę jest twój.' },
    { en: 'New things can be good things. This is proof.', pl: 'Nowe rzeczy też mogą być dobre. To jest dowód.' },
    { en: 'Building it, one memory at a time.', pl: 'Budujesz to, jedno wspomnienie po drugim.' },
    { en: "That's yours. Nobody can tell you otherwise.", pl: 'To twoje i nikt nie może ci tego odebrać.' },
    { en: 'The new hive has a new cell. I love that.', pl: 'Nowy ul ma nową komórkę. Uwielbiam to.' },
    { en: 'Somewhere, this place just got a little more yours.', pl: 'Właśnie teraz to miejsce stało się odrobinę bardziej twoje.' },
    { en: 'Saved. The hive remembers. 🍯', pl: 'Zapisane. Ul pamięta. 🍯' }
  ]
};
