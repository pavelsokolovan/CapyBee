import { useEffect, useMemo, useRef, useState } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import { CapyBeeAvatar, CapyBeeBubble, capyBeeAvatar, sameCalendarDay } from './capybee';
import { useCapyBeePhrase } from './hooks/useCapyBeePhrase';
import { useFriendshipAddedPhrase } from './hooks/useFriendshipAddedPhrase';
import { capybeePhrases, type CapyBeePhrasePoolKey } from './data/capybeePhrases';
import { HoneycombMap } from './components/HoneycombMap';
import { FriendshipStageSelector } from './components/FriendshipStageSelector';
import { FriendshipToast } from './components/FriendshipToast';
import { HomeIcon, MissionsIcon, FriendshipsIcon, MemoriesIcon, ProfileIcon } from './components/NavIcons';
import { useHoneycombCells } from './hooks/useHoneycombCells';
import type { UseHoneycombCellsInput } from './hooks/useHoneycombCells';
import oldWorldTabImage from './assets/honeycomb/old-world-tab.png';
import newWorldTabImage from './assets/honeycomb/new-world-tab.png';
import missionsTabImage from './assets/honeycomb/missions-tab.png';
import friendshipsTabImage from './assets/honeycomb/friedships-tab.png';
import { STAGE_META, type FriendshipStage } from './constants/friendshipStages';

export interface UserProfile {
  authenticated: boolean;
  id?: string;
  email?: string;
  displayName?: string;
  avatarUrl?: string;
}

export interface CheckIn {
  id: string;
  mood: string;
  note: string;
  createdAt: string;
}

interface ChildProfile {
  id: string;
  nickname: string;
  birthYear?: number;
  preferredLocale: 'en' | 'pl';
  avatarSeed?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

interface Mission {
  id: string;
  code: string;
  title: string;
  timeHint: string;
  description: string;
  active: boolean;
}

interface MissionCompletion {
  id: string;
  missionId: string;
  missionCode: string;
  title: string;
  profileId: string;
  completedAt: string;
  worldType?: 'old_world' | 'new_world';
  note?: string;
}

interface FriendshipEntry {
  id: string;
  personLabel: string;
  stage: string;
  note?: string;
  createdAt: string;
  updatedAt: string;
}

interface MemoryEntry {
  id: string;
  worldType: 'old_world' | 'new_world';
  title?: string;
  textContent?: string;
  mediaUrl?: string;
  isFavorite: boolean;
  createdAt: string;
  updatedAt: string;
}

type Mood = 'heavy' | 'okay' | 'good';
type TabKey = 'home' | 'missions' | 'friendships' | 'memories' | 'profile';
type FeedbackKind = 'checkin' | 'mission' | 'friendship' | 'memory';

interface MissionSkipAcknowledgement {
  missionId: string;
  phrase: string;
}

interface ActiveFeedback {
  kind: FeedbackKind;
  phrase: string;
  avatar: string;
}

const missionTitleOverridesPl: Record<string, string> = {
  say_hi_once: 'Powiedz „cześć” jednej osobie',
  sit_one_table_closer: 'Usiądź przy stoliku o jeden bliżej',
  find_one_good_thing: 'Znajdź jedną dobrą rzecz',
  draw_old_and_new_home: 'Narysuj stary i nowy dom',
  ask_about_one_club: 'Zapytaj o jedno kółko zainteresowań',
  say_hi_someone_new_school: 'Powiedz „cześć” komuś nowemu w szkole',
  ask_favorite_subject: 'Zapytaj kogoś o ulubiony przedmiot',
  sit_somewhere_new_lunch: 'Usiądź dziś w nowym miejscu na lunchu',
  compliment_someone_small: 'Powiedz komuś drobny komplement',
  learn_one_new_word_local_language: 'Naucz się jednego nowego słowa w lokalnym języku',
  find_one_new_thing_near_home: 'Znajdź jedną nową rzecz w okolicy domu',
  try_new_food_here: 'Spróbuj jednego nowego jedzenia',
  ask_teacher_question_after_class: 'Zadaj nauczycielowi jedno pytanie po lekcji',
  draw_old_street_memory: 'Narysuj lub opisz z pamięci swoją starą ulicę',
  write_smell_sound_home: 'Zapisz zapach lub dźwięk, który przypomina ci dom',
  message_old_friend_hi: 'Napisz do starego przyjaciela, żeby się przywitać',
  name_one_thing_easier_here: 'Wskaż jedną rzecz, która tutaj jest łatwiejsza niż w dawnym domu',
  look_up_school_club: 'Sprawdź jedno kółko lub aktywność w szkole',
  ask_explain_game_rule: 'Poproś kogoś o wyjaśnienie gry lub zasady',
  photo_today_okay: 'Zrób zdjęcie czegoś, co sprawiło, że dziś było okej',
  wave_smile_two_days: 'Pomachaj lub uśmiechnij się do tej samej osoby przez dwa dni z rzędu',
  cook_something_from_home: 'Zaproponuj rodzinie wspólne ugotowanie czegoś z domu',
  find_popular_school_sport: 'Dowiedz się, jaki sport jest popularny w nowej szkole',
  write_one_sentence_today_felt: 'Napisz jedno zdanie o tym, jak naprawdę minął dziś dzień',
  offer_help_classmate_small: 'Zaproponuj koledze lub koleżance pomoc w czymś drobnym'
};

const missionTitleOverridesEn: Record<string, string> = {
  say_hi_once: 'Say hi to one person',
  sit_one_table_closer: 'Sit one table closer',
  find_one_good_thing: 'Find one good thing',
  draw_old_and_new_home: 'Draw old and new home',
  ask_about_one_club: 'Ask about one club',
  say_hi_someone_new_school: 'Say hi to someone new at school',
  ask_favorite_subject: 'Ask someone what their favorite subject is',
  sit_somewhere_new_lunch: 'Sit somewhere new at lunch',
  compliment_someone_small: 'Compliment someone on something small',
  learn_one_new_word_local_language: 'Learn one new word in the local language',
  find_one_new_thing_near_home: 'Find one thing near your home you did not notice before',
  try_new_food_here: 'Try one food you have not tried here yet',
  ask_teacher_question_after_class: 'Ask a teacher one question after class',
  draw_old_street_memory: 'Draw or describe your old street from memory',
  write_smell_sound_home: 'Write down a smell or sound that reminds you of home',
  message_old_friend_hi: 'Message an old friend just to say hi',
  name_one_thing_easier_here: 'Name one thing that is easier here than back home',
  look_up_school_club: 'Look up one club or activity at your school',
  ask_explain_game_rule: 'Ask someone to explain a game or rule you do not know',
  photo_today_okay: 'Take a photo of something that made today okay',
  wave_smile_two_days: 'Wave or smile at the same person two days in a row',
  cook_something_from_home: 'Ask your family to cook something from home together',
  find_popular_school_sport: 'Find out what sport is popular at your new school',
  write_one_sentence_today_felt: 'Write one sentence about how today actually felt',
  offer_help_classmate_small: 'Offer to help a classmate with something small'
};

function polishMinutesLabel(minutes: number): string {
  if (minutes === 1) return 'minutę';
  const mod10 = minutes % 10;
  const mod100 = minutes % 100;
  if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) {
    return 'minuty';
  }
  return 'minut';
}

function localizeMissionTimeHint(timeHint: string, locale: 'en' | 'pl'): string {
  const normalized = timeHint.trim().toLowerCase();
  const minuteMatch = normalized.match(/(\d+)\s*min/);

  if (!minuteMatch) return timeHint;

  const minutes = Number(minuteMatch[1]);

  if (locale === 'pl') {
    if (normalized.includes('this takes') || normalized.includes('to zajmie')) {
      return `To zajmie ${minutes} ${polishMinutesLabel(minutes)}`;
    }
    return timeHint;
  }

  if (normalized.includes('to zajmie') || normalized.includes('this takes')) {
    return `This takes ${minutes} minute${minutes === 1 ? '' : 's'}`;
  }
  return timeHint;
}

function localizeMissionTitle(code: string, title: string, locale: 'en' | 'pl'): string {
  if (locale === 'pl') {
    return missionTitleOverridesPl[code] ?? title;
  }
  return missionTitleOverridesEn[code] ?? title;
}

