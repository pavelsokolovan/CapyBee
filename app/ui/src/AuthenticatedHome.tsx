import { useEffect, useMemo, useRef, useState, type ComponentType } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import { CapyBeeAvatar, CapyBeeBubble, capyBeeAvatar, sameCalendarDay } from './capybee';
import { useCapyBeePhrase } from './hooks/useCapyBeePhrase';
import { useFriendshipAddedPhrase } from './hooks/useFriendshipAddedPhrase';
import { capybeePhrases, type CapyBeePhrasePoolKey } from './data/capybeePhrases';
import { HoneycombMap } from './components/HoneycombMap';
import { FriendshipStageSelector } from './components/FriendshipStageSelector';
import { FriendshipToast } from './components/FriendshipToast';
import { OnboardingTutorial } from './components/OnboardingTutorial';
import { HomeIcon, MissionsIcon, FriendshipsIcon, MemoriesIcon, ProfileIcon } from './components/NavIcons';
import {
  CategoryAllIcon,
  CategoryExploreIcon,
  CategoryNewWorldIcon,
  CategoryOldWorldIcon,
  CategoryReflectionIcon,
  CategorySocialIcon,
} from './components/MissionCategoryIcons';
import { useHoneycombCells } from './hooks/useHoneycombCells';
import type { UseHoneycombCellsInput } from './hooks/useHoneycombCells';
import oldWorldTabImage from './assets/honeycomb/old-world-tab.png';
import newWorldTabImage from './assets/honeycomb/new-world-tab.png';
import missionsTabImage from './assets/honeycomb/missions-tab.png';
import friendshipsTabImage from './assets/honeycomb/friedships-tab.png';
import { STAGE_META, type FriendshipStage } from './constants/friendshipStages';
import { enqueueAction } from './offline/queueStore';
import { flushQueue, startSyncLoop, onSyncStatusChange } from './offline/syncEngine';

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
  hasSeenOnboarding: boolean;
  createdAt: string;
  updatedAt: string;
}

type MissionCategoryKey = 'all' | 'social' | 'new_world' | 'old_world' | 'reflection' | 'exploration';

interface Mission {
  id: string;
  code: string;
  title: string;
  timeHint: string;
  description: string;
  category?: string;
  active: boolean;
}

const missionCategoryOrder: MissionCategoryKey[] = [
  'all',
  'social',
  'new_world',
  'old_world',
  'reflection',
  'exploration'
];

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
  offer_help_classmate_small: 'Zaproponuj koledze lub koleżance pomoc w czymś drobnym',
  invite_lunch_sit_together: 'Zaproś kogoś, żeby usiadł z tobą na lunchu',
  learn_classmate_name: 'Naucz się imienia kogoś z klasy, kogo jeszcze nie znałeś/aś',
  share_something_funny: 'Podziel się z kimś czymś zabawnym',
  say_thank_you_helper: 'Podziękuj komuś, kto ci ostatnio pomógł/pomogła',
  try_good_morning_local_language: 'Spróbuj powiedzieć dzień dobry w nowym języku',
  find_nearest_park: 'Znajdź najbliższy park lub zielone miejsce blisko domu',
  notice_new_neighborhood_sound: 'Zauważ jeden dźwięk w nowej okolicy, którego nie było w starej',
  find_busiest_market_day: 'Dowiedz się, w który dzień lokalny sklep lub targ jest najbardziej ruchliwy',
  listen_song_reminds_home: 'Posłuchaj piosenki, która przypomina ci dom',
  write_favorite_holiday_tradition: 'Zapisz swoją ulubioną tradycję świąteczną z domu',
  describe_old_bedroom_memory: 'Opisz z pamięci swój stary pokój',
  recall_joke_from_home: 'Przypomnij sobie żart, który ktoś z domu ci opowiadał',
  name_one_thing_proud_week: 'Wskaż jedną rzecz, z której jesteś dumny/a w tym tygodniu',
  write_something_surprised_today: 'Zapisz jedną rzecz, która cię dziś zaskoczyła',
  one_word_today_felt: 'Wymyśl jedno słowo, które opisuje dzisiejszy dzień',
  notice_small_win_today: 'Zauważ jedną małą rzecz, która poszła lepiej, niż się spodziewałeś/aś',
  find_nearest_library: 'Dowiedz się, gdzie jest biblioteka blisko domu',
  look_up_local_holiday: 'Sprawdź jedno święto obchodzone tutaj, którego wcześniej nie znałeś/aś',
  find_new_walk_route: 'Znajdź nową trasę na spacer lub rower w tym tygodniu',
  discover_recess_game: 'Dowiedz się, w co bawią się dzieci na przerwie'
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
  offer_help_classmate_small: 'Offer to help a classmate with something small',
  invite_lunch_sit_together: 'Invite someone to sit with you at lunch',
  learn_classmate_name: "Learn one classmate's name you didn't know",
  share_something_funny: 'Share something funny with someone',
  say_thank_you_helper: 'Say thank you to someone who helped you this week',
  try_good_morning_local_language: 'Try saying good morning in the new language',
  find_nearest_park: 'Find the nearest park or green space near your home',
  notice_new_neighborhood_sound: "Notice one sound your new neighborhood makes that your old one didn't",
  find_busiest_market_day: 'Find out what day the local market or store is busiest',
  listen_song_reminds_home: 'Listen to a song that reminds you of home',
  write_favorite_holiday_tradition: 'Write down your favorite holiday tradition from home',
  describe_old_bedroom_memory: 'Describe your old bedroom from memory',
  recall_joke_from_home: 'Think of a joke someone back home used to tell you',
  name_one_thing_proud_week: "Name one thing you're proud of from this week",
  write_something_surprised_today: 'Write down one thing that surprised you today',
  one_word_today_felt: 'Think of one word that describes how today felt',
  notice_small_win_today: 'Notice one small thing that went better than you expected',
  find_nearest_library: 'Find out where the library is near your home',
  look_up_local_holiday: "Look up one holiday celebrated here that's new to you",
  find_new_walk_route: 'Find one new route to walk or bike this week',
  discover_recess_game: 'Discover what game kids play at recess here'
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

