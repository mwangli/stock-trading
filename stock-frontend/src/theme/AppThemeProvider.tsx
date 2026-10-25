// AI_GENERATE_START--------------
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { App as AntdApp, ConfigProvider, theme } from 'antd';

export type ThemeMode = 'light' | 'dark';

interface ThemePalette {
  appBg: string;
  panelBg: string;
  panelMuted: string;
  border: string;
  textPrimary: string;
  brand: string;
  brandSoft: string;
  rowHover: string;
}

interface AppThemeContextValue {
  mode: ThemeMode;
  autoThemeEnabled: boolean;
  setMode: (mode: ThemeMode) => void;
  setAutoThemeEnabled: (enabled: boolean) => void;
  toggleMode: () => void;
}

const THEME_PALETTES: Record<ThemeMode, ThemePalette> = {
  light: {
    appBg: '#f3f6fa',
    panelBg: '#ffffff',
    panelMuted: '#edf3fa',
    border: '#d9e2ee',
    textPrimary: '#172033',
    brand: '#2563eb',
    brandSoft: '#e8f1ff',
    rowHover: '#f0f6ff',
  },
  dark: {
    appBg: '#090f1b',
    panelBg: '#0d1524',
    panelMuted: '#111c2e',
    border: '#253047',
    textPrimary: '#eef4ff',
    brand: '#3b82f6',
    brandSoft: 'rgba(59, 130, 246, 0.14)',
    rowHover: '#111c2e',
  },
};

const AppThemeContext = createContext<AppThemeContextValue | null>(null);
const AUTO_THEME_STORAGE_KEY = 'stock-theme-auto';
const THEME_MODE_STORAGE_KEY = 'stock-theme';
const THEME_PRESET_STORAGE_KEY = 'stock-theme-preset';
const LAST_LIGHT_THEME_STORAGE_KEY = 'stock-theme-last-light';
const SHARED_THEME_STORAGE_KEY = 'theme';

const isThemeMode = (value: string | null): value is ThemeMode => (
  value === 'light' || value === 'dark'
);

const isDocumentationUrl = (url: string): boolean => {
  const pathname = new URL(url).pathname;
  return pathname === '/docs' || pathname.startsWith('/docs/');
};

const readAutoThemeEnabled = (): boolean => {
  const storedValue = window.localStorage.getItem(AUTO_THEME_STORAGE_KEY);
  return storedValue === null ? true : storedValue === 'true';
};

const readStoredMode = (): ThemeMode | null => {
  const storedMode = window.localStorage.getItem(THEME_MODE_STORAGE_KEY);
  if (storedMode === 'light' || storedMode === 'dark') {
    return storedMode;
  }
  const storedPreset = window.localStorage.getItem(THEME_PRESET_STORAGE_KEY);
  if (storedPreset) {
    return storedPreset === 'dark' ? 'dark' : 'light';
  }
  const storedLightPreset = window.localStorage.getItem(LAST_LIGHT_THEME_STORAGE_KEY);
  return storedLightPreset ? 'light' : null;
};

/**
 * 根据北京时间判断当前应使用的自动主题。
 * 06:00-18:00 使用浅色主题，其余时间使用深色主题。
 */
const resolveBeijingThemeMode = (): ThemeMode => {
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    hour: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date());
  const hour = Number(parts.find((part) => part.type === 'hour')?.value ?? 0);
  return hour >= 6 && hour < 18 ? 'light' : 'dark';
};

const resolveInitialThemeMode = (): ThemeMode => (
  readAutoThemeEnabled() ? resolveBeijingThemeMode() : (readStoredMode() ?? resolveBeijingThemeMode())
);

/**
 * 全局应用主题提供器。
 * 同步管理明暗主题、Ant Design Token、页面属性和本地持久化偏好。
 */