const friendshipStageLabelMap = {
  en: {
    noticed: 'Noticed them',
    was_nice: 'They were nice to me',
    talked: 'We talked',
    want_to_know_better: 'Want to know them better'
  },
  pl: {
    noticed: 'Zauważyłem/-am',
    was_nice: 'Był/a dla mnie miły/a',
    talked: 'Porozmawialiśmy',
    want_to_know_better: 'Chcę go/ją lepiej poznać'
  }
} as const;

function StarIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M12 3.5l2.6 5.6 6.1.6-4.6 4.2 1.3 6.1L12 16.9l-5.4 3.1 1.3-6.1L3.3 9.7l6.1-.6L12 3.5z"
        fill={filled ? '#f2b233' : 'none'}
        stroke="#c8952a"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M5 7h14M10 4h4a1 1 0 011 1v2H9V5a1 1 0 011-1z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M6.5 7l.8 12a1.5 1.5 0 001.5 1.4h6.4a1.5 1.5 0 001.5-1.4L17.5 7"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M10 11v6M14 11v6"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

const copy = {
  en: {
    homeTitle: "How's today?",
    profileSetupTitle: 'Create child profile',
    profileSetupHint: 'Use a nickname. Real names are optional and not required.',
    save: 'Save',
    saving: 'Saving...',
    setUp: 'Set up profile',
    checkInPlaceholder: 'Optional note',
    missions: 'Missions',
    friendships: 'Friendships',
    memories: 'Memories',
    profile: 'Profile',
    home: 'Home',
    navLabel: 'Main navigation',
    oldWorld: 'Old World',
    newWorld: 'New World',
    language: 'Language',
    noItems: 'Nothing here yet.',
    greetingFirstVisit: 'Hey! How was today?',
    greetingReturning: 'Good to see you again.',
    reactionHeavy: "That sounds hard. I'm here.",
    reactionOkay: 'Okay is something. Good job showing up.',
    reactionGood: "That's great! I'm happy with you.",
    missionSuggestion: 'I have a small mission for you today ->',
    missionEmpty: 'No missions right now - check back tomorrow!',
    missionDone: 'Mission done! A new cell in your hive',
    missionNotToday: 'Not today',
    missionOptionalNote: 'Anything you want to remember? (optional)',
    missionSave: 'Save',
    missionBack: 'Back',
    missionUndoSkip: 'Undo',
    missionSkipped: 'Mission skipped for now.',
    missionCompleted: 'Completed',
    missionHistoryEmpty: 'No missions yet.',
    friendshipEmpty: 'Who did you notice today?',
    friendshipEmptyState: 'No one here yet — that\'s okay. We\'ll find your people.',
    friendshipToast: 'Got it! Every step counts.',
    friendshipStageLabel: 'Stage',
    friendshipNotePlaceholder: 'What do you remember about them?',
    friendshipRemove: 'Remove',
    friendshipRemoved: 'Removed. Undo',
    friendshipUndo: 'Undo',
    memoryOldEmpty: 'Your old home is safe here.',
    memoryNewEmpty: 'Start building your new hive.',
    memorySavedOld: 'Saved. This will always be yours.',
    memorySavedNew: 'A new moment in the hive!',
    askName: 'What should I call you?',
    person: 'Person',
    stage: 'Stage',
    note: 'Note',
    addEntry: 'Add entry',
    title: 'Title',
    story: 'Story',
    favoriteMemory: 'Favorite memory',
    deleteMemory: 'Delete memory',
    addMemory: 'Add memory',
    memoryDeleted: 'Memory removed.',
    memoryUndoDelete: 'Undo',
    checkInDeleted: 'Check-in removed.',
    checkInUndoDelete: 'Undo',
    deleteCheckIn: 'Delete check-in',
    missionDeleted: 'Mission removed.',
    missionUndoDelete: 'Undo',
    deleteMission: 'Delete mission',
    profileActive: 'Profile active',
    markComplete: 'Mark complete',
    honeycombProgress: 'Honeycomb progress',
    recentCheckins: 'Recent check-ins',
    completionHistory: 'Completion history',
    nickname: 'Nickname',
    birthYear: 'Birth year',
    avatarSeed: 'Avatar seed',
    english: 'English',
    polish: 'Polish',
    logout: 'Log out',
    moodHeavy: 'Heavy',
    moodOkay: 'Okay',
    moodGood: 'Good',
    hiveTitle: 'Your hive',
    hiveAriaLabel: 'Your hive - progress',
    memoryFallbackTitle: 'Memory',
    friendshipStageAriaLabel: 'Friendship stage'
  },
  pl: {
    homeTitle: 'Jak minął dzień?',
    profileSetupTitle: 'Utwórz profil dziecka',
    profileSetupHint: 'Użyj pseudonimu. Prawdziwe imię nie jest wymagane.',
    save: 'Zapisz',
    saving: 'Zapisywanie...',
    setUp: 'Utwórz profil',
    checkInPlaceholder: 'Opcjonalna notatka',
    missions: 'Misje',
    friendships: 'Relacje',
    memories: 'Wspomnienia',
    profile: 'Profil',
    home: 'Start',
    navLabel: 'Główna nawigacja',
    oldWorld: 'Stary Świat',
    newWorld: 'Nowy Świat',
    language: 'Język',
    noItems: 'Na razie nic tu nie ma.',
    greetingFirstVisit: 'Hej! Jak minął dzień?',
    greetingReturning: 'Miło cię znowu widzieć.',
    reactionHeavy: 'To brzmi ciężko. Jestem obok.',
    reactionOkay: 'To, że jest po prostu okej, też się liczy. Dobra robota.',
    reactionGood: 'To świetnie! Cieszę się razem z tobą.',
    missionSuggestion: 'Mam dla ciebie małą misję na dziś ->',
    missionEmpty: 'Teraz nie ma nowych misji. Zajrzyj jutro!',
    missionDone: 'Misja wykonana! Nowa komórka w ulu.',
    missionNotToday: 'Nie dzisiaj',
    missionOptionalNote: 'Coś, co chcesz zapamiętać? (opcjonalnie)',
    missionSave: 'Zapisz',
    missionBack: 'Wróć',
    missionUndoSkip: 'Cofnij',
    missionSkipped: 'Misja odłożona na teraz.',
    missionCompleted: 'Ukończone',
    missionHistoryEmpty: 'Nie ma jeszcze żadnych misji.',
    friendshipEmpty: 'Kogo dziś zauważyłeś?',
    friendshipEmptyState: 'Nikogo tu jeszcze nie ma — to nic. Znajdziemy twoją paczkę.',
    friendshipToast: 'Zapamiętałem! Każdy krok się liczy.',
    friendshipStageLabel: 'Etap',
    friendshipNotePlaceholder: 'Co o nich pamiętasz?',
    friendshipRemove: 'Usuń',
    friendshipRemoved: 'Usunięto. Cofnij',
    friendshipUndo: 'Cofnij',
    memoryOldEmpty: 'Twój stary dom jest tutaj bezpieczny.',
    memoryNewEmpty: 'Zacznij budować swój nowy ul.',
    memorySavedOld: 'Zapisane. To zawsze będzie twoje.',
    memorySavedNew: 'Nowa chwila w ulu!',
    askName: 'Jak mam się do ciebie zwracać?',
    person: 'Osoba',
    stage: 'Etap',
    note: 'Notatka',
    addEntry: 'Dodaj wpis',
    title: 'Tytuł',
    story: 'Historia',
    favoriteMemory: 'Ulubione wspomnienie',
    deleteMemory: 'Usuń wspomnienie',
    addMemory: 'Dodaj wspomnienie',
    memoryDeleted: 'Wspomnienie usunięte.',
    memoryUndoDelete: 'Cofnij',
    checkInDeleted: 'Check-in dnia usunięty.',
    checkInUndoDelete: 'Cofnij',
    deleteCheckIn: 'Usuń check-in dnia',
    missionDeleted: 'Misja usunięta.',
    missionUndoDelete: 'Cofnij',
    deleteMission: 'Usuń misję',
    profileActive: 'Profil aktywny',
    markComplete: 'Oznacz jako ukończone',
    honeycombProgress: 'Postęp ula',
    recentCheckins: 'Twoje ostatnie chwile',
    completionHistory: 'Historia ukończeń',
    nickname: 'Pseudonim',
    birthYear: 'Rok urodzenia',
    avatarSeed: 'Nazwa awatara',
    english: 'Angielski',
    polish: 'Polski',
    logout: 'Wyloguj',
    moodHeavy: 'Ciężko',
    moodOkay: 'Okej',
    moodGood: 'Dobrze',
    hiveTitle: 'Twój ul',
    hiveAriaLabel: 'Twój ul — postęp',
    memoryFallbackTitle: 'Wspomnienie',
    friendshipStageAriaLabel: 'Etap relacji'
  }
};

