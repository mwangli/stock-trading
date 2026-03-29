import { useState, useEffect, useCallback, useRef } from 'react';
import { checkHealth } from '../api/health';

export interface SystemStatus {
  connected: boolean;
  latency: number | null;
  lastUpdate: number | null;
  error: string | null;
}

const HEALTH_CHECK_INTERVAL = 30000;
const TIMEOUT_MS = 5000;

export function useSystemStatus() {
  const [status, setStatus] = useState<SystemStatus>({
    connected: false,
    latency: null,
    lastUpdate: null,
    error: null,
  });
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  const checkConnection = useCallback(async () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    abortControllerRef.current = new AbortController();

    const startTime = Date.now();
    try {
      const timeoutPromise = new Promise<never>((_, reject) => {
        setTimeout(() => reject(new Error('Timeout')), TIMEOUT_MS);
      });

      await Promise.race([
        checkHealth(),
        timeoutPromise
      ]);

      const latency = Date.now() - startTime;
      setStatus({
        connected: true,
        latency,
        lastUpdate: Date.now(),
        error: null,
      });
    } catch {
      setStatus(prev => ({
        ...prev,
        connected: false,
        error: '连接失败',
      }));
    }
  }, []);

  useEffect(() => {
    checkConnection();

    intervalRef.current = setInterval(checkConnection, HEALTH_CHECK_INTERVAL);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [checkConnection]);

  return status;
}
