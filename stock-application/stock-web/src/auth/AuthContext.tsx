// AI_GENERATE_START-
import React, { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import request from '../utils/request';

export interface AuthUser {
  id: number;
  username: string;
  displayName: string;
}

interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
}

interface LoginPayload {
  username: string;
  password: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  login: (payload: LoginPayload) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    void (request.get('/auth/me') as unknown as Promise<ApiResponse<AuthUser>>)
      .then((result) => {
        if (active && result.success && result.data) {
          setUser(result.data);
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    const handleExpired = () => setUser(null);
    window.addEventListener('auth:expired', handleExpired);
    return () => {
      active = false;
      window.removeEventListener('auth:expired', handleExpired);
    };
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    login: async (payload) => {
      const result = await request.post('/auth/login', payload) as unknown as ApiResponse<AuthUser>;
      if (!result.success || !result.data) {
        throw new Error(result.message || '登录失败');
      }
      setUser(result.data);
      return result.data;
    },
    logout: async () => {
      try {
        await request.post('/auth/logout');
      } finally {
        setUser(null);
      }
    },
  }), [loading, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
};
// AI_GENERATE_END-