function ChevronIcon({ expanded }: { expanded: boolean }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 20 20"
      className="mission-accordion-chevron"
      style={{ transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)' }}
    >
      <path d="M5 7.5L10 12.5L15 7.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

interface MissionAccordionRowProps {
  mission: Mission;
  locale: 'en' | 'pl';
  note: string;
  isExpanded: boolean;
  isSaving: boolean;
  skipPending: boolean;
  showCheerFace: boolean;
  prefersReducedMotion: boolean;
  onToggle: () => void;
  onNoteChange: (nextValue: string) => void;
  onComplete: (missionId: string, noteValue: string) => void;
  onSkip: (missionId: string) => void;
}

function MissionAccordionRow({
  mission,
  locale,
  note,
  isExpanded,
  isSaving,
  skipPending,
  showCheerFace,
  prefersReducedMotion,
  onToggle,
  onNoteChange,
  onComplete,
  onSkip
}: MissionAccordionRowProps) {
  const text = copy[locale];
  const shouldShowExpandedBody = isExpanded && !isSaving;

  return (
    <article className={['list-card mission-card', isExpanded ? 'mission-card--expanded' : ''].join(' ').trim()}>
      <button
        type="button"
        className="mission-accordion-header"
        onClick={onToggle}
        aria-expanded={isExpanded}
        aria-controls={`mission-panel-${mission.id}`}
      >
        <CapyBeeAvatar src={showCheerFace ? capyBeeAvatar.faceHappy : capyBeeAvatar.faceOkay} size={36} />
        <div className="mission-accordion-main">
          <span className="mission-accordion-title">
            {localizeMissionTitle(mission.code, mission.title, locale)}
          </span>
        </div>
        <span className="mission-accordion-time-pill">{localizeMissionTimeHint(mission.timeHint, locale)}</span>
        <ChevronIcon expanded={isExpanded} />
      </button>

      <motion.div
        id={`mission-panel-${mission.id}`}
        initial={false}
        animate={shouldShowExpandedBody ? { opacity: 1, height: 'auto' } : { opacity: 0, height: 0 }}
        transition={prefersReducedMotion ? { duration: 0 } : { duration: 0.24, ease: 'easeInOut' }}
        style={{ overflow: 'hidden' }}
        className="mission-accordion-body"
      >
        <div className="mission-accordion-body-inner">
          <textarea
            rows={2}
            className="check-in-note mission-accordion-note"
            value={note}
            onChange={(event) => onNoteChange(event.target.value)}
            placeholder={text.missionOptionalNote}
          />

          <div className="mission-accordion-actions">
            <button
              type="button"
              className="primary-button mission-primary-button"
              onClick={() => onComplete(mission.id, note)}
              disabled={isSaving}
            >
              {isSaving ? text.saving : text.markComplete}
            </button>

            <button
              type="button"
              className="mission-skip-link"
              onClick={() => onSkip(mission.id)}
              disabled={skipPending || isSaving}
            >
              {text.missionNotToday}
            </button>
          </div>
        </div>
      </motion.div>
    </article>
  );
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
    missionEmptyCategory: 'Nothing here right now - try another group, or tap All.',
    missionCategoryAll: 'All',
    missionCategorySocial: 'People',
    missionCategoryNewWorld: 'New World',
    missionCategoryOldWorld: 'Old World',
    missionCategoryReflection: 'Feelings',
    missionCategoryExploration: 'Explore',
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
    friendshipStageAriaLabel: 'Friendship stage',
    onboardingWelcomeTitle: "Hey, I'm CapyBee.",
    onboardingWelcomeBody: "I'll show you around - quick, promise.",
    onboardingStartTitle: 'This is where you check in.',
    onboardingStartBody: "Pick heavy, okay, or good - whatever's true today. No wrong answer, and I'll always react to what you tell me.",
    onboardingHiveTitle: 'This is your hive.',
    onboardingHiveBody: 'Every check-in, mission, and memory adds one cell. It starts empty on purpose - we fill it together, over time.',
    onboardingHistoryTitle: 'Your check-ins stay here.',
    onboardingHistoryBody: 'This list helps you look back at your days. You can see how things change over time, one check-in at a time.',
    onboardingMissionsTitle: 'Missions live here.',
    onboardingMissionsBody: "I'll suggest one small real-world thing to try. Do it, or tap Not today - skipped missions just wait at the bottom, they never disappear.",
    onboardingFriendshipsTitle: 'Friendships are private, just for you.',
    onboardingFriendshipsBody: 'Noticed someone? Said hi? Log it here. No one else ever sees this list - not even other kids using CapyBee.',
    onboardingMemoriesTitle: 'Both your worlds live here.',
    onboardingMemoriesBody: "Old World keeps what you miss - golden, safe, always yours. New World grows as you build your life here. Neither one replaces the other.",
    onboardingProfileTitle: 'One more thing.',
    onboardingProfileBody: 'This is your profile, up here. Language and a few settings live behind it - you can peek anytime.',
    onboardingDoneTitle: "That's the hive.",
    onboardingDoneBody: "Come back anytime - I'm here.",
    onboardingNext: 'Next',
    onboardingSkip: 'Skip',
    onboardingStart: "Let's go"
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
    missionEmptyCategory: 'Nic tu teraz nie ma - spróbuj innej grupy albo kliknij Wszystkie.',
    missionCategoryAll: 'Wszystkie',
    missionCategorySocial: 'Ludzie',
    missionCategoryNewWorld: 'Nowy świat',
    missionCategoryOldWorld: 'Stary świat',
    missionCategoryReflection: 'Uczucia',
    missionCategoryExploration: 'Odkrywaj',
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
    friendshipStageAriaLabel: 'Etap relacji',
    onboardingWelcomeTitle: 'Hej, jestem CapyBee.',
    onboardingWelcomeBody: 'Pokażę ci, co tu jest - szybko, obiecuję.',
    onboardingStartTitle: 'Tu mówisz, jak się dziś czujesz.',
    onboardingStartBody: 'Wybierz ciężko, okej albo dobrze - co jest prawdą dziś. Nie ma złej odpowiedzi, a ja zawsze zareaguję na to, co mi powiesz.',
    onboardingHiveTitle: 'To twój ul.',
    onboardingHiveBody: 'Każdy check-in, misja i wspomnienie dodają jedną komórkę. Zaczyna się pusty specjalnie - zapełniamy go razem, po trochu.',
    onboardingHistoryTitle: 'Tu zostają twoje check-iny.',
    onboardingHistoryBody: 'Ta lista pomaga wracać do twoich dni. Możesz zobaczyć, jak wszystko zmienia się z czasem, krok po kroku.',
    onboardingMissionsTitle: 'Tu mieszkają misje.',
    onboardingMissionsBody: 'Zaproponuję ci jedną małą, prawdziwą rzecz do zrobienia. Zrób ją albo kliknij Nie dziś - pominięte misje po prostu czekają na dole, nigdy nie znikają.',
    onboardingFriendshipsTitle: 'Relacje są prywatne, tylko dla ciebie.',
    onboardingFriendshipsBody: 'Zauważyłeś kogoś? Powiedziałeś cześć? Zapisz to tutaj. Nikt inny tego nie widzi - nawet inne dzieci używające CapyBee.',
    onboardingMemoriesTitle: 'Oba twoje światy są tutaj.',
    onboardingMemoriesBody: 'Stary Świat trzyma to, za czym tęsknisz - złoty, bezpieczny, zawsze twój. Nowy Świat rośnie, gdy budujesz tu swoje życie. Żaden nie zastępuje drugiego.',
    onboardingProfileTitle: 'I jeszcze jedno.',
    onboardingProfileBody: 'To twój profil, tutaj na górze. Język i kilka ustawień są za nim - możesz tam zajrzeć, kiedy chcesz.',
    onboardingDoneTitle: 'To twój ul.',
    onboardingDoneBody: 'Wracaj tu, kiedy chcesz - jestem tu.',
    onboardingNext: 'Dalej',
    onboardingSkip: 'Pomiń',
    onboardingStart: 'Zaczynamy'
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
  const prefersReducedMotion = Boolean(useReducedMotion());
  const [activeTab, setActiveTab] = useState<TabKey>('home');
  const [profile, setProfile] = useState<ChildProfile | null>(null);
  const [profileMissing, setProfileMissing] = useState(false);
  const [locale, setLocale] = useState<'en' | 'pl'>('pl');
  const pickPhrase = useCapyBeePhrase(locale);
  const pickFriendshipPhrase = useFriendshipAddedPhrase(locale);

  const [mood, setMood] = useState<Mood>('okay');
  const [note, setNote] = useState('');
  const [checkIns, setCheckIns] = useState<CheckIn[]>([]);
  const [missionSuggestionVisible, setMissionSuggestionVisible] = useState(false);

  const [missions, setMissions] = useState<Mission[]>([]);
  const [missionCategory, setMissionCategory] = useState<MissionCategoryKey>('all');
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
  const [deletedFriendshipIds, setDeletedFriendshipIds] = useState<Set<string>>(new Set());
  const [pendingDeletedFriendship, setPendingDeletedFriendship] = useState<FriendshipEntry | null>(null);
  const [pendingDeleteTimer, setPendingDeleteTimer] = useState<number | null>(null);
  const [pendingDeleteCheckInId, setPendingDeleteCheckInId] = useState<string | null>(null);
  const [deletedCheckInIds, setDeletedCheckInIds] = useState<Set<string>>(new Set());
  const [pendingDeletedCheckIn, setPendingDeletedCheckIn] = useState<CheckIn | null>(null);
  const [pendingDeleteMissionId, setPendingDeleteMissionId] = useState<string | null>(null);
  const [deletedMissionCompletionIds, setDeletedMissionCompletionIds] = useState<Set<string>>(new Set());
  const [pendingDeletedMissionCompletion, setPendingDeletedMissionCompletion] = useState<MissionCompletion | null>(null);

  const [worldType, setWorldType] = useState<'old_world' | 'new_world'>('old_world');
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [allMemories, setAllMemories] = useState<MemoryEntry[]>([]);
  const [memoryTitle, setMemoryTitle] = useState('');
  const [memoryText, setMemoryText] = useState('');
  const [memoryFavorite, setMemoryFavorite] = useState(false);
  const [pendingDeleteMemoryId, setPendingDeleteMemoryId] = useState<string | null>(null);
  const [deletedMemoryIds, setDeletedMemoryIds] = useState<Set<string>>(new Set());
  const [pendingDeletedMemory, setPendingDeletedMemory] = useState<MemoryEntry | null>(null);
  const [homeAnimatedCellId, setHomeAnimatedCellId] = useState<string | null>(null);
  const [homeGreetingIndex] = useState(() => pickHomeGreetingIndex());
  const [tutorialActive, setTutorialActive] = useState(false);
  const [pendingSync, setPendingSync] = useState(0);
  const tabScrollPositions = useRef<Record<TabKey, number>>({
    home: 0,
    missions: 0,
    friendships: 0,
    memories: 0,
    profile: 0
  });


  const [setupNickname, setSetupNickname] = useState('');
  const [setupBirthYear, setSetupBirthYear] = useState('');
  const [setupLocale, setSetupLocale] = useState<'en' | 'pl'>('pl');
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
  const worldTypeRef = useRef<'old_world' | 'new_world'>(worldType);
  const memoriesFetchAbortRef = useRef<AbortController | null>(null);
  const moodSectionRef = useRef<HTMLElement>(null);
  const hiveSectionRef = useRef<HTMLElement>(null);
  const checkInHistorySectionRef = useRef<HTMLElement>(null);
  const missionsNavRef = useRef<HTMLButtonElement>(null);
  const friendshipsNavRef = useRef<HTMLButtonElement>(null);
  const memoriesNavRef = useRef<HTMLButtonElement>(null);
  const profileButtonRef = useRef<HTMLButtonElement>(null);

  const text = copy[locale];
  const missionCategoryIcon: Record<MissionCategoryKey, ComponentType<{ active?: boolean }>> = {
    all: CategoryAllIcon,
    social: CategorySocialIcon,
    new_world: CategoryNewWorldIcon,
    old_world: CategoryOldWorldIcon,
    reflection: CategoryReflectionIcon,
    exploration: CategoryExploreIcon
  };

  const missionCategoryLabelKey: Record<MissionCategoryKey, keyof typeof text> = {
    all: 'missionCategoryAll',
    social: 'missionCategorySocial',
    new_world: 'missionCategoryNewWorld',
    old_world: 'missionCategoryOldWorld',
    reflection: 'missionCategoryReflection',
    exploration: 'missionCategoryExploration'
  };

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
  const navRefByKey: Partial<Record<TabKey, typeof missionsNavRef>> = {
    missions: missionsNavRef,
    friendships: friendshipsNavRef,
    memories: memoriesNavRef
  };

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

  const changeTab = (nextTab: TabKey) => {
    tabScrollPositions.current[activeTab] = window.scrollY || window.pageYOffset || 0;
    setActiveTab(nextTab);
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
    const unsubscribe = onSyncStatusChange(setPendingSync);
    const stopLoop = startSyncLoop();
    return () => {
      unsubscribe();
      stopLoop();
    };
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
    const saveCurrentScroll = () => {
      tabScrollPositions.current[activeTab] = window.scrollY || window.pageYOffset || 0;
    };

    const handleScroll = () => {
      saveCurrentScroll();
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => {
      saveCurrentScroll();
      window.removeEventListener('scroll', handleScroll);
    };
  }, [activeTab]);

  useEffect(() => {
    const initialTop = 0;
    window.scrollTo({ top: initialTop, behavior: 'auto' });
  }, []);

  useEffect(() => {
    const restoreTop = tabScrollPositions.current[activeTab] ?? 0;
    window.scrollTo({ top: restoreTop, behavior: 'auto' });
  }, [activeTab]);

  useEffect(() => {
    if (profile) {
      setLocale(profile.preferredLocale);
    }
  }, [profile]);

  useEffect(() => {
    if (profile && !profile.hasSeenOnboarding) {
      setTutorialActive(true);
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
    worldTypeRef.current = worldType;
  }, [worldType]);

  useEffect(() => {
    if (activeTab === 'memories' && !profileMissing) {
      fetchAllMemories();
    }
  }, [activeTab]);

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

  const visibleMissions = useMemo(() => {
    if (missionCategory === 'all') return missions;
    return missions.filter((mission) => mission.category === missionCategory);
  }, [missions, missionCategory]);

  useEffect(() => {
    if (missionCategory === 'all') return;
    const hasAny = missions.some((mission) => mission.category === missionCategory);
    if (!hasAny) setMissionCategory('all');
  }, [missions, missionCategory]);

  const sortedFriendships = useMemo(() => {
    return [...friendships]
      .filter((entry) => !deletedFriendshipIds.has(entry.id))
      .sort((left, right) => {
      const leftTime = new Date(left.createdAt ?? left.updatedAt).getTime();
      const rightTime = new Date(right.createdAt ?? right.updatedAt).getTime();
      return rightTime - leftTime;
    });
  }, [friendships, deletedFriendshipIds]);

  const sortedMemories = useMemo(() => {
    return [...allMemories].sort((a, b) => {
      if (a.isFavorite !== b.isFavorite) {
        return a.isFavorite ? -1 : 1;
      }
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    });
  }, [allMemories]);

  const visibleMemories = useMemo(
    () => sortedMemories.filter((entry) => entry.id !== pendingDeleteMemoryId && !deletedMemoryIds.has(entry.id)),
    [sortedMemories, pendingDeleteMemoryId, deletedMemoryIds]
  );

  const visibleCheckIns = useMemo(
    () => checkIns.filter((entry) => entry.id !== pendingDeleteCheckInId && !deletedCheckInIds.has(entry.id)),
    [checkIns, pendingDeleteCheckInId, deletedCheckInIds]
  );

  const visibleMissionCompletions = useMemo(
    () => missionCompletions.filter((entry) => entry.id !== pendingDeleteMissionId && !deletedMissionCompletionIds.has(entry.id)),
    [missionCompletions, pendingDeleteMissionId, deletedMissionCompletionIds]
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


  const canReachBackend = async () => {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 2500);
    try {
      const res = await fetch('/api/health', {
        credentials: 'include',
        cache: 'no-store',
        signal: controller.signal
      });
      return res.ok;
    } catch {
      return false;
    } finally {
      window.clearTimeout(timeout);
    }
  };

  const redirectToLogin = () => {
    void (async () => {
      const reachable = await canReachBackend();
      if (!reachable) {
        return;
      }
      window.location.href = '/oauth2/authorization/google';
    })();
  };

  const request = async <T,>(path: string, init?: RequestInit): Promise<T> => {
    const res = await fetch(path, {
      credentials: 'include',
      cache: 'no-store',
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
    memoriesFetchAbortRef.current?.abort();
    const controller = new AbortController();
    memoriesFetchAbortRef.current = controller;
    try {
      const data = await request<MemoryEntry[]>(
        `/api/memories?worldType=${targetWorld}`,
        { signal: controller.signal }
      );
      if (worldTypeRef.current === targetWorld) {
        setMemories(data);
      }
    } catch (error) {
      if ((error as Error).name !== 'AbortError') {
        console.error(error);
      }
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
    const submittedMood = mood;
    const submittedNote = note;
    const clientId = crypto.randomUUID();
    const optimisticCheckIn: CheckIn = {
      id: clientId,
      mood: submittedMood,
      note: submittedNote,
      createdAt: new Date().toISOString()
    };

    setCheckIns((current) => [...current, optimisticCheckIn]);
    setNote('');
    setMissionSuggestionVisible(true);
    triggerFeedback({
      kind: 'checkin',
      phrase: pickPhrase(moodPoolKey(submittedMood)),
      avatar: moodReactionAvatar(submittedMood)
    });
    if (activeTab === 'home') {
      setHomeAnimatedCellId(clientId);
    }

    await enqueueAction({
      clientId,
      type: 'checkIn',
      path: '/api/check-ins',
      method: 'POST',
      payload: { mood: submittedMood, note: submittedNote },
      createdAt: Date.now()
    });
    flushQueue();
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
          active: changes.active,
          hasSeenOnboarding: changes.hasSeenOnboarding
        })
      });
      setProfile(updated);
      setLocale(updated.preferredLocale);
    } catch (error) {
      console.error(error);
    }
  };

  const completeMission = async (missionId: string, noteValue: string) => {
    setExpandedMissionId((current) => (current === missionId ? null : current));
    setSavingMissionId(missionId);
    const clientId = crypto.randomUUID();
    const mission = missions.find((m) => m.id === missionId);
    const optimisticCompletion: MissionCompletion = {
      id: clientId,
      missionId,
      missionCode: mission?.code ?? '',
      title: mission?.title ?? '',
      profileId: profile?.id ?? '',
      completedAt: new Date().toISOString(),
      note: noteValue || undefined
    };

    setMissionCompletions((current) => [...current, optimisticCompletion]);
    setMissions((current) => {
      const index = current.findIndex((entry) => entry.id === missionId);
      if (index === -1) {
        return current;
      }

      const next = [...current];
      const [completedMission] = next.splice(index, 1);
      next.push(completedMission);
      return next;
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
    triggerFeedback({
      kind: 'mission',
      phrase: pickPhrase('missionComplete'),
      avatar: capyBeeAvatar.celebrating
    });
    if (activeTab === 'home') {
      setHomeAnimatedCellId(clientId);
    }

    try {
      await enqueueAction({
        clientId,
        type: 'missionCompletion',
        path: `/api/missions/${missionId}/completions`,
        method: 'POST',
        payload: { note: noteValue },
        createdAt: Date.now()
      });
      flushQueue();
    } catch (error) {
      console.error(error);
    } finally {
      setSavingMissionId((current) => (current === missionId ? null : current));
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
    const submittedLabel = friendLabel;
    const submittedStage = friendStage;
    const submittedNote = friendNote;
    const clientId = crypto.randomUUID();
    const now = new Date().toISOString();
    const optimisticFriendship: FriendshipEntry = {
      id: clientId,
      personLabel: submittedLabel,
      stage: submittedStage,
      note: submittedNote || undefined,
      createdAt: now,
      updatedAt: now
    };

    setFriendships((current) => [...current, optimisticFriendship]);
    setFriendLabel('');
    setFriendNote('');
    setFriendStage('noticed');
    setFriendshipToast({
      message: pickFriendshipPhrase(submittedStage, submittedLabel || (locale === 'pl' ? 'kogoś' : 'them')),
      avatarSrc: resolveFriendshipAvatar(submittedStage)
    });
    if (activeTab === 'home') {
      setHomeAnimatedCellId(clientId);
    }

    await enqueueAction({
      clientId,
      type: 'friendship',
      path: '/api/friendships',
      method: 'POST',
      payload: { personLabel: submittedLabel, stage: submittedStage, note: submittedNote },
      createdAt: Date.now()
    });
    flushQueue();
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

    const target = friendships.find((entry) => entry.id === id);
    if (!target) {
      return;
    }

    setPendingDeleteId(id);
    setPendingDeletedFriendship(target);
    setDeletedFriendshipIds((current) => new Set(current).add(id));
    setFriendships((current) => current.filter((entry) => entry.id !== id));
    friendshipDeleteTimer.current = window.setTimeout(async () => {
      try {
        await enqueueAction({
          clientId: id,
          type: 'friendshipDelete',
          path: `/api/friendships/${id}`,
          method: 'DELETE',
          createdAt: Date.now()
        });
        flushQueue();
      } catch (error) {
        console.error(error);
      } finally {
        setPendingDeleteId(null);
        setPendingDeletedFriendship(null);
        friendshipDeleteTimer.current = null;
      }
    }, 5000);
  };

  const undoFriendshipRemove = () => {
    if (friendshipDeleteTimer.current) {
      window.clearTimeout(friendshipDeleteTimer.current);
      friendshipDeleteTimer.current = null;
    }
    if (pendingDeletedFriendship) {
      setFriendships((current) => [...current, pendingDeletedFriendship]);
      setDeletedFriendshipIds((current) => {
        const next = new Set(current);
        next.delete(pendingDeletedFriendship.id);
        return next;
      });
    }
    setPendingDeletedFriendship(null);
    setPendingDeleteId(null);
  };

  const addMemory = async (event: React.FormEvent) => {
    event.preventDefault();
    const submittedWorldType = worldType;
    const submittedTitle = memoryTitle;
    const submittedText = memoryText;
    const submittedFavorite = memoryFavorite;
    const clientId = crypto.randomUUID();
    const now = new Date().toISOString();
    const optimisticMemory: MemoryEntry = {
      id: clientId,
      worldType: submittedWorldType,
      title: submittedTitle || undefined,
      textContent: submittedText,
      isFavorite: submittedFavorite,
      createdAt: now,
      updatedAt: now
    };

    setMemories((current) => [optimisticMemory, ...current]);
    setAllMemories((current) => [optimisticMemory, ...current]);
    setMemoryTitle('');
    setMemoryText('');
    setMemoryFavorite(false);
    triggerFeedback({
      kind: 'memory',
      phrase: pickPhrase(submittedWorldType === 'old_world' ? 'memoryOldWorld' : 'memoryNewWorld'),
      avatar: capyBeeAvatar.celebrating
    });
    if (activeTab === 'home') {
      setHomeAnimatedCellId(clientId);
    }

    await enqueueAction({
      clientId,
      type: 'memory',
      path: '/api/memories',
      method: 'POST',
      payload: { worldType: submittedWorldType, title: submittedTitle, textContent: submittedText, isFavorite: submittedFavorite },
      createdAt: Date.now()
    });
    flushQueue();
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
      await enqueueAction({
        clientId: id,
        type: 'memoryDelete',
        path: `/api/memories/${id}`,
        method: 'DELETE',
        createdAt: Date.now()
      });
      flushQueue();
    } catch (error) {
      console.error(error);
    } finally {
      setPendingDeletedMemory(null);
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

    const target = allMemories.find((entry) => entry.id === id);
    if (!target) {
      return;
    }

    setPendingDeleteMemoryId(id);
    setPendingDeletedMemory(target);
    setDeletedMemoryIds((current) => new Set(current).add(id));
    setMemories((current) => current.filter((entry) => entry.id !== id));
    setAllMemories((current) => current.filter((entry) => entry.id !== id));
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
    if (pendingDeletedMemory) {
      setMemories((current) => {
        if (current.some((entry) => entry.id === pendingDeletedMemory.id)) {
          return current;
        }
        return [pendingDeletedMemory, ...current];
      });
      setAllMemories((current) => {
        if (current.some((entry) => entry.id === pendingDeletedMemory.id)) {
          return current;
        }
        return [pendingDeletedMemory, ...current];
      });
      setDeletedMemoryIds((current) => {
        const next = new Set(current);
        next.delete(pendingDeletedMemory.id);
        return next;
      });
    }
    setPendingDeletedMemory(null);
    setPendingDeleteMemoryId(null);
  };

  const commitCheckInDelete = async (id: string) => {
    setPendingDeleteCheckInId((current) => (current === id ? null : current));
    try {
      await enqueueAction({
        clientId: id,
        type: 'checkInDelete',
        path: `/api/check-ins/${id}`,
        method: 'DELETE',
        createdAt: Date.now()
      });
      flushQueue();
    } catch (error) {
      console.error(error);
    } finally {
      setPendingDeletedCheckIn(null);
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

    const target = checkIns.find((entry) => entry.id === id);
    if (!target) {
      return;
    }

    setPendingDeleteCheckInId(id);
    setPendingDeletedCheckIn(target);
    setDeletedCheckInIds((current) => new Set(current).add(id));
    setCheckIns((current) => current.filter((entry) => entry.id !== id));
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
    if (pendingDeletedCheckIn) {
      setCheckIns((current) => {
        if (current.some((entry) => entry.id === pendingDeletedCheckIn.id)) {
          return current;
        }
        return [...current, pendingDeletedCheckIn];
      });
      setDeletedCheckInIds((current) => {
        const next = new Set(current);
        next.delete(pendingDeletedCheckIn.id);
        return next;
      });
    }
    setPendingDeletedCheckIn(null);
    setPendingDeleteCheckInId(null);
  };

  const commitMissionCompletionDelete = async (id: string) => {
    setPendingDeleteMissionId((current) => (current === id ? null : current));
    try {
      await enqueueAction({
        clientId: id,
        type: 'missionCompletionDelete',
        path: `/api/missions/completions/${id}`,
        method: 'DELETE',
        createdAt: Date.now()
      });
      flushQueue();
    } catch (error) {
      console.error(error);
    } finally {
      setPendingDeletedMissionCompletion(null);
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

    const target = missionCompletions.find((entry) => entry.id === id);
    if (!target) {
      return;
    }

    setPendingDeleteMissionId(id);
    setPendingDeletedMissionCompletion(target);
    setDeletedMissionCompletionIds((current) => new Set(current).add(id));
    setMissionCompletions((current) => current.filter((entry) => entry.id !== id));
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
    if (pendingDeletedMissionCompletion) {
      setMissionCompletions((current) => {
        if (current.some((entry) => entry.id === pendingDeletedMissionCompletion.id)) {
          return current;
        }
        return [...current, pendingDeletedMissionCompletion];
      });
      setDeletedMissionCompletionIds((current) => {
        const next = new Set(current);
        next.delete(pendingDeletedMissionCompletion.id);
        return next;
      });
    }
    setPendingDeletedMissionCompletion(null);
    setPendingDeleteMissionId(null);
  };

  const finishTutorial = () => {
    setTutorialActive(false);
    if (profile) {
      updateProfile({ hasSeenOnboarding: true });
    }
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
          <CapyBeeAvatar src={capyBeeAvatar.faceHappy} size={34} alt={user.displayName} className="avatar" />
          <div>
            <h1 title={profile ? profile.nickname : user.displayName}>{profile ? profile.nickname : user.displayName}</h1>
            {profile ? null : <p>{user.email}</p>}
          </div>
        </div>
        <div className="auth-actions">
          {pendingSync > 0 && (
            <motion.span
              className="sync-indicator"
              animate={{ opacity: [0.4, 1, 0.4] }}
              transition={{ duration: 2, repeat: Infinity }}
              aria-hidden="true"
            >
              🐝
            </motion.span>
          )}
          <button
            ref={profileButtonRef}
            type="button"
            className={activeTab === 'profile' ? 'profile-nav-button active' : 'profile-nav-button'}
            onClick={() => changeTab('profile')}
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
          <a href="/logout" className="secondary-button logout-button" aria-label={text.logout}>
            <span className="logout-label-full">{text.logout}</span>
            <span className="logout-label-short" aria-hidden="true">{locale === 'pl' ? 'Wyj.' : 'Out'}</span>
          </a>
        </div>
      </header>

      <section className="content-area">
        {activeTab === 'home' ? (
          <>
            <section className="panel capybee-center-block">
              <CapyBeeAvatar src={homeAvatar} size={160} />
              <CapyBeeBubble text={homeAvatarBubble} />
            </section>

            <section className="panel" ref={moodSectionRef}>
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
                <button type="submit" className="primary-button">
                  {text.save}
                </button>
              </form>

              {missionSuggestionVisible ? (
                <button className="suggestion-card" onClick={() => changeTab('missions')}>
                  <CapyBeeAvatar src={capyBeeAvatar.suggesting} size={96} />
                  <span>{text.missionSuggestion}</span>
                </button>
              ) : null}
            </section>

            <section className="panel" ref={hiveSectionRef}>
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

            <section className="panel" ref={checkInHistorySectionRef}>
              <h3>{text.recentCheckins}</h3>
              {visibleCheckIns.length === 0 ? (
                <div className="capybee-center-block">
                  <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
                  <p className="empty-copy">{text.noItems}</p>
                </div>
              ) : (
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

              <div className="mission-category-row" role="tablist" aria-label={text.missions}>
                {missionCategoryOrder.map((key) => {
                  const Icon = missionCategoryIcon[key];
                  const isActive = missionCategory === key;
                  return (
                    <button
                      key={key}
                      type="button"
                      role="tab"
                      aria-selected={isActive}
                      className={isActive ? 'mission-chip active' : 'mission-chip'}
                      onClick={() => setMissionCategory(key)}
                    >
                      <Icon active={isActive} />
                      <span>{text[missionCategoryLabelKey[key]]}</span>
                    </button>
                  );
                })}
              </div>

              {visibleMissions.length === 0 ? (
                <div className="capybee-center-block">
                  <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
                  <CapyBeeBubble text={missionCategory === 'all' ? text.missionEmpty : text.missionEmptyCategory} />
                </div>
              ) : (
                <div className="list-stack">
                  {visibleMissions.map((mission) => {
                    const isExpanded = expandedMissionId === mission.id;
                    const isSaving = savingMissionId === mission.id;
                    const missionNote = missionNotes[mission.id] ?? '';
                    const showCheerFace = cheerMissionId === mission.id;

                    return (
                      <MissionAccordionRow
                        key={mission.id}
                        mission={mission}
                        locale={locale}
                        note={missionNote}
                        isExpanded={isExpanded}
                        isSaving={isSaving}
                        skipPending={skipPendingMissionId !== null}
                        showCheerFace={showCheerFace}
                        prefersReducedMotion={prefersReducedMotion}
                        onToggle={() => setExpandedMissionId((current) => (current === mission.id ? null : mission.id))}
                        onNoteChange={(nextValue) => setMissionNotes((current) => ({
                          ...current,
                          [mission.id]: nextValue
                        }))}
                        onComplete={completeMission}
                        onSkip={skipMission}
                      />
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
              {allMemories.length === 0 ? (
                <div className="capybee-center-block">
                  <CapyBeeAvatar
                    src={capyBeeAvatar.empathetic}
                    size={120}
                  />
                  <CapyBeeBubble text={text.memoryOldEmpty} />
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
              ref={navRefByKey[key]}
              type="button"
              className={isActive ? 'nav-item active' : 'nav-item'}
              onClick={() => changeTab(key)}
              aria-current={isActive ? 'page' : undefined}
            >
              <Icon active={isActive} />
              <span>{label}</span>
            </button>
          );
        })}
      </nav>

      {tutorialActive && activeTab === 'home' ? (
        <OnboardingTutorial
          copy={text}
          targets={{
            moodSection: moodSectionRef,
            hiveSection: hiveSectionRef,
            checkInHistorySection: checkInHistorySectionRef,
            missionsNav: missionsNavRef,
            friendshipsNav: friendshipsNavRef,
            memoriesNav: memoriesNavRef,
            profileButton: profileButtonRef
          }}
          onFinish={finishTutorial}
        />
      ) : null}
    </main>
  );
}
