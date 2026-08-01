import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { AuthenticatedHome } from './AuthenticatedHome';
import type { UserProfile } from './AuthenticatedHome';
import { CapyBeeAvatar, CapyBeeBubble, capyBeeAvatar } from './capybee';

const landingCopy = {
  en: {
    loading: 'One moment...',
    sessionExpired: "Hey, you're back! Sign in again.",
    signIn: 'Sign in',
    eyebrow: 'CapyBee concept scaffold',
    heroTitle: 'Gentle software for a harder transition.',
    heroLead:
      'CapyBee helps children settle into a new country with gentle daily check-ins, tiny real-world missions, and private spaces for old and new memories.',
    continueWithGoogle: 'Continue with Google',
    viewOverview: 'View app overview',
    firstScreen: 'First screen',
    welcomeHive: 'Welcome to your hive.',
    heroCardLead: 'A safe first release for families: simple setup, warm tone, and one small step at a time.',
    builtForMilestone: 'Built for the first milestone',
    scaffoldSupports: 'What the scaffold already supports',
    firstReleaseShape: 'First release shape',
    afterSignIn: 'What happens after sign-in',
    profileSetup: 'Profile setup',
    profileSetupDescription: 'Parent creates one child profile using nickname and language preference.',
    dailyFlow: 'Daily flow',
    dailyFlowDescription: 'Child can check in, finish a mission, and track tiny social progress privately.',
    twoWorlds: 'Two worlds',
    twoWorldsDescription: 'Old World and New World memories are stored side-by-side, never replacing each other.',
    highlights: [
      {
        title: 'Google sign-in',
        description: 'Parent login starts with Google OAuth and opens a private family space.'
      },
      {
        title: 'Child profile setup',
        description: 'Create one child profile with nickname and language preference in a few taps.'
      },
      {
        title: 'Daily support loop',
        description: 'Check-ins, missions, friendship notes, and memories are all stored safely for one family.'
      },
      {
        title: 'Mobile-first experience',
        description: 'Core flows are optimized for phones so children can use CapyBee in short, calm sessions.'
      }
    ]
  },
  pl: {
    loading: 'Chwileczkę...',
    sessionExpired: 'Hej, wróciłeś! Zaloguj się ponownie.',
    signIn: 'Zaloguj się',
    eyebrow: 'Szkielet koncepcji CapyBee',
    heroTitle: 'Łagodne wsparcie na trudniejszy czas zmiany.',
    heroLead:
      'CapyBee pomaga dzieciom odnaleźć się w nowym kraju dzięki codziennym, spokojnym meldunkom, małym misjom w prawdziwym świecie oraz bezpiecznej przestrzeni na stare i nowe wspomnienia.',
    continueWithGoogle: 'Kontynuuj z Google',
    viewOverview: 'Zobacz przegląd aplikacji',
    firstScreen: 'Pierwszy ekran',
    welcomeHive: 'Witaj w swoim ulu.',
    heroCardLead: 'Bezpieczna pierwsza wersja dla rodzin: prosty start, ciepły ton i jeden mały krok naraz.',
    builtForMilestone: 'Zaprojektowane na pierwszy etap',
    scaffoldSupports: 'Co już wspiera ten szkielet',
    firstReleaseShape: 'Kształt pierwszego wydania',
    afterSignIn: 'Co dzieje się po zalogowaniu',
    profileSetup: 'Konfiguracja profilu',
    profileSetupDescription: 'Rodzic tworzy jeden profil dziecka z pseudonimem i preferowanym językiem.',
    dailyFlow: 'Codzienny rytm',
    dailyFlowDescription: 'Dziecko może zrobić meldunek dnia, wykonać misję i prywatnie śledzić małe postępy społeczne.',
    twoWorlds: 'Dwa światy',
    twoWorldsDescription: 'Wspomnienia ze Starego i Nowego Świata są przechowywane obok siebie, bez zastępowania jednych drugimi.',
    highlights: [
      {
        title: 'Logowanie przez Google',
        description: 'Rodzic loguje się przez Google OAuth i otwiera prywatną przestrzeń rodziny.'
      },
      {
        title: 'Profil dziecka',
        description: 'W kilku kliknięciach tworzysz profil dziecka z pseudonimem i preferowanym językiem.'
      },
      {
        title: 'Codzienna pętla wsparcia',
        description: 'Meldunki dnia, misje, notatki o relacjach i wspomnienia są bezpiecznie przechowywane dla jednej rodziny.'
      },
      {
        title: 'Doświadczenie mobile-first',
        description: 'Kluczowe ścieżki są zoptymalizowane pod telefon, aby dzieci mogły korzystać z CapyBee krótko i spokojnie.'
      }
    ]
  }
} as const;

