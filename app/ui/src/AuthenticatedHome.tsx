import { useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { CapyBeeAvatar, CapyBeeBubble, capyBeeAvatar, sameCalendarDay } from './capybee';
import { useCapyBeePhrase } from './hooks/useCapyBeePhrase';
import type { CapyBeePhrasePoolKey } from './data/capybeePhrases';

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

interface ActiveFeedback {
  kind: FeedbackKind;
  phrase: string;
  avatar: string;
}

const stageOptions = ['noticed', 'was_nice', 'talked', 'want_to_know_better'];

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
    missionHistoryEmpty: 'No missions yet.',
    friendshipEmpty: 'Who did you notice today?',
    friendshipToast: 'Got it! Every step counts.',
    memoryOldEmpty: 'Your old home is safe here.',
    memoryNewEmpty: 'Start building your new hive.',
    memorySavedOld: 'Saved. This will always be yours.',
    memorySavedNew: 'A new moment in the hive!',
    askName: 'What should I call you?',
    completionNote: 'Completion note',
    person: 'Person',
    stage: 'Stage',
    note: 'Note',
    addEntry: 'Add entry',
    title: 'Title',
    story: 'Story',
    favoriteMemory: 'Favorite memory',
    addMemory: 'Add memory',
    profileActive: 'Profile active',
    markComplete: 'Mark complete',
    honeycombProgress: 'Honeycomb progress',
    recentCheckins: 'Recent check-ins',
    completionHistory: 'Completion history'
  },
  pl: {
    homeTitle: 'Jak dzis bylo?',
    profileSetupTitle: 'Utworz profil dziecka',
    profileSetupHint: 'Uzyj ksywki. Prawdziwe imie nie jest wymagane.',
    save: 'Zapisz',
    saving: 'Zapisywanie...',
    setUp: 'Utworz profil',
    checkInPlaceholder: 'Opcjonalna notatka',
    missions: 'Misje',
    friendships: 'Relacje',
    memories: 'Wspomnienia',
    profile: 'Profil',
    home: 'Start',
    oldWorld: 'Stary swiat',
    newWorld: 'Nowy swiat',
    language: 'Jezyk',
    noItems: 'Na razie nic tu nie ma.',
    greetingFirstVisit: 'Hej! Jak dzis bylo?',
    greetingReturning: 'Milo cie znowu widziec.',
    reactionHeavy: 'To brzmi ciezko. Jestem tu.',
    reactionOkay: 'Okej to tez jest cos. Dobra robota.',
    reactionGood: 'To swietnie! Ciesze sie razem z toba.',
    missionSuggestion: 'Mam dla ciebie mala misje na dzis ->',
    missionEmpty: 'Nie ma teraz misji - wroc jutro!',
    missionDone: 'Misja wykonana! Nowa komorka w ulu',
    missionHistoryEmpty: 'Nie ma jeszcze zadnych misji.',
    friendshipEmpty: 'Kogo dzis zauwazyles?',
    friendshipToast: 'Zapamietalem! Kazdy krok sie liczy.',
    memoryOldEmpty: 'Twoj stary dom jest tutaj bezpieczny.',
    memoryNewEmpty: 'Zacznij budowac swoj nowy ul.',
    memorySavedOld: 'Zapamietane. To zawsze bedzie twoje.',
    memorySavedNew: 'Nowa chwila w ulu!',
    askName: 'Jak mam sie do ciebie zwracac?',
    completionNote: 'Notatka do ukonczenia',
    person: 'Osoba',
    stage: 'Etap',
    note: 'Notatka',
    addEntry: 'Dodaj wpis',
    title: 'Tytul',
    story: 'Historia',
    favoriteMemory: 'Ulubione wspomnienie',
    addMemory: 'Dodaj wspomnienie',
    profileActive: 'Profil aktywny',
    markComplete: 'Oznacz jako ukonczone',
    honeycombProgress: 'Postep ula',
    recentCheckins: 'Ostatnie check-iny',
    completionHistory: 'Historia ukonczen'
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

export function AuthenticatedHome({ user }: { user: UserProfile }) {
  const [activeTab, setActiveTab] = useState<TabKey>('home');
  const [profile, setProfile] = useState<ChildProfile | null>(null);
  const [profileMissing, setProfileMissing] = useState(false);
  const [locale, setLocale] = useState<'en' | 'pl'>('en');
  const pickPhrase = useCapyBeePhrase(locale);

  const [mood, setMood] = useState<Mood>('okay');
  const [note, setNote] = useState('');
  const [checkIns, setCheckIns] = useState<CheckIn[]>([]);
  const [checkInLoading, setCheckInLoading] = useState(false);
  const [missionSuggestionVisible, setMissionSuggestionVisible] = useState(false);

  const [missions, setMissions] = useState<Mission[]>([]);
  const [missionCompletions, setMissionCompletions] = useState<MissionCompletion[]>([]);
  const [missionNote, setMissionNote] = useState('');
  const [completingMissionId, setCompletingMissionId] = useState<string | null>(null);
  const [activeFeedback, setActiveFeedback] = useState<ActiveFeedback | null>(null);
  const [feedbackFadeOut, setFeedbackFadeOut] = useState(false);

  const [friendships, setFriendships] = useState<FriendshipEntry[]>([]);
  const [friendLabel, setFriendLabel] = useState('');
  const [friendStage, setFriendStage] = useState(stageOptions[0]);
  const [friendNote, setFriendNote] = useState('');

  const [worldType, setWorldType] = useState<'old_world' | 'new_world'>('old_world');
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [memoryTitle, setMemoryTitle] = useState('');
  const [memoryText, setMemoryText] = useState('');
  const [memoryFavorite, setMemoryFavorite] = useState(false);

  const [setupNickname, setSetupNickname] = useState('');
  const [setupBirthYear, setSetupBirthYear] = useState('');
  const [setupLocale, setSetupLocale] = useState<'en' | 'pl'>('en');
  const [setupAvatarSeed, setSetupAvatarSeed] = useState('sunny-bee');
  const [setupLoading, setSetupLoading] = useState(false);

  const feedbackFadeOutTimer = useRef<number | null>(null);
  const feedbackCleanupTimer = useRef<number | null>(null);

  const text = copy[locale];

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
  }, [profileMissing]);

  useEffect(() => {
    if (!profileMissing) {
      fetchMemories(worldType);
    }
  }, [worldType]);

  useEffect(() => {
    if (activeTab !== 'home') {
      setMissionSuggestionVisible(false);
    }
  }, [activeTab]);

  const hasCheckInToday = useMemo(
    () => checkIns.some((entry) => sameCalendarDay(new Date(entry.createdAt), new Date())),
    [checkIns]
  );

  const homeAvatar = hasCheckInToday ? capyBeeAvatar.default : capyBeeAvatar.waving;
  const homeAvatarBubble = hasCheckInToday ? text.greetingReturning : text.greetingFirstVisit;

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
      const data = await request<Mission[]>('/api/missions?active=true');
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

  const submitCheckIn = async (event: React.FormEvent) => {
    event.preventDefault();
    setCheckInLoading(true);
    const submittedMood = mood;

    try {
      await request<CheckIn>('/api/check-ins', {
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

  const completeMission = async (missionId: string) => {
    try {
      setCompletingMissionId(missionId);
      await request<MissionCompletion>(`/api/missions/${missionId}/completions`, {
        method: 'POST',
        body: JSON.stringify({ note: missionNote })
      });
      setMissionNote('');
      await fetchMissionCompletions();
      triggerFeedback({
        kind: 'mission',
        phrase: pickPhrase('missionComplete'),
        avatar: capyBeeAvatar.celebrating
      });
    } catch (error) {
      console.error(error);
    } finally {
      setCompletingMissionId(null);
    }
  };

  const addFriendship = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      await request<FriendshipEntry>('/api/friendships', {
        method: 'POST',
        body: JSON.stringify({ personLabel: friendLabel, stage: friendStage, note: friendNote })
      });
      setFriendLabel('');
      setFriendNote('');
      await fetchFriendships();
      triggerFeedback({
        kind: 'friendship',
        phrase: pickPhrase('friendshipAdded'),
        avatar: capyBeeAvatar.celebrating
      });
    } catch (error) {
      console.error(error);
    }
  };

  const removeFriendship = async (id: string) => {
    try {
      await request<void>(`/api/friendships/${id}`, { method: 'DELETE' });
      await fetchFriendships();
    } catch (error) {
      console.error(error);
    }
  };

  const addMemory = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      await request<MemoryEntry>('/api/memories', {
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
      triggerFeedback({
        kind: 'memory',
        phrase: pickPhrase(worldType === 'old_world' ? 'memoryOldWorld' : 'memoryNewWorld'),
        avatar: capyBeeAvatar.celebrating
      });
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
    } catch (error) {
      console.error(error);
    }
  };

  const deleteMemory = async (id: string) => {
    try {
      await request<void>(`/api/memories/${id}`, { method: 'DELETE' });
      await fetchMemories(worldType);
    } catch (error) {
      console.error(error);
    }
  };

  const totalProgress = checkIns.length + missionCompletions.length + friendships.length + memories.length;

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
              Nickname
              <input value={setupNickname} onChange={(event) => setSetupNickname(event.target.value)} required />
            </label>

            <label>
              Birth year
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
                <option value="en">English</option>
                <option value="pl">Polski</option>
              </select>
            </label>

            <label>
              Avatar seed
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
          {user.avatarUrl ? <img src={user.avatarUrl} alt={user.displayName} className="avatar" /> : null}
          <div>
            <h1>{profile ? profile.nickname : user.displayName}</h1>
            <p>{user.email}</p>
          </div>
        </div>
        <div className="auth-actions">
          <label className="locale-control">
            {text.language}
            <select
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
          <a href="/logout" className="secondary-button">Log out</a>
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
                      <span className="mood-label">{value}</span>
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
              <div className="honeycomb-grid" aria-label="Progress map">
                {Array.from({ length: 18 }).map((_, index) => (
                  <span
                    key={index}
                    className={`honeycomb-cell ${index < totalProgress ? 'filled' : ''}`}
                    title={index < totalProgress ? 'Filled' : 'Empty'}
                  />
                ))}
              </div>
            </section>

            <section className="panel">
              <h3>{text.recentCheckins}</h3>
              {checkIns.length === 0 ? <p>{text.noItems}</p> : (
                <div className="list-stack">
                  {checkIns.map((entry, index) => (
                    <motion.article
                      key={entry.id}
                      className="list-card"
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: index * 0.04 }}
                    >
                      <div className="line-between checkin-row">
                        <div className="checkin-headline">
                          <CapyBeeAvatar src={checkInListFace(entry.mood)} size={32} />
                          <strong>{entry.mood}</strong>
                        </div>
                        <span>{new Date(entry.createdAt).toLocaleDateString(locale)}</span>
                      </div>
                      {entry.note ? <p>{entry.note}</p> : null}
                    </motion.article>
                  ))}
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

              <label>
                {text.completionNote}
                <input value={missionNote} onChange={(event) => setMissionNote(event.target.value)} />
              </label>

              {missions.length === 0 ? (
                <div className="capybee-center-block">
                  <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
                  <CapyBeeBubble text={text.missionEmpty} />
                </div>
              ) : (
                <div className="list-stack">
                  {missions.map((mission) => (
                    <article key={mission.id} className="list-card">
                      <h3>{mission.title}</h3>
                      <p>{mission.description}</p>
                      <button
                        className="primary-button"
                        onClick={() => completeMission(mission.id)}
                        disabled={completingMissionId === mission.id}
                      >
                        {completingMissionId === mission.id ? text.saving : text.markComplete}
                      </button>
                    </article>
                  ))}
                </div>
              )}
            </section>

            <section className="panel">
              <h3>{text.completionHistory}</h3>
              {missionCompletions.length === 0 ? (
                <div className="capybee-center-block">
                  <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
                  <p className="empty-copy">{text.missionHistoryEmpty}</p>
                </div>
              ) : (
                <div className="list-stack">
                  {missionCompletions.map((entry) => (
                    <article key={entry.id} className="list-card">
                      <div className="line-between checkin-row">
                        <div className="checkin-headline">
                          <CapyBeeAvatar src={capyBeeAvatar.faceHappy} size={32} />
                          <strong>{entry.title}</strong>
                        </div>
                        <span>{new Date(entry.completedAt).toLocaleDateString(locale)}</span>
                      </div>
                      {entry.note ? <p>{entry.note}</p> : null}
                    </article>
                  ))}
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
              <form className="stack-form" onSubmit={addFriendship}>
                <label>
                  {text.person}
                  <input value={friendLabel} onChange={(event) => setFriendLabel(event.target.value)} required />
                </label>
                <label>
                  {text.stage}
                  <select value={friendStage} onChange={(event) => setFriendStage(event.target.value)}>
                    {stageOptions.map((stage) => (
                      <option key={stage} value={stage}>{stage}</option>
                    ))}
                  </select>
                </label>
                <label>
                  {text.note}
                  <input value={friendNote} onChange={(event) => setFriendNote(event.target.value)} />
                </label>
                <button className="primary-button" type="submit">{text.addEntry}</button>
              </form>
            </section>

            <section className="panel">
              {friendships.length === 0 ? (
                <div className="capybee-center-block">
                  <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
                  <CapyBeeBubble text={text.friendshipEmpty} />
                </div>
              ) : (
                <div className="list-stack">
                  {friendships.map((entry) => (
                    <article key={entry.id} className="list-card">
                      <div className="line-between">
                        <strong>{entry.personLabel}</strong>
                        <span>{entry.stage}</span>
                      </div>
                      {entry.note ? <p>{entry.note}</p> : null}
                      <button className="danger-button" onClick={() => removeFriendship(entry.id)}>Delete</button>
                    </article>
                  ))}
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
                <label className="checkbox-inline">
                  <input
                    type="checkbox"
                    checked={memoryFavorite}
                    onChange={(event) => setMemoryFavorite(event.target.checked)}
                  />
                  {text.favoriteMemory}
                </label>
                <button className="primary-button" type="submit">{text.addMemory}</button>
              </form>
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
                  {memories.map((entry) => (
                    <article key={entry.id} className="list-card">
                      <div className="line-between">
                        <strong>{entry.title || 'Memory'}</strong>
                        <button className="ghost-button" onClick={() => toggleFavorite(entry)}>
                          {entry.isFavorite ? '★' : '☆'}
                        </button>
                      </div>
                      {entry.textContent ? <p>{entry.textContent}</p> : null}
                      <button className="danger-button" onClick={() => deleteMemory(entry.id)}>Delete</button>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </>
        ) : null}

        {activeTab === 'profile' && profile ? (
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
                Nickname
                <input
                  value={profile.nickname}
                  onChange={(event) => setProfile({ ...profile, nickname: event.target.value })}
                />
              </label>

              <label>
                Birth year
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
                  value={profile.preferredLocale}
                  onChange={(event) => setProfile({ ...profile, preferredLocale: event.target.value as 'en' | 'pl' })}
                >
                  <option value="en">English</option>
                  <option value="pl">Polski</option>
                </select>
              </label>

              <label>
                Avatar seed
                <input
                  value={profile.avatarSeed ?? ''}
                  onChange={(event) => setProfile({ ...profile, avatarSeed: event.target.value })}
                />
              </label>

              <label className="checkbox-inline">
                <input
                  type="checkbox"
                  checked={profile.active}
                  onChange={(event) => setProfile({ ...profile, active: event.target.checked })}
                />
                {text.profileActive}
              </label>

              <button className="primary-button" type="submit">{text.save}</button>
            </form>
          </section>
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

      <nav className="bottom-nav">
        {([
          ['home', text.home],
          ['missions', text.missions],
          ['friendships', text.friendships],
          ['memories', text.memories],
          ['profile', text.profile]
        ] as Array<[TabKey, string]>).map(([key, label]) => (
          <button
            key={key}
            className={activeTab === key ? 'nav-item active' : 'nav-item'}
            onClick={() => setActiveTab(key)}
          >
            {label}
          </button>
        ))}
      </nav>
    </main>
  );
}
