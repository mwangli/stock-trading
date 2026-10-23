// AI_GENERATE_START--------
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { App as AntdApp, ConfigProvider, theme } from 'antd';

export type ThemeMode = 'light' | 'dark';

interface AppThemeContextValue {
  mode: ThemeMode;
  autoThemeEnabled: boolean;
  setMode: (mode: ThemeMode) => void;
  setAutoThemeEnabled: (enabled: boolean) => void;
  toggleMode: () => void;
  resetToAutomatic: () => ThemeMode;
}

const AppThemeContext = createContext<AppThemeContextValue | null>(null);
const AUTO_THEME_STORAGE_KEY = 'stock-theme-auto';

const readAutoThemeEnabled = (): boolean => {
  const storedValue = window.localStorage.getItem(AUTO_THEME_STORAGE_KEY);
  return storedValue === null ? true : storedValue === 'true';
};

const readStoredMode = (): ThemeMode | null => {
  const storedMode = window.localStorage.getItem('stock-theme');
  if (storedMode === 'light' || storedMode === 'dark') {
    return storedMode;
  }
  return null;
};

/**
 * 根据北京时间判断当前是否处于白天时段。
 * 06:00-18:00 使用浅色模式，其余时间使用深色模式。
 */
const resolveBeijingMode = (): ThemeMode => {
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    hour: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date());
  const hour = Number(parts.find((part) => part.type === 'hour')?.value ?? 0);
  return hour >= 6 && hour < 18 ? 'light' : 'dark';
};

const resolveInitialMode = (): ThemeMode => readAutoThemeEnabled() ? resolveBeijingMode() : (readStoredMode() ?? resolveBeijingMode());

/**
 * 全局应用主题提供器。
 * 同步管理 Ant Design 主题、页面 data-theme 属性和本地持久化偏好。
 */
export const AppThemeProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [autoThemeEnabled, setAutoThemeEnabledState] = useState<boolean>(readAutoThemeEnabled);
  const [mode, setMode] = useState<ThemeMode>(resolveInitialMode);

  const applyManualMode = useCallback((nextMode: ThemeMode) => {
    setAutoThemeEnabledState(false);
    window.localStorage.setItem(AUTO_THEME_STORAGE_KEY, 'false');
    window.localStorage.setItem('stock-theme', nextMode);
    setMode(nextMode);
  }, []);

  const setAutoThemeEnabled = useCallback((enabled: boolean) => {
    setAutoThemeEnabledState(enabled);
    window.localStorage.setItem(AUTO_THEME_STORAGE_KEY, String(enabled));
    if (enabled) {
      window.localStorage.removeItem('stock-theme');
      setMode(resolveBeijingMode());
    }
  }, []);

  const resetToAutomatic = useCallback(() => {
    setAutoThemeEnabledState(true);
    window.localStorage.setItem(AUTO_THEME_STORAGE_KEY, 'true');
    window.localStorage.removeItem('stock-theme');
    const automaticMode = resolveBeijingMode();
    setMode(automaticMode);
    return automaticMode;
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = mode;
  }, [mode]);

  useEffect(() => {
    if (!autoThemeEnabled) {
      return undefined;
    }

    const syncWithBeijingTime = () => {
      setMode(resolveBeijingMode());
    };

    syncWithBeijingTime();
    const timer = window.setInterval(syncWithBeijingTime, 60 * 1000);
    return () => window.clearInterval(timer);
  }, [autoThemeEnabled]);

  const contextValue = useMemo<AppThemeContextValue>(() => ({
    mode,
    autoThemeEnabled,
    setMode: applyManualMode,
    setAutoThemeEnabled,
    toggleMode: () => applyManualMode(mode === 'light' ? 'dark' : 'light'),
    resetToAutomatic,
  }), [applyManualMode, autoThemeEnabled, mode, resetToAutomatic, setAutoThemeEnabled]);

  return (
    <AppThemeContext.Provider value={contextValue}>
      <ConfigProvider
        theme={{
          algorithm: mode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
          token: {
            colorPrimary: '#2563eb',
            colorInfo: '#2563eb',
            colorBgBase: mode === 'dark' ? '#090f1b' : '#ffffff',
            colorBgLayout: mode === 'dark' ? '#090f1b' : '#f5f7fa',
            colorBgContainer: mode === 'dark' ? '#0d1524' : '#ffffff',
            colorBorder: mode === 'dark' ? '#253047' : '#dce3ed',
            borderRadius: 4,
            controlHeight: 34,
            fontSize: 14,
          },
          components: {
            Card: { headerHeight: 48 },
            Table: {
              headerBg: mode === 'dark' ? '#111c2e' : '#f7f9fc',
              rowHoverBg: mode === 'dark' ? '#111c2e' : '#f0f6ff',
            },
            Menu: {
              darkItemBg: '#0d1524',
              darkItemSelectedBg: '#2563eb',
              itemSelectedBg: '#eaf2ff',
              itemSelectedColor: '#1d4ed8',
            },
          },
        }}
      >
        <AntdApp>{children}</AntdApp>
      </ConfigProvider>
    </AppThemeContext.Provider>
  );
};

/**
 * 获取当前全局主题状态。
 *
 * @return 主题模式和切换方法
 */
export const useAppTheme = (): AppThemeContextValue => {
  const context = useContext(AppThemeContext);
  if (!context) {
    throw new Error('useAppTheme 必须在 AppThemeProvider 内使用');
  }
  return context;
};
// AI_GENERATE_END--------