const stats = [
  { label: 'Stack', value: 'Spring Boot + React' },
  { label: 'Auth', value: 'Google OAuth' },
  { label: 'Database', value: 'PostgreSQL' },
  { label: 'Deploy', value: 'Fly.io' }
];

function App() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);

  const locale: 'pl' | 'en' = navigator.language.toLowerCase().startsWith('pl') ? 'pl' : 'en';
  const text = landingCopy[locale];

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const res = await fetch('/api/auth-status');
      if (res.ok) {
        const data = await res.json();
        setUser(data);
        setSessionExpired(false);
      } else if (res.status === 401) {
        setSessionExpired(true);
      }
    } catch (err) {
      console.error('Failed to check auth status:', err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <main className="auth-loading">
        <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
        <p>{text.loading}</p>
      </main>
    );
  }

  if (user?.authenticated) {
    return <AuthenticatedHome user={user} />;
  }

  if (sessionExpired) {
    return (
      <main className="auth-loading">
        <CapyBeeAvatar src={capyBeeAvatar.waving} size={120} />
        <CapyBeeBubble
          text={text.sessionExpired}
        />
        <a className="primary-button" href="/oauth2/authorization/google">
          {text.signIn}
        </a>
      </main>
    );
  }

  return (
    <main className="page-shell">
      <section className="hero">
        <div className="hero-copy">
          <CapyBeeAvatar src={capyBeeAvatar.waving} size={200} alt="CapyBee" className="hero-capybee" />
          <span className="eyebrow">{text.eyebrow}</span>
          <h1>{text.heroTitle}</h1>
          <p className="lead">{text.heroLead}</p>
          <div className="hero-actions">
            <a className="primary-button" href="/oauth2/authorization/google">
              {text.continueWithGoogle}
            </a>
            <a className="secondary-button" href="#overview">
              {text.viewOverview}
            </a>
          </div>
        </div>

        <motion.aside
          className="hero-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: 'easeOut' }}
        >
          <div className="hero-card-badge">{text.firstScreen}</div>
          <h2>{text.welcomeHive}</h2>
          <p>{text.heroCardLead}</p>
          <div className="hero-card-grid">
            {stats.map((stat) => (
              <div key={stat.label} className="stat-chip">
                <span>{stat.label}</span>
                <strong>{stat.value}</strong>
              </div>
            ))}
          </div>
        </motion.aside>
      </section>

      <section className="overview" id="overview">
        <div className="section-heading">
          <span>{text.builtForMilestone}</span>
          <h2>{text.scaffoldSupports}</h2>
        </div>
        <div className="feature-grid">
          {text.highlights.map((item, index) => (
            <motion.article
              key={item.title}
              className="feature-card"
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.45, delay: index * 0.08 }}
            >
              <span className="feature-index">0{index + 1}</span>
              <h3>{item.title}</h3>
              <p>{item.description}</p>
            </motion.article>
          ))}
        </div>
      </section>

      <section className="roadmap">
        <div className="section-heading compact">
          <span>{text.firstReleaseShape}</span>
          <h2>{text.afterSignIn}</h2>
        </div>
        <div className="roadmap-list">
          <div>
            <strong>{text.profileSetup}</strong>
            <p>{text.profileSetupDescription}</p>
          </div>
          <div>
            <strong>{text.dailyFlow}</strong>
            <p>{text.dailyFlowDescription}</p>
          </div>
          <div>
            <strong>{text.twoWorlds}</strong>
            <p>{text.twoWorldsDescription}</p>
          </div>
        </div>
      </section>
    </main>
  );
}

export default App;