export const AppThemeProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [autoThemeEnabled, setAutoThemeEnabledState] = useState<boolean>(readAutoThemeEnabled);
  const [mode, setModeState] = useState<ThemeMode>(resolveInitialThemeMode);
  const palette = THEME_PALETTES[mode];

  const applyManualMode = useCallback((nextMode: ThemeMode) => {
    setAutoThemeEnabledState(false);
    window.localStorage.setItem(AUTO_THEME_STORAGE_KEY, 'false');
    window.localStorage.setItem(THEME_MODE_STORAGE_KEY, nextMode);
    window.localStorage.removeItem(THEME_PRESET_STORAGE_KEY);
    window.localStorage.removeItem(LAST_LIGHT_THEME_STORAGE_KEY);
    setModeState(nextMode);
  }, []);

  const setAutoThemeEnabled = useCallback((enabled: boolean) => {
    setAutoThemeEnabledState(enabled);
    window.localStorage.setItem(AUTO_THEME_STORAGE_KEY, String(enabled));
    if (enabled) {
      window.localStorage.removeItem(THEME_MODE_STORAGE_KEY);
      window.localStorage.removeItem(THEME_PRESET_STORAGE_KEY);
      window.localStorage.removeItem(LAST_LIGHT_THEME_STORAGE_KEY);
      setModeState(resolveBeijingThemeMode());
    } else {
      window.localStorage.setItem(THEME_MODE_STORAGE_KEY, mode);
    }
  }, [mode]);

  useEffect(() => {
    document.documentElement.dataset.theme = mode;
    delete document.documentElement.dataset.themePreset;
    window.localStorage.setItem(SHARED_THEME_STORAGE_KEY, mode);
  }, [mode]);

  useEffect(() => {
    const syncThemeFromDocumentation = (event: StorageEvent) => {
      if (event.storageArea !== window.localStorage
        || event.key !== SHARED_THEME_STORAGE_KEY
        || !isThemeMode(event.newValue)
        || !isDocumentationUrl(event.url)) {
        return;
      }

      setAutoThemeEnabledState(false);
      window.localStorage.setItem(AUTO_THEME_STORAGE_KEY, 'false');
      window.localStorage.setItem(THEME_MODE_STORAGE_KEY, event.newValue);
      window.localStorage.removeItem(THEME_PRESET_STORAGE_KEY);
      window.localStorage.removeItem(LAST_LIGHT_THEME_STORAGE_KEY);
      setModeState(event.newValue);
    };

    window.addEventListener('storage', syncThemeFromDocumentation);
    return () => window.removeEventListener('storage', syncThemeFromDocumentation);
  }, []);

  useEffect(() => {
    if (!autoThemeEnabled) {
      return undefined;
    }

    const syncWithBeijingTime = () => {
      setModeState(resolveBeijingThemeMode());
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
  }), [applyManualMode, autoThemeEnabled, mode, setAutoThemeEnabled]);

  return (
    <AppThemeContext.Provider value={contextValue}>
      <ConfigProvider
        theme={{
          algorithm: mode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
          token: {
            colorPrimary: palette.brand,
            colorInfo: palette.brand,
            colorBgBase: palette.panelBg,
            colorBgLayout: palette.appBg,
            colorBgContainer: palette.panelBg,
            colorBgElevated: palette.panelBg,
            colorBorder: palette.border,
            colorSplit: palette.border,
            colorTextBase: palette.textPrimary,
            borderRadius: 4,
            controlHeight: 34,
            fontSize: 14,
          },
          components: {
            Card: { headerHeight: 48 },
            Table: {
              headerBg: palette.panelMuted,
              rowHoverBg: palette.rowHover,
            },
            Menu: {
              darkItemBg: THEME_PALETTES.dark.panelBg,
              darkItemSelectedBg: THEME_PALETTES.dark.brandSoft,
              itemSelectedBg: palette.brandSoft,
              itemSelectedColor: palette.brand,
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
 * @return 主题模式、自适应状态和切换方法
 */
export const useAppTheme = (): AppThemeContextValue => {
  const context = useContext(AppThemeContext);
  if (!context) {
    throw new Error('useAppTheme 必须在 AppThemeProvider 内使用');
  }
  return context;
};
// AI_GENERATE_END--------------
