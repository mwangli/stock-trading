import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider, theme } from 'antd';
import './index.css'
import './i18n'; // Import i18n configuration
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#00e396',
          colorBgBase: '#050505',
          colorBgContainer: '#0a0c10',
        },
      }}
    >
      <App />
    </ConfigProvider>
  </StrictMode>,
)
