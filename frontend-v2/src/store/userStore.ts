import { create } from 'zustand';

interface UserState {
  token: string | null;
  user: {
    username: string;
    role: string;
  } | null;
  login: (username: string) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

export const useUserStore = create<UserState>((set) => ({
  token: localStorage.getItem('token'),
  user: localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')!) : null,
  isAuthenticated: !!localStorage.getItem('token'),

  login: (username: string) => {
    const token = 'mock-token-' + Math.random().toString(36).substr(2);
    const user = { username, role: 'admin' };
    
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));

    set({ token, user, isAuthenticated: true });
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    set({ token: null, user: null, isAuthenticated: false });
  },
}));
