import { createContext, useContext, useState, useCallback } from 'react';

/**
 * AuthContext – stores the authenticated user after login.
 *
 * What is persisted in localStorage:
 *   edu_token  – raw JWT string (sent as Authorization: Bearer <token>)
 *   edu_user   – JSON object: { token, type, userId, personId, role }
 *
 * TokenResponse shape from Spring Boot (see auth/TokenResponse.java):
 *   { token, type: "Bearer", userId, personId, role }
 *
 * Role values (as stored in the DB):  Admin | Registrar | Faculty | Student
 * Spring Security @PreAuthorize expects ROLE_Admin etc., but we store the raw
 * DB role string here.  The ProtectedRoute component checks this value.
 */

const AuthContext = createContext(null);

const TOKEN_KEY = 'edu_token';
const USER_KEY  = 'edu_user';

export function AuthProvider({ children }) {
  // Rehydrate from localStorage so a page refresh keeps the session alive
  const [user, setUser] = useState(() => {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  });

  /**
   * login – called after a successful POST /api/auth/login.
   * @param {object} tokenData – the unwrapped { token, type, userId, personId, role }
   */
  const login = useCallback((tokenData) => {
    localStorage.setItem(TOKEN_KEY, tokenData.token);
    localStorage.setItem(USER_KEY, JSON.stringify(tokenData));
    setUser(tokenData);
  }, []);

  /** logout – clears localStorage and redirects to /login (via navigation). */
  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  );
}

/**
 * useAuth – hook to read auth state in any component.
 * @returns {{ user, login, logout, isAuthenticated }}
 */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
