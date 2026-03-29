import { useState, useEffect, useCallback, useRef } from 'react';
import { checkHealth } from '../api/health';

export type HealthState = 'checking' | 'online' | 'offline';

interface UseHealthCheckOptions {
  interval?: number;
  timeout?: number;
  retries?: number;
}

interface UseHealthCheckReturn {
  state: HealthState;
  isOnline: boolean;
  isOffline: boolean;
  isChecking: boolean;
  lastCheckTime: number | null;
  refresh: () => void;
}

const DEFAULT_OPTIONS: UseHealthCheckOptions = {
  interval: 10000,
  timeout: 5000,
  retries: 3,
};

export function useHealthCheck(options: UseHealthCheckOptions = {}): UseHealthCheckReturn {
  const { interval = DEFAULT_OPTIONS.interval, timeout = DEFAULT_OPTIONS.timeout } = options;

  const [state, setState] = useState<HealthState>('checking');
  const [lastCheckTime, setLastCheckTime] = useState<number | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const isMountedRef = useRef(true);

  const doCheck = useCallback(async () => {
    if (!isMountedRef.current) return;

    setState('checking');

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), timeout);

      await checkHealth();

      clearTimeout(timeoutId);

      if (isMountedRef.current) {
        setState('online');
        setLastCheckTime(Date.now());
      }
    } catch {
      if (isMountedRef.current) {
        setState('offline');
      }
    }
  }, [timeout]);

  const refresh = useCallback(() => {
    doCheck();
  }, [doCheck]);

  useEffect(() => {
    isMountedRef.current = true;

    doCheck();

    intervalRef.current = setInterval(() => {
      doCheck();
    }, interval);

    return () => {
      isMountedRef.current = false;
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [doCheck, interval]);

  return {
    state,
    isOnline: state === 'online',
    isOffline: state === 'offline',
    isChecking: state === 'checking',
    lastCheckTime,
    refresh,
  };
}