import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { AuthenticatedHome } from './AuthenticatedHome';
import type { UserProfile } from './AuthenticatedHome';
import { CapyBeeAvatar, CapyBeeBubble, capyBeeAvatar } from './capybee';

const authCopy = {
  en: {
    loading: 'One moment...',
    sessionExpired: "Hey, you're back! Sign in again.",
    signIn: 'Sign in'
  },
  pl: {
    loading: 'Chwileczkę...',
    sessionExpired: 'Hej, wróciłeś! Zaloguj się ponownie.',
    signIn: 'Zaloguj się'
  }
} as const;

const landingCopy = {
  en: {
    tagline: 'Together we build a new hive',
    heroTitle: 'Hey, you found the hive.',
    heroLine: "Let's settle in - one small step at a time.",
    ctaPrimary: 'Continue with Google',
    trustLine: 'Private. No ads. Just for your family.',
    featuresHeading: 'How CapyBee helps',
    featuresEyebrow: 'What your child actually does',
    features: [
      {
        title: 'A 2-minute daily check-in',
        body: 'Not a questionnaire - just "heavy, okay, or good?" CapyBee responds with warmth, never a lecture.'
      },
      {
        title: 'Two worlds, side by side',
        body: 'Old World memories and New World wins live together in one hive. Nothing gets deleted, nothing has to be replaced.'
      },
      {
        title: 'Small, real-world missions',
        body: 'Gentle nudges like saying hi to one person - always optional, never punished for skipping.'
      },
      {
        title: 'A private friendship tracker',
        body: 'A quiet place to notice small social steps. No public profiles, no strangers, ever.'
      }
    ],
    afterHeading: 'What happens after you sign in',
    afterEyebrow: 'First session',
    after: [
      {
        title: 'Profile setup',
        body: "You create one child profile with a nickname and a language preference - that's it."
      },
      {
        title: 'Daily flow',
        body: 'Your child checks in, tries a small mission, and quietly tracks social progress - five minutes, max.'
      },
      {
        title: 'Two worlds',
        body: 'Old World and New World memories are stored side by side, never replacing each other.'
      }
    ]
  },
  pl: {
    tagline: 'Razem budujemy nowy ul',
    heroTitle: 'Hej, znalazłeś nasz ul.',
    heroLine: 'Urządzimy się tu małymi krokami.',
    ctaPrimary: 'Kontynuuj z Google',
    trustLine: 'Prywatnie. Bez reklam. Tylko dla waszej rodziny.',
    featuresHeading: 'Jak CapyBee pomaga',
    featuresEyebrow: 'Co robi twoje dziecko',
    features: [
      {
        title: '2-minutowy codzienny check-in',
        body: 'To nie test. Tylko: ciężko, okej czy dobrze? CapyBee odpowiada ciepło i spokojnie.'
      },
      {
        title: 'Dwa światy obok siebie',
        body: 'Wspomnienia ze Starego Świata i nowe sukcesy żyją razem w jednym ulu.'
      },
      {
        title: 'Małe misje w prawdziwym świecie',
        body: 'Delikatne kroki, jak powiedzenie komuś cześć. Zawsze opcjonalnie, bez kar.'
      },
      {
        title: 'Prywatny tracker znajomości',
        body: 'Ciche miejsce na małe kroki społeczne. Bez obcych i bez publicznych profili.'
      }
    ],
    afterHeading: 'Co dzieje się po zalogowaniu',
    afterEyebrow: 'Pierwsza sesja',
    after: [
      {
        title: 'Ustawienie profilu',
        body: 'Tworzysz jeden profil dziecka z pseudonimem i językiem - i gotowe.'
      },
      {
        title: 'Codzienny rytm',
        body: 'Dziecko robi check-in, próbuje małej misji i zaznacza postępy - maksymalnie 5 minut.'
      },
      {
        title: 'Dwa światy',
        body: 'Wspomnienia ze Starego i Nowego Świata są obok siebie, nic nie znika.'
      }
    ]
  }
} as const;

function App() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);

  const locale: 'pl' | 'en' = navigator.language.toLowerCase().startsWith('pl') ? 'pl' : 'en';
  const authText = authCopy[locale];
  const t = landingCopy[locale];

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const res = await fetch('/api/auth-status');
      if (res.ok) {
        const data = await res.json();
        setUser(data);
        if (!data.authenticated) {
          setSessionExpired(true);
        } else {
          setSessionExpired(false);
        }
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
        <p>{authText.loading}</p>
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
          text={authText.sessionExpired}
        />
        <a className="primary-button" href="/oauth2/authorization/google">
          {authText.signIn}
        </a>
      </main>
    );
  }

  return (
    <main className="page-shell">
      <div className="landing-hex-bg" aria-hidden="true">
        <svg width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <pattern id="hexPattern" width="68" height="108" patternUnits="userSpaceOnUse" patternTransform="scale(1.4)">
              <polygon points="32,2 62,18 62,54 32,70 2,54 2,18" fill="none" stroke="#f2b233" strokeWidth="1" strokeOpacity="0.08" />
              <polygon points="32,72 62,88 62,124 32,140 2,124 2,88" fill="none" stroke="#f2b233" strokeWidth="1" strokeOpacity="0.08" />
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill="url(#hexPattern)" />
        </svg>
      </div>

      <section className="hero hero-minimal">
        <CapyBeeAvatar src={capyBeeAvatar.waving} size={140} alt="CapyBee" className="hero-capybee" />
        <span className="eyebrow">{t.tagline}</span>
        <h1>{t.heroTitle}</h1>
        <p className="hero-subline">{t.heroLine}</p>
        <a className="primary-button" href="/oauth2/authorization/google">
          {t.ctaPrimary}
        </a>
        <span className="trust-line">{t.trustLine}</span>
      </section>

      <section className="overview" id="overview">
        <div className="section-heading">
          <span>{t.featuresEyebrow}</span>
          <h2>{t.featuresHeading}</h2>
        </div>
        <div className="feature-grid">
          {t.features.map((item, index) => (
            <motion.article
              key={item.title}
              className="feature-card"
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.45, delay: index * 0.08 }}
            >
              <span className="feature-index">0{index + 1}</span>
              <h3>{item.title}</h3>
              <p>{item.body}</p>
            </motion.article>
          ))}
        </div>
      </section>

      <section className="roadmap">
        <div className="section-heading compact">
          <span>{t.afterEyebrow}</span>
          <h2>{t.afterHeading}</h2>
        </div>
        <div className="roadmap-list">
          {t.after.map((item) => (
            <div key={item.title}>
              <strong>{item.title}</strong>
              <p>{item.body}</p>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}

export default App;
