// AI_GENERATE_START-------------
import React, { useEffect, useRef, useState } from 'react';
import { Button, Input, message, Tooltip } from 'antd';
import { GlobalOutlined, LockOutlined, MoonOutlined, SunOutlined, UserOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../auth/AuthContext';
import { useAppTheme, type ThemeMode } from '../../theme/AppThemeProvider';
import SystemMark from '../../components/SystemMark';
import { checkBackendHealth, isBackendUnavailable } from '../../utils/backendHealth';

interface LoginCanvasPalette {
  background: string;
  grid: string;
  trend: string;
  candleUp: string;
  candleDown: string;
  volumeUp: string;
  volumeDown: string;
}

const LOGIN_CANVAS_PALETTES: Record<ThemeMode, LoginCanvasPalette> = {
  light: {
    background: '#f3f6fa',
    grid: 'rgba(37, 99, 235, 0.10)',
    trend: 'rgba(37, 99, 235, 0.46)',
    candleUp: 'rgba(22, 163, 74, 0.42)',
    candleDown: 'rgba(220, 38, 38, 0.38)',
    volumeUp: 'rgba(22, 163, 74, 0.11)',
    volumeDown: 'rgba(220, 38, 38, 0.09)',
  },
  dark: {
    background: '#080d17',
    grid: 'rgba(105, 128, 158, 0.13)',
    trend: 'rgba(45, 212, 191, 0.55)',
    candleUp: 'rgba(34, 197, 94, 0.48)',
    candleDown: 'rgba(248, 113, 113, 0.45)',
    volumeUp: 'rgba(34, 197, 94, 0.16)',
    volumeDown: 'rgba(248, 113, 113, 0.14)',
  },
};

const AnimatedMarketBackground: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { mode } = useAppTheme();

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d');
    if (!canvas || !context) {
      return undefined;
    }

    let width = 0;
    let height = 0;
    let animationFrame = 0;
    let reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
    const colors = LOGIN_CANVAS_PALETTES[mode];

    const draw = (time: number) => {
      context.clearRect(0, 0, width, height);
      context.fillStyle = colors.background;
      context.fillRect(0, 0, width, height);

      const gridSize = width < 640 ? 44 : 64;
      const gridOffset = reduceMotion ? 0 : (time * 0.012) % gridSize;
      context.save();
      context.strokeStyle = colors.grid;
      context.lineWidth = 1;
      for (let x = -gridOffset; x <= width + gridSize; x += gridSize) {
        context.beginPath();
        context.moveTo(x, 0);
        context.lineTo(x, height);
        context.stroke();
      }
      for (let y = 0; y <= height; y += gridSize) {
        context.beginPath();
        context.moveTo(0, y);
        context.lineTo(width, y);
        context.stroke();
      }
      context.restore();

      // 绘制连续行情轨迹，使用低频位移避免干扰登录操作。
      const lineSpacing = width < 640 ? 28 : 36;
      const lineShift = reduceMotion ? 0 : time * 0.018;
      context.save();
      context.beginPath();
      for (let x = -lineSpacing; x <= width + lineSpacing; x += lineSpacing) {
        const phase = (x + lineShift) / 115;
        const y = height * 0.56
          + Math.sin(phase) * height * 0.075
          + Math.cos(phase * 0.47) * height * 0.045;
        if (x === -lineSpacing) {
          context.moveTo(x, y);
        } else {
          context.lineTo(x, y);
        }
      }
      context.strokeStyle = colors.trend;
      context.lineWidth = 2;
      context.stroke();
      context.restore();

      // K 线与成交量共享横向节奏，形成持续更新的市场背景。
      const candleSpacing = width < 640 ? 54 : 70;
      const candleWidth = width < 640 ? 8 : 10;
      const candleOffset = reduceMotion ? 0 : (time * 0.016) % candleSpacing;
      const firstIndex = Math.floor((reduceMotion ? 0 : time * 0.016) / candleSpacing);
      context.save();
      for (let x = -candleSpacing - candleOffset, index = firstIndex - 1;
        x <= width + candleSpacing;
        x += candleSpacing, index += 1) {
        const center = height * 0.34
          + Math.sin(index * 1.31) * height * 0.11
          + Math.cos(index * 0.57) * height * 0.04;
        const movement = Math.sin(index * 2.17) * height * 0.035;
        const open = center - movement;
        const close = center + movement;
        const high = Math.min(open, close) - height * (0.025 + Math.abs(Math.sin(index)) * 0.025);
        const low = Math.max(open, close) + height * (0.025 + Math.abs(Math.cos(index)) * 0.025);
        const rising = close <= open;
        const color = rising ? colors.candleUp : colors.candleDown;

        context.strokeStyle = color;
        context.fillStyle = color;
        context.lineWidth = 1;
        context.beginPath();
        context.moveTo(x, high);
        context.lineTo(x, low);
        context.stroke();
        context.fillRect(x - candleWidth / 2, Math.min(open, close), candleWidth, Math.max(3, Math.abs(close - open)));

        const volumeHeight = height * (0.035 + Math.abs(Math.sin(index * 0.83)) * 0.085);
        context.fillStyle = rising ? colors.volumeUp : colors.volumeDown;
        context.fillRect(x - candleWidth, height - volumeHeight - 28, candleWidth * 2, volumeHeight);
      }
      context.restore();

      if (!reduceMotion) {
        animationFrame = window.requestAnimationFrame(draw);
      }
    };

    const resize = () => {
      const bounds = canvas.getBoundingClientRect();
      const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
      width = bounds.width;
      height = bounds.height;
      canvas.width = Math.round(width * pixelRatio);
      canvas.height = Math.round(height * pixelRatio);
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      if (reduceMotion) {
        draw(0);
      }
    };

    const handleMotionPreference = (event: MediaQueryListEvent) => {
      reduceMotion = event.matches;
      window.cancelAnimationFrame(animationFrame);
      draw(0);
    };

    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(canvas);
    motionPreference.addEventListener('change', handleMotionPreference);
    resize();
    draw(0);

    return () => {
      window.cancelAnimationFrame(animationFrame);
      resizeObserver.disconnect();
      motionPreference.removeEventListener('change', handleMotionPreference);
    };
  }, [mode]);

  return <canvas ref={canvasRef} aria-hidden="true" className="pointer-events-none absolute inset-0 h-full w-full" />;
};

