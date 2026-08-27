import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearSessionToken,
  getSessionToken,
  hasValidSessionToken,
  storeSessionToken
} from './sessionPersistence';

const STORAGE_KEY = 'capybee.sessionToken';

describe('session persistence', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-27T12:00:00.000Z'));
    vi.spyOn(console, 'log').mockImplementation(() => undefined);
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('stores and retrieves a token using the requested expiry', () => {
    storeSessionToken('restore-token', 1_000);

    expect(localStorage.getItem(STORAGE_KEY)).toBe(
      JSON.stringify({ token: 'restore-token', expiresAt: Date.now() + 1_000 })
    );
    expect(getSessionToken()).toBe('restore-token');
    expect(hasValidSessionToken()).toBe(true);
  });

  it('clears an expired token', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: 'expired', expiresAt: Date.now() - 1 }));

    expect(getSessionToken()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(hasValidSessionToken()).toBe(false);
  });

  it('clears malformed stored data', () => {
    localStorage.setItem(STORAGE_KEY, '{not json');

    expect(getSessionToken()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('clears a token explicitly', () => {
    storeSessionToken('restore-token');

    clearSessionToken();

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});