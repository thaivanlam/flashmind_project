const ACCESS_KEY = 'flashmind_access_token';
const REFRESH_KEY = 'flashmind_refresh_token';
const USER_KEY = 'flashmind_user';

export const getAccessToken = (): string | null =>
  localStorage.getItem(ACCESS_KEY);

export const getRefreshToken = (): string | null =>
  localStorage.getItem(REFRESH_KEY);

export const setTokens = (access: string, refresh: string): void => {
  localStorage.setItem(ACCESS_KEY, access);
  localStorage.setItem(REFRESH_KEY, refresh);
};

export const clearTokens = (): void => {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
};

export const setStoredUser = (user: unknown): void => {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
};

export const getStoredUser = <T>(): T | null => {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
};