const Login: React.FC = () => {
  const { t, i18n } = useTranslation();
  const { login } = useAuth();
  const { mode, toggleMode } = useAppTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('123456');
  const [submitting, setSubmitting] = useState(false);
  const [backendStatus, setBackendStatus] = useState<'checking' | 'online' | 'offline'>('checking');

  useEffect(() => {
    let active = true;
    const checkStatus = async () => {
      try {
        const online = await checkBackendHealth();
        if (active) {
          setBackendStatus(online ? 'online' : 'offline');
        }
      } catch {
        if (active) {
          setBackendStatus('offline');
        }
      }
    };
    void checkStatus();
    const timer = window.setInterval(() => void checkStatus(), 10_000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  const changeLanguage = () => {
    void i18n.changeLanguage(i18n.language.startsWith('en') ? 'zh' : 'en');
  };

  const submit = async () => {
    if (!username.trim() || !password) {
      message.error('请输入用户名和密码');
      return;
    }
    setSubmitting(true);
    try {
      await login({ username: username.trim(), password });
      const target = new URLSearchParams(location.search).get('redirect') || '/dashboard';
      navigate(target, { replace: true });
    } catch (error) {
      if (isBackendUnavailable(error)) {
        setBackendStatus('offline');
      }
      message.error(error instanceof Error ? error.message : '登录失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="relative isolate flex min-h-screen items-center justify-center overflow-hidden bg-[var(--app-bg)] px-4">
      <AnimatedMarketBackground />
      <div aria-hidden="true" className="login-theme-overlay pointer-events-none absolute inset-0" />
      <div className="absolute left-4 top-4 z-20 flex items-center gap-3 sm:left-6 sm:top-6">
        <SystemMark size="medium" title={t('login.algoTrading')} />
        <div>
          <div className="text-lg font-bold tracking-wide text-[var(--text-primary)] sm:text-xl">{t('login.algoTrading')}</div>
          <div className="mt-1 text-[10px] uppercase tracking-[0.18em] text-[var(--text-muted)] sm:text-xs">AI-AGENT Smart Trading Console</div>
        </div>
      </div>
      <div className="absolute right-4 top-4 z-20 flex items-center gap-2 sm:right-6 sm:top-6">
        <div className="flex items-center gap-2 rounded border border-[var(--border-color)] bg-[var(--panel-bg)]/90 px-3 py-2 text-xs text-[var(--text-secondary)] shadow-lg backdrop-blur-md">
          <span className={`h-2 w-2 rounded-full ${backendStatus === 'online' ? 'bg-emerald-500 shadow-[0_0_0_3px_rgba(34,197,94,0.12)]' : backendStatus === 'offline' ? 'bg-red-500' : 'bg-amber-500'}`} />
          <span>{backendStatus === 'online' ? t('login.systemOnline') : backendStatus === 'offline' ? t('login.systemOffline') : t('login.systemChecking')}</span>
        </div>
        <Tooltip title={mode === 'dark' ? t('layout.lightMode') : t('layout.darkMode')}>
          <Button
            type="text"
            className="app-theme-picker-button !h-9 !w-9"
            icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
            onClick={toggleMode}
            aria-label={mode === 'dark' ? t('layout.lightMode') : t('layout.darkMode')}
          />
        </Tooltip>
        <Tooltip title={i18n.language.startsWith('en') ? '中文' : 'English'}>
          <Button
            type="text"
            className="!h-9 !px-2 text-xs text-[var(--text-secondary)] hover:!bg-[var(--panel-bg)] hover:!text-[var(--text-primary)]"
            icon={<GlobalOutlined />}
            onClick={changeLanguage}
            aria-label={i18n.language.startsWith('en') ? '中文' : 'English'}
          >
            {i18n.language.startsWith('en') ? '中文' : 'EN'}
          </Button>
        </Tooltip>
      </div>
      <section className="relative z-10 w-full max-w-[420px] rounded-md border border-[var(--border-color)] bg-[var(--panel-bg)] p-8 shadow-2xl">
        <div className="mb-8 text-center">
          <SystemMark size="large" className="mx-auto mb-4" title={t('login.algoTrading')} />
          <h1 className="m-0 text-2xl font-semibold text-[var(--text-primary)]">{t('login.form.title')}</h1>
          <p className="mt-2 text-sm text-[var(--text-muted)]">{t('login.form.subtitle')}</p>
        </div>
        <div className="space-y-4">
          <Input
            size="large"
            prefix={<UserOutlined />}
            value={username}
            placeholder={t('common.username')}
            onChange={(event) => setUsername(event.target.value)}
            onPressEnter={() => void submit()}
          />
          <Input.Password
            size="large"
            prefix={<LockOutlined />}
            value={password}
            placeholder={t('common.password')}
            onChange={(event) => setPassword(event.target.value)}
            onPressEnter={() => void submit()}
          />
          <Button type="primary" size="large" block loading={submitting} onClick={() => void submit()}>
            {t('login.form.button')}
          </Button>
        </div>
        <p className="mt-6 text-center text-xs text-[var(--text-muted)]">支持账户：admin、test</p>
      </section>
    </main>
  );
};

export default Login;
// AI_GENERATE_END-------------
