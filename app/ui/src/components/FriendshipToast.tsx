import { AnimatePresence, motion } from 'framer-motion';
import { useEffect } from 'react';

interface FriendshipToastProps {
  toast: { message: string; avatarSrc: string } | null;
  onDismiss: () => void;
}

export function FriendshipToast({ toast, onDismiss }: FriendshipToastProps) {
  useEffect(() => {
    if (!toast) return;

    const timer = window.setTimeout(onDismiss, 4000);
    return () => window.clearTimeout(timer);
  }, [toast, onDismiss]);

  return (
    <AnimatePresence>
      {toast ? (
        <motion.aside
          initial={{ opacity: 0, y: 28 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 20 }}
          transition={{ type: 'spring', stiffness: 280, damping: 24 }}
          className="capybee-toast friendship-toast"
          role="status"
          aria-live="polite"
        >
          <motion.img
            src={toast.avatarSrc}
            alt=""
            animate={{ rotate: [0, -8, 8, -4, 0] }}
            transition={{ duration: 0.6, delay: 0.15 }}
            className="capybee-toast__avatar"
          />
          <span>{toast.message}</span>
        </motion.aside>
      ) : null}
    </AnimatePresence>
  );
}
