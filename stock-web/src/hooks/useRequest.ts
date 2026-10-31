// AI_GENERATE_START -
import { useCallback, useEffect, useState } from 'react'

export function useRequest<T>(loader: () => Promise<T>, dependencies: readonly unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await loader())
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '请求失败')
    } finally {
      setLoading(false)
    }
  }, dependencies)

  useEffect(() => {
    void reload()
  }, [reload])

  return { data, loading, error, reload }
}
// AI_GENERATE_END -
