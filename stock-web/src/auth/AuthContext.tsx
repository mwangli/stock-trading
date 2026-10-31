// AI_GENERATE_START--
import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from 'react'
import { getData, postData } from '../api/client'

interface AuthState {
  username: string | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: PropsWithChildren) {
  const [username, setUsername] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getData<{ username: string }>('/auth/me')
      .then((value) => setUsername(value.username))
      .catch(() => setUsername(null))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const clear = () => setUsername(null)
    window.addEventListener('stock-console:unauthorized', clear)
    return () => window.removeEventListener('stock-console:unauthorized', clear)
  }, [])

  const value = useMemo<AuthState>(() => ({
    username,
    loading,
    login: async (name, password) => {
      const user = await postData<{ username: string }>('/auth/login', { username: name, password })
      setUsername(user.username)
    },
    logout: async () => {
      await postData('/auth/logout')
      setUsername(null)
    },
  }), [loading, username])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider 未初始化')
  return value
}
// AI_GENERATE_END--
