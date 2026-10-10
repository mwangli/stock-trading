// AI_GENERATE_START -
import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { App as AntdApp, ConfigProvider, theme } from 'antd';

export type ThemeMode = 'light' | 'dark';

interface AppThemeContextValue {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  toggleMode: () => void;
}

const AppThemeContext = createContext<AppThemeContextValue | null>(null);

const resolveInitialMode = (): ThemeMode => {
  const storedMode = window.localStorage.getItem('stock-theme');
  if (storedMode === 'light' || storedMode === 'dark') {
    return storedMode;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
};

/**
 * 全局应用主题提供器。
 * 同步管理 Ant Design 主题、页面 data-theme 属性和本地持久化偏好。
 */
export const AppThemeProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [mode, setMode] = useState<ThemeMode>(resolveInitialMode);

  useEffect(() => {
    document.documentElement.dataset.theme = mode;
    window.localStorage.setItem('stock-theme', mode);
  }, [mode]);

  const contextValue = useMemo<AppThemeContextValue>(() => ({
    mode,
    setMode,
    toggleMode: () => setMode((current) => current === 'light' ? 'dark' : 'light'),
  }), [mode]);

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
// AI_GENERATE_END -
