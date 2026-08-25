/**
 * Session Persistence Layer
 * Bridges browser cookies (which may not survive PWA close on Android)
 * with a localStorage-backed session token for PWA reliability.
 *
 * Flow:
 * 1. After successful login, server sends a non-sensitive session token
 * 2. Client stores token in localStorage
 * 3. On app reopen, client sends token to /api/auth-status
 * 4. Server validates token against the database session
 * 5. If valid, session is restored; if invalid, user must re-login
 */

export interface SessionToken {
  token: string;
  expiresAt: number; // Unix timestamp (ms)
}

const SESSION_TOKEN_KEY = 'capybee.sessionToken';

/**
 * Store the session token locally.
 * Called after successful OAuth2 login.
 */
export function storeSessionToken(token: string, expiresInMs: number = 30 * 24 * 60 * 60 * 1000) {
  const expiresAt = Date.now() + expiresInMs;
  const sessionToken: SessionToken = { token, expiresAt };
  localStorage.setItem(SESSION_TOKEN_KEY, JSON.stringify(sessionToken));
  console.log(`[SessionPersistence] Stored session token, expires at ${new Date(expiresAt).toISOString()}`);
}

/**
 * Retrieve the stored session token if it exists and hasn't expired.
 */
export function getSessionToken(): string | null {
  try {
    const raw = localStorage.getItem(SESSION_TOKEN_KEY);
    if (!raw) return null;

    const parsed = JSON.parse(raw) as SessionToken;
    if (parsed.expiresAt < Date.now()) {
      console.log('[SessionPersistence] Stored token expired, clearing.');
      clearSessionToken();
      return null;
    }

    return parsed.token;
  } catch (err) {
    console.error('[SessionPersistence] Error reading stored token:', err);
    clearSessionToken();
    return null;
  }
}

/**
 * Clear the stored session token.
 * Called on logout or when token validation fails.
 */
export function clearSessionToken() {
  localStorage.removeItem(SESSION_TOKEN_KEY);
  console.log('[SessionPersistence] Cleared session token.');
}

/**
 * Check if we have a valid stored token.
 */
export function hasValidSessionToken(): boolean {
  return getSessionToken() !== null;
}