function moodPickerAvatar(mood: Mood) {
  if (mood === 'heavy') return capyBeeAvatar.faceSad;
  if (mood === 'okay') return capyBeeAvatar.faceOkay;
  return capyBeeAvatar.faceHappy;
}

function moodReactionAvatar(mood: Mood) {
  if (mood === 'heavy') return capyBeeAvatar.empathetic;
  if (mood === 'okay') return capyBeeAvatar.faceOkay;
  return capyBeeAvatar.celebrating;
}

function checkInListFace(mood: string) {
  if (mood === 'heavy') return capyBeeAvatar.faceSad;
  if (mood === 'okay') return capyBeeAvatar.faceOkay;
  return capyBeeAvatar.faceHappy;
}

const HOME_GREETING_LAST_INDEX_KEY = 'capybee.homeGreeting.lastIndex';

function pickHomeGreetingIndex() {
  const pool = capybeePhrases.homeGreeting;
  if (pool.length === 0) return 0;

  if (typeof window === 'undefined') {
    return Math.floor(Math.random() * pool.length);
  }

  let lastIndex: number | null = null;
  const raw = window.sessionStorage.getItem(HOME_GREETING_LAST_INDEX_KEY);
  if (raw !== null) {
    const parsed = Number.parseInt(raw, 10);
    if (Number.isInteger(parsed) && parsed >= 0 && parsed < pool.length) {
      lastIndex = parsed;
    }
  }

  let index = 0;
  do {
    index = Math.floor(Math.random() * pool.length);
  } while (pool.length > 1 && index === lastIndex);

  window.sessionStorage.setItem(HOME_GREETING_LAST_INDEX_KEY, String(index));
  return index;
}

