import { useEffect, useState, type RefObject } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { CapyBeeAvatar, capyBeeAvatar } from '../capybee';

export interface OnboardingTargets {
  moodSection: RefObject<HTMLElement | null>;
  hiveSection: RefObject<HTMLElement | null>;
  checkInHistorySection: RefObject<HTMLElement | null>;
  missionsNav: RefObject<HTMLButtonElement | null>;
  friendshipsNav: RefObject<HTMLButtonElement | null>;
  memoriesNav: RefObject<HTMLButtonElement | null>;
  profileButton: RefObject<HTMLButtonElement | null>;
}

interface Step {
  titleKey: string;
  bodyKey: string;
  target: keyof OnboardingTargets | null;
  spotlight: 'content' | 'nav' | 'none';
}

const STEPS: Step[] = [
  { titleKey: 'onboardingWelcomeTitle', bodyKey: 'onboardingWelcomeBody', target: null, spotlight: 'none' },
  { titleKey: 'onboardingStartTitle', bodyKey: 'onboardingStartBody', target: 'moodSection', spotlight: 'content' },
  { titleKey: 'onboardingHiveTitle', bodyKey: 'onboardingHiveBody', target: 'hiveSection', spotlight: 'content' },
  {
    titleKey: 'onboardingHistoryTitle',
    bodyKey: 'onboardingHistoryBody',
    target: 'checkInHistorySection',
    spotlight: 'content'
  },
  { titleKey: 'onboardingMissionsTitle', bodyKey: 'onboardingMissionsBody', target: 'missionsNav', spotlight: 'nav' },
  {
    titleKey: 'onboardingFriendshipsTitle',
    bodyKey: 'onboardingFriendshipsBody',
    target: 'friendshipsNav',
    spotlight: 'nav'
  },
  { titleKey: 'onboardingMemoriesTitle', bodyKey: 'onboardingMemoriesBody', target: 'memoriesNav', spotlight: 'nav' },
  { titleKey: 'onboardingProfileTitle', bodyKey: 'onboardingProfileBody', target: 'profileButton', spotlight: 'nav' }
];

interface OnboardingTutorialProps {
  copy: Record<string, string>;
  targets: OnboardingTargets;
  onFinish: () => void;
}

export function OnboardingTutorial({ copy, targets, onFinish }: OnboardingTutorialProps) {
  const [step, setStep] = useState(0);
  const [hole, setHole] = useState<{ top: number; left: number; width: number; height: number } | null>(null);
  const current = STEPS[step];
  const isLast = step === STEPS.length - 1;

  useEffect(() => {
    if (!current.target) {
      return;
    }

    const el = targets[current.target].current;
    if (!el) {
      return;
    }

    // Keep the currently highlighted target visible, including the top profile icon step.
    el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [current, targets]);

  useEffect(() => {
    if (current.spotlight !== 'content' || !current.target) {
      setHole(null);
      return;
    }

    const el = targets[current.target].current;
    if (!el) {
      setHole(null);
      return;
    }

    el.scrollIntoView({ block: 'center', behavior: 'smooth' });

    const measure = () => {
      const rect = el.getBoundingClientRect();
      setHole({
        top: rect.top - 8,
        left: rect.left - 8,
        width: rect.width + 16,
        height: rect.height + 16
      });
    };

    const timeout = window.setTimeout(measure, 350);
    window.addEventListener('resize', measure);

    return () => {
      window.clearTimeout(timeout);
      window.removeEventListener('resize', measure);
    };
  }, [current, targets]);

  useEffect(() => {
    const navKeys: Array<keyof OnboardingTargets> = [
      'missionsNav',
      'friendshipsNav',
      'memoriesNav',
      'profileButton'
    ];

    navKeys.forEach((key) => {
      const el = targets[key].current;
      if (!el) {
        return;
      }
      const isActiveTarget = current.spotlight === 'nav' && current.target === key;
      el.classList.toggle('onboarding-glow', isActiveTarget);
    });

    return () => {
      navKeys.forEach((key) => targets[key].current?.classList.remove('onboarding-glow'));
    };
  }, [current, targets]);

  const advance = () => {
    if (isLast) {
      onFinish();
      return;
    }
    setStep((value) => value + 1);
  };

  return (
    <>
      <AnimatePresence>
        {current.spotlight === 'none' && step === 0 ? (
          <motion.div
            className="onboarding-intro-mask"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          />
        ) : null}

        {current.spotlight === 'content' && hole ? (
          <motion.div
            className="onboarding-mask"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <motion.div
              className="onboarding-hole"
              animate={{ top: hole.top, left: hole.left, width: hole.width, height: hole.height }}
              transition={{ type: 'spring', stiffness: 260, damping: 26 }}
            />
          </motion.div>
        ) : null}

        {current.spotlight === 'nav' ? (
          <motion.div
            className="onboarding-nav-mask"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          />
        ) : null}
      </AnimatePresence>

      <div className="onboarding-bubble-anchor" role="dialog" aria-live="polite" aria-label={copy[current.titleKey]}>
        <CapyBeeAvatar src={capyBeeAvatar.waving} size={128} style={{ transform: 'scaleX(-1)' }} />
        <div className="capybee-bubble onboarding-bubble">
          <strong>{copy[current.titleKey]}</strong>
          <p>{copy[current.bodyKey]}</p>
          <div className="onboarding-controls">
            <div className="onboarding-hexdots" aria-hidden="true">
              {STEPS.map((_, index) => (
                <span key={index} className={index <= step ? 'onboarding-hexdot on' : 'onboarding-hexdot'} />
              ))}
            </div>
            <div className="onboarding-buttons">
              {!isLast ? (
                <button type="button" className="onboarding-skip" onClick={onFinish}>
                  {copy.onboardingSkip}
                </button>
              ) : null}
              <button type="button" className="primary-button onboarding-next" onClick={advance}>
                {isLast ? copy.onboardingStart : copy.onboardingNext}
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