export function AuthenticatedHome({ user }: { user: UserProfile }) {
  const prefersReducedMotion = useReducedMotion();
  const [activeTab, setActiveTab] = useState<TabKey>('home');
  const [profile, setProfile] = useState<ChildProfile | null>(null);
  const [profileMissing, setProfileMissing] = useState(false);
  const [locale, setLocale] = useState<'en' | 'pl'>('en');
  const pickPhrase = useCapyBeePhrase(locale);
  const pickFriendshipPhrase = useFriendshipAddedPhrase(locale);

  const [mood, setMood] = useState<Mood>('okay');
  const [note, setNote] = useState('');
  const [checkIns, setCheckIns] = useState<CheckIn[]>([]);
  const [checkInLoading, setCheckInLoading] = useState(false);
  const [missionSuggestionVisible, setMissionSuggestionVisible] = useState(false);

  const [missions, setMissions] = useState<Mission[]>([]);
  const [missionCompletions, setMissionCompletions] = useState<MissionCompletion[]>([]);
  const [missionNotes, setMissionNotes] = useState<Record<string, string>>({});
  const [expandedMissionId, setExpandedMissionId] = useState<string | null>(null);
  const [savingMissionId, setSavingMissionId] = useState<string | null>(null);
  const [cheerMissionId, setCheerMissionId] = useState<string | null>(null);
  const [skipAcknowledgement, setSkipAcknowledgement] = useState<MissionSkipAcknowledgement | null>(null);
  const [skipUndoMissionId, setSkipUndoMissionId] = useState<string | null>(null);
  const [skipPendingMissionId, setSkipPendingMissionId] = useState<string | null>(null);
  const [activeFeedback, setActiveFeedback] = useState<ActiveFeedback | null>(null);
  const [feedbackFadeOut, setFeedbackFadeOut] = useState(false);

  const [friendships, setFriendships] = useState<FriendshipEntry[]>([]);
  const [friendLabel, setFriendLabel] = useState('');
  const [friendStage, setFriendStage] = useState<FriendshipStage>('noticed');
  const [friendNote, setFriendNote] = useState('');
  const [friendshipToast, setFriendshipToast] = useState<{ message: string; avatarSrc: string } | null>(null);
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);
  const [pendingDeleteTimer, setPendingDeleteTimer] = useState<number | null>(null);
  const [pendingDeleteCheckInId, setPendingDeleteCheckInId] = useState<string | null>(null);
  const [pendingDeleteMissionId, setPendingDeleteMissionId] = useState<string | null>(null);

  const [worldType, setWorldType] = useState<'old_world' | 'new_world'>('old_world');
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [allMemories, setAllMemories] = useState<MemoryEntry[]>([]);
  const [memoryTitle, setMemoryTitle] = useState('');
  const [memoryText, setMemoryText] = useState('');
  const [memoryFavorite, setMemoryFavorite] = useState(false);
  const [pendingDeleteMemoryId, setPendingDeleteMemoryId] = useState<string | null>(null);
  const [homeAnimatedCellId, setHomeAnimatedCellId] = useState<string | null>(null);
  const [homeGreetingIndex] = useState(() => pickHomeGreetingIndex());


  const [setupNickname, setSetupNickname] = useState('');
  const [setupBirthYear, setSetupBirthYear] = useState('');
  const [setupLocale, setSetupLocale] = useState<'en' | 'pl'>('en');
  const [setupAvatarSeed, setSetupAvatarSeed] = useState('sunny-bee');
  const [setupLoading, setSetupLoading] = useState(false);

  const feedbackFadeOutTimer = useRef<number | null>(null);
  const feedbackCleanupTimer = useRef<number | null>(null);
  const missionSkipAckTimer = useRef<number | null>(null);
  const missionSkipCollapseTimer = useRef<number | null>(null);
  const missionSkipUndoTimer = useRef<number | null>(null);
  const missionCheerTimer = useRef<number | null>(null);
  const friendshipDeleteTimer = useRef<number | null>(null);
  const memoryDeleteTimerRef = useRef<number | null>(null);
  const checkInDeleteTimerRef = useRef<number | null>(null);
  const missionCompletionDeleteTimerRef = useRef<number | null>(null);

  const text = copy[locale];
  const moodLabels: Record<Mood, string> = {
    heavy: locale === 'pl' ? text.moodHeavy : 'Heavy',
    okay: locale === 'pl' ? text.moodOkay : 'Okay',
    good: locale === 'pl' ? text.moodGood : 'Good'
  };
  const getFriendshipStageLabel = (stage: FriendshipStage) => friendshipStageLabelMap[locale][stage];
  const bottomNavItems = [
    { key: 'home' as const, label: text.home, Icon: HomeIcon },
    { key: 'missions' as const, label: text.missions, Icon: MissionsIcon },
    { key: 'friendships' as const, label: text.friendships, Icon: FriendshipsIcon },
    { key: 'memories' as const, label: text.memories, Icon: MemoriesIcon }
  ];

  const triggerFeedback = (nextFeedback: ActiveFeedback) => {
    if (feedbackFadeOutTimer.current) window.clearTimeout(feedbackFadeOutTimer.current);
    if (feedbackCleanupTimer.current) window.clearTimeout(feedbackCleanupTimer.current);

    setFeedbackFadeOut(false);
    setActiveFeedback(nextFeedback);

    feedbackFadeOutTimer.current = window.setTimeout(() => {
      setFeedbackFadeOut(true);
    }, 4000);

    feedbackCleanupTimer.current = window.setTimeout(() => {
      setActiveFeedback(null);
      setFeedbackFadeOut(false);
    }, 4300);
  };

  const moodPoolKey = (value: Mood): CapyBeePhrasePoolKey => {
    if (value === 'heavy') return 'moodHeavy';
    if (value === 'okay') return 'moodOkay';
    return 'moodGood';
  };

  useEffect(() => {
    initialize();
  }, []);

  useEffect(() => {
    return () => {
      if (feedbackFadeOutTimer.current) window.clearTimeout(feedbackFadeOutTimer.current);
      if (feedbackCleanupTimer.current) window.clearTimeout(feedbackCleanupTimer.current);
      if (missionSkipAckTimer.current) window.clearTimeout(missionSkipAckTimer.current);
      if (missionSkipCollapseTimer.current) window.clearTimeout(missionSkipCollapseTimer.current);
      if (missionSkipUndoTimer.current) window.clearTimeout(missionSkipUndoTimer.current);
      if (missionCheerTimer.current) window.clearTimeout(missionCheerTimer.current);
      if (friendshipDeleteTimer.current) window.clearTimeout(friendshipDeleteTimer.current);
      if (memoryDeleteTimerRef.current) window.clearTimeout(memoryDeleteTimerRef.current);
    };
  }, []);

  useEffect(() => {
    if (profile) {
      setLocale(profile.preferredLocale);
    }
  }, [profile]);

  useEffect(() => {
    if (profileMissing) return;
    fetchCheckIns();
    fetchMissions();
    fetchMissionCompletions();
    fetchFriendships();
    fetchMemories(worldType);
    fetchAllMemories();
  }, [profileMissing]);

  useEffect(() => {
    if (!profileMissing) {
      fetchMemories(worldType);
    }
  }, [worldType]);

  useEffect(() => {
    if (!profileMissing) {
      fetchMissions();
    }
  }, [locale, profileMissing]);

  useEffect(() => {
    if (activeTab !== 'home') {
      setHomeAnimatedCellId(null);
    }
  }, [activeTab]);

  useEffect(() => {
    if (activeTab !== 'home') {
      setMissionSuggestionVisible(false);
    }
  }, [activeTab]);

  const hasCheckInToday = useMemo(
    () => checkIns.some((entry) => sameCalendarDay(new Date(entry.createdAt), new Date())),
    [checkIns]
  );

  const visibleMissions = useMemo(() => missions, [missions]);
  const sortedFriendships = useMemo(() => {
    return [...friendships].sort((left, right) => {
      const leftTime = new Date(left.createdAt ?? left.updatedAt).getTime();
      const rightTime = new Date(right.createdAt ?? right.updatedAt).getTime();
      return rightTime - leftTime;
    });
  }, [friendships]);

  const sortedMemories = useMemo(() => {
    return [...memories].sort((a, b) => {
      if (a.isFavorite !== b.isFavorite) {
        return a.isFavorite ? -1 : 1;
      }
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    });
  }, [memories]);

  const visibleMemories = useMemo(
    () => sortedMemories.filter((entry) => entry.id !== pendingDeleteMemoryId),
    [sortedMemories, pendingDeleteMemoryId]
  );

  const visibleCheckIns = useMemo(
    () => checkIns.filter((entry) => entry.id !== pendingDeleteCheckInId),
    [checkIns, pendingDeleteCheckInId]
  );

  const visibleMissionCompletions = useMemo(
    () => missionCompletions.filter((entry) => entry.id !== pendingDeleteMissionId),
    [missionCompletions, pendingDeleteMissionId]
  );

  const homeGreeting = capybeePhrases.homeGreeting[homeGreetingIndex] ?? capybeePhrases.homeGreeting[0];
  const homeAvatar = hasCheckInToday ? capyBeeAvatar.default : capyBeeAvatar.waving;
  const homeAvatarBubble = locale === 'pl' ? (homeGreeting?.pl ?? homeGreeting?.en ?? '') : (homeGreeting?.en ?? homeGreeting?.pl ?? '');

  const homeHoneycombCells = useHoneycombCells({
    checkIns,
    missions: missionCompletions,
    friendships,
    memories: allMemories,
    locale
  } satisfies UseHoneycombCellsInput);


  const redirectToLogin = () => {
    window.location.href = '/oauth2/authorization/google';
  };

  const request = async <T,>(path: string, init?: RequestInit): Promise<T> => {
    const res = await fetch(path, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
      ...init
    });

    if (res.status === 401) {
      redirectToLogin();
      throw new Error('Unauthorized');
    }

    if (!res.ok) {
      throw new Error(`Request failed: ${res.status}`);
    }

    if (res.status === 204) {
      return undefined as T;
    }

    return (await res.json()) as T;
  };

  const initialize = async () => {
    try {
      const profileData = await request<ChildProfile>('/api/child-profile');
      setProfile(profileData);
      setProfileMissing(false);
    } catch (error) {
      try {
        const response = await fetch('/api/child-profile', { credentials: 'include' });
        if (response.status === 404) {
          setProfileMissing(true);
          return;
        }
        if (response.status === 401) {
          redirectToLogin();
          return;
        }
      } catch (fallbackError) {
        console.error(fallbackError);
      }
      console.error(error);
    }
  };

  const fetchCheckIns = async () => {
    try {
      const data = await request<CheckIn[]>('/api/check-ins');
      setCheckIns(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchMissions = async () => {
    try {
      const data = await request<Mission[]>(`/api/missions?active=true&locale=${locale}`);
      setMissions(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchMissionCompletions = async () => {
    try {
      const data = await request<MissionCompletion[]>('/api/missions/completions');
      setMissionCompletions(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchFriendships = async () => {
    try {
      const data = await request<FriendshipEntry[]>('/api/friendships');
      setFriendships(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchMemories = async (targetWorld: 'old_world' | 'new_world') => {
    try {
      const data = await request<MemoryEntry[]>(`/api/memories?worldType=${targetWorld}`);
      setMemories(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchAllMemories = async () => {
    try {
      const [oldWorldMemories, newWorldMemories] = await Promise.all([
        request<MemoryEntry[]>('/api/memories?worldType=old_world'),
        request<MemoryEntry[]>('/api/memories?worldType=new_world')
      ]);

      const merged = [...oldWorldMemories, ...newWorldMemories];
      const deduplicated = Array.from(new Map(merged.map((entry) => [entry.id, entry])).values());
      setAllMemories(deduplicated);
    } catch (error) {
      console.error(error);
    }
  };

  const submitCheckIn = async (event: React.FormEvent) => {
    event.preventDefault();
    setCheckInLoading(true);
    const submittedMood = mood;

    try {
      const created = await request<CheckIn>('/api/check-ins', {
        method: 'POST',
        body: JSON.stringify({ mood: submittedMood, note })
      });
      setNote('');
      await fetchCheckIns();
      setMissionSuggestionVisible(true);
      triggerFeedback({
        kind: 'checkin',
        phrase: pickPhrase(moodPoolKey(submittedMood)),
        avatar: moodReactionAvatar(submittedMood)
      });
      if (activeTab === 'home') {
        setHomeAnimatedCellId(created.id);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setCheckInLoading(false);
    }
  };

  const createProfile = async (event: React.FormEvent) => {
    event.preventDefault();
    setSetupLoading(true);
    try {
      const payload = {
        nickname: setupNickname,
        birthYear: setupBirthYear ? Number(setupBirthYear) : null,
        preferredLocale: setupLocale,
        avatarSeed: setupAvatarSeed
      };

      const created = await request<ChildProfile>('/api/child-profile', {
        method: 'POST',
        body: JSON.stringify(payload)
      });

      setProfile(created);
      setProfileMissing(false);
      setLocale(created.preferredLocale);
      await fetchCheckIns();
      await fetchMissions();
      await fetchMissionCompletions();
      await fetchFriendships();
      await fetchMemories(worldType);
    } catch (error) {
      console.error(error);
    } finally {
      setSetupLoading(false);
    }
  };

  const updateProfile = async (changes: Partial<ChildProfile>) => {
    try {
      const updated = await request<ChildProfile>('/api/child-profile', {
        method: 'PATCH',
        body: JSON.stringify({
          nickname: changes.nickname,
          birthYear: changes.birthYear,
          preferredLocale: changes.preferredLocale,
          avatarSeed: changes.avatarSeed,
          active: changes.active
        })
      });
      setProfile(updated);
      setLocale(updated.preferredLocale);
    } catch (error) {
      console.error(error);
    }
  };

  const completeMission = async (missionId: string, noteValue: string) => {
    try {
      setSavingMissionId(missionId);
      const created = await request<MissionCompletion>(`/api/missions/${missionId}/completions`, {
        method: 'POST',
        body: JSON.stringify({ note: noteValue })
      });
      setMissionNotes((current) => ({ ...current, [missionId]: '' }));
      setExpandedMissionId(null);
      setCheerMissionId(missionId);
      if (missionCheerTimer.current) {
        window.clearTimeout(missionCheerTimer.current);
      }
      missionCheerTimer.current = window.setTimeout(() => {
        setCheerMissionId((current) => (current === missionId ? null : current));
      }, 2200);
      await fetchMissions();
      await fetchMissionCompletions();
      triggerFeedback({
        kind: 'mission',
        phrase: pickPhrase('missionComplete'),
        avatar: capyBeeAvatar.celebrating
      });
      if (activeTab === 'home') {
        setHomeAnimatedCellId(created.id);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setSavingMissionId(null);
    }
  };

  const skipMission = async (missionId: string) => {
    if (skipPendingMissionId) {
      return;
    }

    try {
      setSkipPendingMissionId(missionId);
      await request(`/api/missions/${missionId}/skip`, { method: 'POST' });

      if (missionSkipAckTimer.current) {
        window.clearTimeout(missionSkipAckTimer.current);
      }
      if (missionSkipCollapseTimer.current) {
        window.clearTimeout(missionSkipCollapseTimer.current);
      }

      setExpandedMissionId((current) => (current === missionId ? null : current));
      setMissionNotes((current) => ({ ...current, [missionId]: '' }));
      setSkipAcknowledgement({ missionId, phrase: pickPhrase('missionSkip') });
      await fetchMissions();
      setSkipUndoMissionId(missionId);
      setSkipPendingMissionId(null);

      missionSkipAckTimer.current = window.setTimeout(() => {
        setSkipAcknowledgement((current) => (current?.missionId === missionId ? null : current));
      }, prefersReducedMotion ? 0 : 2200);

      if (missionSkipUndoTimer.current) {
        window.clearTimeout(missionSkipUndoTimer.current);
      }
      missionSkipUndoTimer.current = window.setTimeout(() => {
        setSkipUndoMissionId((current) => (current === missionId ? null : current));
      }, 4000);
    } catch (error) {
      setSkipPendingMissionId(null);
      console.error(error);
    }
  };

  const undoMissionSkip = async () => {
    if (!skipUndoMissionId) {
      return;
    }

    const missionId = skipUndoMissionId;
    try {
      await request(`/api/missions/${missionId}/skip/undo`, { method: 'POST' });
      await fetchMissions();
      setSkipUndoMissionId(null);
      if (missionSkipUndoTimer.current) {
        window.clearTimeout(missionSkipUndoTimer.current);
        missionSkipUndoTimer.current = null;
      }
    } catch (error) {
      console.error(error);
    }
  };

  const addFriendship = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      const created = await request<FriendshipEntry>('/api/friendships', {
        method: 'POST',
        body: JSON.stringify({ personLabel: friendLabel, stage: friendStage, note: friendNote })
      });
      setFriendLabel('');
      setFriendNote('');
      setFriendStage('noticed');
      await fetchFriendships();
      setFriendshipToast({
        message: pickFriendshipPhrase(friendStage, friendLabel || (locale === 'pl' ? 'kogoś' : 'them')),
        avatarSrc: resolveFriendshipAvatar(friendStage)
      });
      if (activeTab === 'home') {
        setHomeAnimatedCellId(created.id);
      }
    } catch (error) {
      console.error(error);
    }
  };

  const resolveFriendshipAvatar = (stage: FriendshipStage) => {
    const expression = STAGE_META[stage].avatarExpression;
    if (expression === 'excited') return capyBeeAvatar.celebrating;
    if (expression === 'warm-smile') return capyBeeAvatar.faceHappy;
    if (expression === 'hopeful') return capyBeeAvatar.waving;
    return capyBeeAvatar.default;
  };

  const handleFriendshipRemoveClick = (id: string) => {
    if (friendshipDeleteTimer.current) {
      window.clearTimeout(friendshipDeleteTimer.current);
    }

    setPendingDeleteId(id);
    friendshipDeleteTimer.current = window.setTimeout(async () => {
      try {
        await request<void>(`/api/friendships/${id}`, { method: 'DELETE' });
        await fetchFriendships();
      } catch (error) {
        console.error(error);
      } finally {
        setPendingDeleteId(null);
        friendshipDeleteTimer.current = null;
      }
    }, 5000);
  };

  const undoFriendshipRemove = () => {
    if (friendshipDeleteTimer.current) {
      window.clearTimeout(friendshipDeleteTimer.current);
      friendshipDeleteTimer.current = null;
    }
    setPendingDeleteId(null);
  };

  const addMemory = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      const created = await request<MemoryEntry>('/api/memories', {
        method: 'POST',
        body: JSON.stringify({
          worldType,
          title: memoryTitle,
          textContent: memoryText,
          isFavorite: memoryFavorite
        })
      });
      setMemoryTitle('');
      setMemoryText('');
      setMemoryFavorite(false);
      await fetchMemories(worldType);
      await fetchAllMemories();
      triggerFeedback({
        kind: 'memory',
        phrase: pickPhrase(worldType === 'old_world' ? 'memoryOldWorld' : 'memoryNewWorld'),
        avatar: capyBeeAvatar.celebrating
      });
      if (activeTab === 'home') {
        setHomeAnimatedCellId(created.id);
      }
    } catch (error) {
      console.error(error);
    }
  };

  const toggleFavorite = async (entry: MemoryEntry) => {
    try {
      await request<MemoryEntry>(`/api/memories/${entry.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ isFavorite: !entry.isFavorite })
      });
      await fetchMemories(worldType);
      await fetchAllMemories();
    } catch (error) {
      console.error(error);
    }
  };

  const commitMemoryDelete = async (id: string) => {
    setPendingDeleteMemoryId((current) => (current === id ? null : current));
    try {
      await request<void>(`/api/memories/${id}`, { method: 'DELETE' });
      await fetchMemories(worldType);
      await fetchAllMemories();
    } catch (error) {
      console.error(error);
    }
  };

  const requestDeleteMemory = (id: string) => {
    // Only one pending delete at a time — if another is already waiting, commit it now.
    if (pendingDeleteMemoryId && pendingDeleteMemoryId !== id) {
      if (memoryDeleteTimerRef.current) {
        window.clearTimeout(memoryDeleteTimerRef.current);
      }
      commitMemoryDelete(pendingDeleteMemoryId);
    }

    setPendingDeleteMemoryId(id);
    if (memoryDeleteTimerRef.current) {
      window.clearTimeout(memoryDeleteTimerRef.current);
    }
    memoryDeleteTimerRef.current = window.setTimeout(() => {
      commitMemoryDelete(id);
    }, 4000);
  };

  const undoMemoryDelete = () => {
    if (memoryDeleteTimerRef.current) {
      window.clearTimeout(memoryDeleteTimerRef.current);
      memoryDeleteTimerRef.current = null;
    }
    setPendingDeleteMemoryId(null);
  };

  const commitCheckInDelete = async (id: string) => {
    setPendingDeleteCheckInId((current) => (current === id ? null : current));
    try {
      await request<void>(`/api/check-ins/${id}`, { method: 'DELETE' });
      await fetchCheckIns();
    } catch (error) {
      console.error(error);
    }
  };

  const requestDeleteCheckIn = (id: string) => {
    // Only one pending delete at a time — if another is already waiting, commit it now.
    if (pendingDeleteCheckInId && pendingDeleteCheckInId !== id) {
      if (checkInDeleteTimerRef.current) {
        window.clearTimeout(checkInDeleteTimerRef.current);
      }
      commitCheckInDelete(pendingDeleteCheckInId);
    }

    setPendingDeleteCheckInId(id);
    if (checkInDeleteTimerRef.current) {
      window.clearTimeout(checkInDeleteTimerRef.current);
    }
    checkInDeleteTimerRef.current = window.setTimeout(() => {
      commitCheckInDelete(id);
    }, 4000);
  };

  const undoCheckInDelete = () => {
    if (checkInDeleteTimerRef.current) {
      window.clearTimeout(checkInDeleteTimerRef.current);
      checkInDeleteTimerRef.current = null;
    }
    setPendingDeleteCheckInId(null);
  };

  const commitMissionCompletionDelete = async (id: string) => {
    setPendingDeleteMissionId((current) => (current === id ? null : current));
    try {
      await request<void>(`/api/missions/completions/${id}`, { method: 'DELETE' });
      await fetchMissionCompletions();
    } catch (error) {
      console.error(error);
    }
  };

  const requestDeleteMissionCompletion = (id: string) => {
    // Only one pending delete at a time — if another is already waiting, commit it now.
    if (pendingDeleteMissionId && pendingDeleteMissionId !== id) {
      if (missionCompletionDeleteTimerRef.current) {
        window.clearTimeout(missionCompletionDeleteTimerRef.current);
      }
      commitMissionCompletionDelete(pendingDeleteMissionId);
    }

    setPendingDeleteMissionId(id);
    if (missionCompletionDeleteTimerRef.current) {
      window.clearTimeout(missionCompletionDeleteTimerRef.current);
    }
    missionCompletionDeleteTimerRef.current = window.setTimeout(() => {
      commitMissionCompletionDelete(id);
    }, 4000);
  };

  const undoMissionCompletionDelete = () => {
    if (missionCompletionDeleteTimerRef.current) {
      window.clearTimeout(missionCompletionDeleteTimerRef.current);
      missionCompletionDeleteTimerRef.current = null;
    }
    setPendingDeleteMissionId(null);
  };

  if (profileMissing) {
    return (
      <main className="app-shell">
        <section className="panel profile-setup-panel">
          <div className="capybee-center-block">
            <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
            <CapyBeeBubble text={text.askName} />
          </div>
          <h1>{text.profileSetupTitle}</h1>
          <p>{text.profileSetupHint}</p>

          <form className="stack-form" onSubmit={createProfile}>
            <label>
              {text.nickname}
              <input value={setupNickname} onChange={(event) => setSetupNickname(event.target.value)} required />
            </label>

            <label>
              {text.birthYear}
              <input
                type="number"
                value={setupBirthYear}
                onChange={(event) => setSetupBirthYear(event.target.value)}
                placeholder="2014"
              />
            </label>

            <label>
              {text.language}
              <select
                value={setupLocale}
                onChange={(event) => setSetupLocale(event.target.value as 'en' | 'pl')}
              >
                <option value="en">{text.english}</option>
                <option value="pl">{text.polish}</option>
              </select>
            </label>

            <label>
              {text.avatarSeed}
              <input value={setupAvatarSeed} onChange={(event) => setSetupAvatarSeed(event.target.value)} />
            </label>

            <button type="submit" className="primary-button" disabled={setupLoading}>
              {setupLoading ? text.saving : text.setUp}
            </button>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <header className="auth-topbar panel">
        <div className="auth-user">
          <CapyBeeAvatar src={capyBeeAvatar.faceHappy} size={48} alt={user.displayName} className="avatar" />
          <div>
            <h1>{profile ? profile.nickname : user.displayName}</h1>
            {profile ? null : <p>{user.email}</p>}
          </div>
        </div>
        <div className="auth-actions">
          <button
            type="button"
            className={activeTab === 'profile' ? 'profile-nav-button active' : 'profile-nav-button'}
            onClick={() => setActiveTab('profile')}
            aria-label={text.profile}
            aria-current={activeTab === 'profile' ? 'page' : undefined}
          >
            <ProfileIcon active={activeTab === 'profile'} />
          </button>
          <label className="locale-control">
            <select
              aria-label={text.language}
              value={locale}
              onChange={(event) => {
                const nextLocale = event.target.value as 'en' | 'pl';
                setLocale(nextLocale);
                if (profile) {
                  updateProfile({ preferredLocale: nextLocale });
                }
              }}
            >
              <option value="en">EN</option>
              <option value="pl">PL</option>
            </select>
          </label>
          <a href="/logout" className="secondary-button">{text.logout}</a>
        </div>
      </header>

      <section className="content-area">
        {activeTab === 'home' ? (
          <>
            <section className="panel capybee-center-block">
              <CapyBeeAvatar src={homeAvatar} size={160} />
              <CapyBeeBubble text={homeAvatarBubble} />
            </section>

            <section className="panel">
              <h2>{text.homeTitle}</h2>
              <form className="stack-form" onSubmit={submitCheckIn}>
                <div className="mood-selector compact">
                  {(['heavy', 'okay', 'good'] as Mood[]).map((value) => (
                    <label key={value} className={`mood-option ${mood === value ? 'active' : ''}`}>
                      <input
                        type="radio"
                        name="mood"
                        value={value}
                        checked={mood === value}
                        onChange={(event) => setMood(event.target.value as Mood)}
                      />
                      <CapyBeeAvatar src={moodPickerAvatar(value)} size={72} className="mood-avatar" />
                      <span className="mood-label">{moodLabels[value]}</span>
                    </label>
                  ))}
                </div>
                <textarea
                  className="check-in-note"
                  rows={3}
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  placeholder={text.checkInPlaceholder}
                />
                <button type="submit" className="primary-button" disabled={checkInLoading}>
                  {checkInLoading ? text.saving : text.save}
                </button>
              </form>

              {missionSuggestionVisible ? (
                <button className="suggestion-card" onClick={() => setActiveTab('missions')}>
                  <CapyBeeAvatar src={capyBeeAvatar.suggesting} size={96} />
                  <span>{text.missionSuggestion}</span>
                </button>
              ) : null}
            </section>

            <section className="panel">
              <h3>{text.honeycombProgress}</h3>
              <div className="honeycomb-card">
                <h4 className="honeycomb-heading">{locale === 'pl' ? text.hiveTitle : 'Your hive'}</h4>
                <HoneycombMap
                  cells={homeHoneycombCells}
                  ariaLabel={locale === 'pl' ? text.hiveAriaLabel : 'Your hive - progress'}
                  animatedCellId={homeAnimatedCellId}
                />
              </div>
            </section>

            <section className="panel">
              <h3>{text.recentCheckins}</h3>
              {visibleCheckIns.length === 0 ? <p>{text.noItems}</p> : (
                <div className="list-stack">
                  {[...visibleCheckIns].reverse().slice(0, 10).map((entry, index) => {
                    const isPendingDelete = pendingDeleteCheckInId === entry.id;
                    return (
                      <motion.article
                        key={entry.id}
                        className={`list-card ${isPendingDelete ? 'pending-delete' : ''}`}
                        initial={{ opacity: 0, y: 8 }}
                        animate={{ opacity: isPendingDelete ? 0.55 : 1, y: 0 }}
                        transition={{ delay: index * 0.04 }}
                      >
                        <div className="line-between checkin-row">
                          <div className="checkin-headline">
                            <CapyBeeAvatar src={checkInListFace(entry.mood)} size={32} />
                            <strong>{entry.mood}</strong>
                          </div>
                        </div>
                        {entry.note ? <p>{entry.note}</p> : null}
                        <div className="line-between">
                          <span>{new Date(entry.createdAt).toLocaleDateString(locale)}</span>
                          <button
                            className="icon-delete-button"
                            onClick={() => requestDeleteCheckIn(entry.id)}
                            aria-label={text.deleteCheckIn}
                          >
                            <TrashIcon />
                          </button>
                        </div>
                      </motion.article>
                    );
                  })}
                </div>
              )}
            </section>
          </>
        ) : null}

        {activeTab === 'missions' ? (
          <>
            <section className="panel">
              <div className="title-with-avatar">
                <CapyBeeAvatar src={capyBeeAvatar.waving} size={96} />
                <h2>{text.missions}</h2>
              </div>

              <img
                src={missionsTabImage}
                alt=""
                draggable={false}
                className="world-tab-header-image"
              />

              {visibleMissions.length === 0 ? (
                <div className="capybee-center-block">
                  <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
                  <CapyBeeBubble text={text.missionEmpty} />
                </div>
              ) : (
                <div className="list-stack">
                  {visibleMissions.map((mission) => {
                    const isExpanded = expandedMissionId === mission.id;
                    const isSaving = savingMissionId === mission.id;
                    const missionNote = missionNotes[mission.id] ?? '';
                    const showCheerFace = cheerMissionId === mission.id;

                    return (
                      <article key={mission.id} className={['list-card', 'mission-card'].join(' ').trim()}>
                        <div className="mission-header-row">
                          <CapyBeeAvatar
                            src={showCheerFace ? capyBeeAvatar.faceHappy : capyBeeAvatar.faceOkay}
                            size={36}
                          />
                          <div className="mission-heading-block">
                            <h3>{localizeMissionTitle(mission.code, mission.title, locale)}</h3>
                            <span className="mission-time-pill">{localizeMissionTimeHint(mission.timeHint, locale)}</span>
                          </div>
                        </div>

                        {!isExpanded ? (
                          <div className="mission-action-row">
                            <button
                              className="primary-button mission-primary-button"
                              onClick={() => setExpandedMissionId(mission.id)}
                            >
                              {text.markComplete}
                            </button>
                            <button
                              className="mission-skip-link"
                              onClick={() => skipMission(mission.id)}
                              disabled={skipPendingMissionId !== null || isSaving}
                            >
                              {text.missionNotToday}
                            </button>
                          </div>
                        ) : (
                          <div className="mission-note-block">
                            <textarea
                              rows={2}
                              className="check-in-note"
                              value={missionNote}
                              onChange={(event) => setMissionNotes((current) => ({
                                ...current,
                                [mission.id]: event.target.value
                              }))}
                              placeholder={text.missionOptionalNote}
                            />
                            <div className="mission-note-actions">
                              <button
                                className="primary-button mission-primary-button"
                                onClick={() => completeMission(mission.id, missionNote)}
                                disabled={isSaving}
                              >
                                {isSaving ? text.saving : text.missionSave}
                              </button>
                              <button
                                className="secondary-button"
                                onClick={() => setExpandedMissionId(null)}
                                disabled={isSaving}
                              >
                                {text.missionBack}
                              </button>
                            </div>
                          </div>
                        )}
                      </article>
                    );
                  })}
                </div>
              )}
            </section>

            <section className="panel">
              <h3>{text.honeycombProgress}</h3>
              <div className="honeycomb-card">
                <h4 className="honeycomb-heading">{locale === 'pl' ? text.hiveTitle : 'Your hive'}</h4>
                <HoneycombMap
                  cells={homeHoneycombCells}
                  ariaLabel={locale === 'pl' ? text.hiveAriaLabel : 'Your hive - progress'}
                  animatedCellId={homeAnimatedCellId}
                />
              </div>
            </section>

            <section className="panel">
              <h3>{text.completionHistory}</h3>
              {visibleMissionCompletions.length === 0 ? (
                <div className="capybee-center-block">
                  <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
                  <p className="empty-copy">{text.missionHistoryEmpty}</p>
                </div>
              ) : (
                <div className="list-stack">
                  {[...visibleMissionCompletions].reverse().slice(0, 20).map((entry) => {
                    const isPendingDelete = pendingDeleteMissionId === entry.id;
                    return (
                      <motion.article
                        key={entry.id}
                        className={`list-card ${isPendingDelete ? 'pending-delete' : ''}`}
                        initial={{ opacity: 0, y: 8 }}
                        animate={{ opacity: isPendingDelete ? 0.55 : 1, y: 0 }}
                        transition={{ duration: 0.18 }}
                      >
                        <div className="line-between checkin-row">
                          <div className="checkin-headline">
                            <CapyBeeAvatar src={capyBeeAvatar.faceHappy} size={32} />
                            <strong>{localizeMissionTitle(entry.missionCode, entry.title, locale)}</strong>
                          </div>
                        </div>
                        {entry.note ? <p>{entry.note}</p> : null}
                        <div className="line-between">
                          <span>{new Date(entry.completedAt).toLocaleDateString(locale)}</span>
                          <button
                            className="icon-delete-button"
                            onClick={() => requestDeleteMissionCompletion(entry.id)}
                            aria-label={text.deleteMission}
                          >
                            <TrashIcon />
                          </button>
                        </div>
                      </motion.article>
                    );
                  })}
                </div>
              )}
            </section>
          </>
        ) : null}

        {activeTab === 'friendships' ? (
          <>
            <section className="panel">
              <div className="title-with-avatar">
                <CapyBeeAvatar src={capyBeeAvatar.waving} size={96} />
                <h2>{text.friendships}</h2>
              </div>

              <img
                src={friendshipsTabImage}
                alt=""
                draggable={false}
                className="world-tab-header-image"
              />

              <form className="stack-form" onSubmit={addFriendship}>
                <label>
                  {text.person}
                  <input value={friendLabel} onChange={(event) => setFriendLabel(event.target.value)} required />
                </label>
                <label>
                  {text.friendshipStageLabel}
                  <FriendshipStageSelector
                    value={friendStage}
                    onChange={(stage) => setFriendStage(stage)}
                    getLabel={(stage) => getFriendshipStageLabel(stage)}
                    ariaLabel={locale === 'pl' ? text.friendshipStageAriaLabel : 'Friendship stage'}
                  />
                </label>
                <label>
                  {text.note}
                  <input
                    value={friendNote}
                    onChange={(event) => setFriendNote(event.target.value)}
                    placeholder={text.friendshipNotePlaceholder}
                  />
                </label>
                <button className="primary-button" type="submit">{locale === 'pl' ? 'Dodaj' : 'Add'}</button>
              </form>
            </section>

            <section className="panel">
              <h3>{text.honeycombProgress}</h3>
              <div className="honeycomb-card">
                <h4 className="honeycomb-heading">{locale === 'pl' ? text.hiveTitle : 'Your hive'}</h4>
                <HoneycombMap
                  cells={homeHoneycombCells}
                  ariaLabel={locale === 'pl' ? text.hiveAriaLabel : 'Your hive - progress'}
                  animatedCellId={homeAnimatedCellId}
                />
              </div>
            </section>

            <section className="panel">
              {sortedFriendships.length === 0 ? (
                <div className="capybee-center-block friendship-empty-state">
                  <CapyBeeAvatar src={capyBeeAvatar.waving} size={128} />
                  <p className="friendship-empty-state__copy">{text.friendshipEmptyState}</p>
                </div>
              ) : (
                <div className="list-stack">
                  {sortedFriendships.map((entry) => {
                    const stage = (entry.stage as FriendshipStage) || 'noticed';
                    const stageMeta = STAGE_META[stage] ?? STAGE_META.noticed;
                    const isPendingDelete = pendingDeleteId === entry.id;

                    return (
                      <motion.article
                        key={entry.id}
                        className={`list-card friendship-card ${isPendingDelete ? 'pending-delete' : ''}`}
                        initial={{ opacity: 0, y: 8 }}
                        animate={{ opacity: isPendingDelete ? 0.55 : 1, y: 0 }}
                        transition={{ duration: 0.18 }}
                        style={{ borderLeft: `4px solid ${stageMeta.colorToken}` }}
                      >
                        <div className="friendship-card__header">
                          <div className="friendship-card__identity">
                            <span className="friendship-card__icon">{stageMeta.icon}</span>
                            <strong>{entry.personLabel}</strong>
                          </div>
                          <div className="friendship-card__meta">
                            <span className="friendship-card__badge">{getFriendshipStageLabel(stage)}</span>
                            <span className="friendship-card__date">{new Date(entry.createdAt).toLocaleDateString(locale)}</span>
                          </div>
                        </div>
                        {entry.note ? <p className="friendship-card__note">{entry.note}</p> : null}
                        <div className="line-between">
                          <span></span>
                          <button
                            className="icon-delete-button"
                            onClick={() => handleFriendshipRemoveClick(entry.id)}
                            aria-label={text.friendshipRemove}
                          >
                            <TrashIcon />
                          </button>
                        </div>
                      </motion.article>
                    );
                  })}
                </div>
              )}
            </section>
          </>
        ) : null}

        {activeTab === 'memories' ? (
          <>
            <section className="panel">
              <div className="title-with-avatar">
                <CapyBeeAvatar src={capyBeeAvatar.waving} size={96} />
                <h2>{text.memories}</h2>
              </div>
              <div className="segment-control">
                <button
                  className={worldType === 'old_world' ? 'segment active' : 'segment'}
                  onClick={() => setWorldType('old_world')}
                >
                  {text.oldWorld}
                </button>
                <button
                  className={worldType === 'new_world' ? 'segment active' : 'segment'}
                  onClick={() => setWorldType('new_world')}
                >
                  {text.newWorld}
                </button>
              </div>

              <img
                src={worldType === 'old_world' ? oldWorldTabImage : newWorldTabImage}
                alt=""
                draggable={false}
                className="world-tab-header-image"
              />

              <form className="stack-form" onSubmit={addMemory}>
                <label>
                  {text.title}
                  <input value={memoryTitle} onChange={(event) => setMemoryTitle(event.target.value)} />
                </label>
                <label>
                  {text.story}
                  <textarea
                    rows={3}
                    className="check-in-note"
                    value={memoryText}
                    onChange={(event) => setMemoryText(event.target.value)}
                    required
                  />
                </label>
                <button
                  type="button"
                  className={memoryFavorite ? 'memory-favorite-toggle checked' : 'memory-favorite-toggle'}
                  onClick={() => setMemoryFavorite((current) => !current)}
                  aria-pressed={memoryFavorite}
                  aria-label={text.favoriteMemory}
                >
                  <span className="memory-favorite-toggle__icon" aria-hidden="true">⭐</span>
                  <span className="memory-favorite-toggle__label">{text.favoriteMemory}</span>
                </button>
                <button className="primary-button" type="submit">{text.addMemory}</button>
              </form>
            </section>

            <section className="panel">
              <h3>{text.honeycombProgress}</h3>
              <div className="honeycomb-card">
                <h4 className="honeycomb-heading">{locale === 'pl' ? text.hiveTitle : 'Your hive'}</h4>
                <HoneycombMap
                  cells={homeHoneycombCells}
                  ariaLabel={locale === 'pl' ? text.hiveAriaLabel : 'Your hive - progress'}
                  animatedCellId={homeAnimatedCellId}
                />
              </div>
            </section>

            <section className="panel">
              {memories.length === 0 ? (
                <div className="capybee-center-block">
                  <CapyBeeAvatar
                    src={worldType === 'old_world' ? capyBeeAvatar.empathetic : capyBeeAvatar.default}
                    size={120}
                  />
                  <CapyBeeBubble text={worldType === 'old_world' ? text.memoryOldEmpty : text.memoryNewEmpty} />
                </div>
              ) : (
                <div className="list-stack">
                  {visibleMemories.map((entry) => (
                    <article key={entry.id} className="list-card memory-card">
                      <div className="line-between">
                        <strong>{entry.title || (locale === 'pl' ? text.memoryFallbackTitle : 'Memory')}</strong>
                        <button
                          className={entry.isFavorite ? 'favorite-star active' : 'favorite-star'}
                          onClick={() => toggleFavorite(entry)}
                          aria-label={text.favoriteMemory}
                          aria-pressed={entry.isFavorite}
                        >
                          <StarIcon filled={entry.isFavorite} />
                        </button>
                      </div>
                      {entry.textContent ? <p className="memory-story-preview">{entry.textContent}</p> : null}
                      <div className="memory-card-footer">
                        <span className="memory-card-date">{new Date(entry.createdAt).toLocaleDateString(locale)}</span>
                        <button
                          className="icon-delete-button"
                          onClick={() => requestDeleteMemory(entry.id)}
                          aria-label={text.deleteMemory}
                        >
                          <TrashIcon />
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </>
        ) : null}

        {activeTab === 'profile' && profile ? (
          <>
            <section className="panel">
              <div className="title-with-avatar">
              <CapyBeeAvatar src={capyBeeAvatar.default} size={80} />
              <h2>{text.profile}</h2>
            </div>
            <form
              className="stack-form"
              onSubmit={(event) => {
                event.preventDefault();
                updateProfile(profile);
              }}
            >
              <label>
                {text.nickname}
                <input
                  value={profile.nickname}
                  onChange={(event) => setProfile({ ...profile, nickname: event.target.value })}
                />
              </label>

              <label>
                {text.birthYear}
                <input
                  type="number"
                  value={profile.birthYear ?? ''}
                  onChange={(event) => setProfile({
                    ...profile,
                    birthYear: event.target.value ? Number(event.target.value) : undefined
                  })}
                />
              </label>

              <label>
                {text.language}
                <select
                  aria-label={text.language}
                  value={profile.preferredLocale}
                  onChange={(event) => setProfile({ ...profile, preferredLocale: event.target.value as 'en' | 'pl' })}
                >
                  <option value="en">{text.english}</option>
                  <option value="pl">{text.polish}</option>
                </select>
              </label>

              <label>
                {text.avatarSeed}
                <input
                  value={profile.avatarSeed ?? ''}
                  onChange={(event) => setProfile({ ...profile, avatarSeed: event.target.value })}
                />
              </label>

              <div className="profile-switch-row">
                <span>{text.profileActive}</span>
                <button
                  type="button"
                  role="switch"
                  aria-checked={profile.active}
                  aria-label={text.profileActive}
                  className={profile.active ? 'profile-switch checked' : 'profile-switch'}
                  onClick={() => setProfile({ ...profile, active: !profile.active })}
                >
                  <span className="profile-switch__thumb" aria-hidden="true" />
                </button>
              </div>

              <button className="primary-button" type="submit">{text.save}</button>
            </form>
            </section>
          </>
        ) : null}
      </section>

      {activeFeedback ? (
        <aside
          className={[
            'capybee-toast',
            'mission-toast',
            feedbackFadeOut ? 'feedback-fade-out' : 'feedback-fade-in'
          ].join(' ').trim()}
        >
          <CapyBeeAvatar src={activeFeedback.avatar} size={48} />
          <span>{activeFeedback.phrase}</span>
        </aside>
      ) : null}

      <FriendshipToast toast={friendshipToast} onDismiss={() => setFriendshipToast(null)} />

      {pendingDeleteId ? (
        <aside className="skip-undo-toast" role="status" aria-live="polite">
          <span>{text.friendshipRemoved}</span>
          <button type="button" onClick={undoFriendshipRemove}>{text.friendshipUndo}</button>
        </aside>
      ) : null}

      {skipUndoMissionId ? (
        <aside className="skip-undo-toast" role="status" aria-live="polite">
          <span>{text.missionSkipped}</span>
          <button type="button" onClick={undoMissionSkip}>{text.missionUndoSkip}</button>
        </aside>
      ) : null}

      {pendingDeleteMemoryId ? (
        <aside className="skip-undo-toast" role="status" aria-live="polite">
          <span>{text.memoryDeleted}</span>
          <button type="button" onClick={undoMemoryDelete}>{text.memoryUndoDelete}</button>
        </aside>
      ) : null}

      {pendingDeleteCheckInId ? (
        <aside className="skip-undo-toast" role="status" aria-live="polite">
          <span>{text.checkInDeleted}</span>
          <button type="button" onClick={undoCheckInDelete}>{text.checkInUndoDelete}</button>
        </aside>
      ) : null}

      {pendingDeleteMissionId ? (
        <aside className="skip-undo-toast" role="status" aria-live="polite">
          <span>{text.missionDeleted}</span>
          <button type="button" onClick={undoMissionCompletionDelete}>{text.missionUndoDelete}</button>
        </aside>
      ) : null}

      <nav className="bottom-nav" aria-label={text.navLabel}>
        {bottomNavItems.map(({ key, label, Icon }) => {
          const isActive = activeTab === key;
          return (
            <button
              key={key}
              type="button"
              className={isActive ? 'nav-item active' : 'nav-item'}
              onClick={() => setActiveTab(key)}
              aria-current={isActive ? 'page' : undefined}
            >
              <Icon active={isActive} />
              <span>{label}</span>
            </button>
          );
        })}
      </nav>
    </main>
  );
}
