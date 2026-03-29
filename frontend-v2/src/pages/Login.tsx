import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Input, Button, Form, message } from 'antd';
import { UserOutlined, LockOutlined, RiseOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useUserStore } from '../store/userStore';
import { useHealthCheck } from '../hooks/useHealthCheck';

import { useTranslation } from 'react-i18next';
const Login: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const { login } = useUserStore();
  const { t } = useTranslation();
  const { state: healthState, isChecking } = useHealthCheck({ interval: 15000, timeout: 5000 });

  const onFinish = async (values: any) => {
    setLoading(true);
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 1000));

    login(values.username);
    message.success('System Access Granted');
    navigate('/dashboard');
    setLoading(false);
  };

  // Grid animation logic
  const [offset, setOffset] = useState(0);
  useEffect(() => {
    const interval = setInterval(() => {
      setOffset(prev => (prev + 1) % 40);
    }, 50);
    return () => clearInterval(interval);
  }, []);

  const getStatusBadge = () => {
    if (isChecking) {
      return (
        <motion.div
          className="inline-block px-3 py-1 mb-6 border border-yellow-500/50 rounded-full bg-yellow-500/10 text-yellow-500 text-xs font-mono tracking-widest"
          animate={{ opacity: [0.5, 1, 0.5] }}
          transition={{ duration: 1.5, repeat: Infinity }}
        >
          {isChecking ? '检测中...' : ''}
        </motion.div>
      );
    }

    if (healthState === 'online') {
      return (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          className="inline-block px-3 py-1 mb-6 border border-[#00e396] rounded-full bg-[#00e396]/10 text-[#00e396] text-xs font-mono tracking-widest"
        >
          {t('login.systemOnline')}
        </motion.div>
      );
    }

    return (
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        className="inline-block px-3 py-1 mb-6 border border-red-500 rounded-full bg-red-500/10 text-red-500 text-xs font-mono tracking-widest"
      >
        系统离线
      </motion.div>
    );
  };

  const getSystemStatusText = () => {
    if (isChecking) {
      return '检测中...';
    }
    if (healthState === 'online') {
      return '系统状态：在线';
    }
    return '系统状态：离线';
  };

  return (
    <div className="flex h-screen w-full bg-[#050505] overflow-hidden font-sans text-white">
      {/* Left Panel - Cyberpunk Terminal */}
      <div className="hidden md:flex flex-1 relative flex-col justify-center px-20 bg-[radial-gradient(circle_at_10%_20%,rgb(16,20,28)_0%,rgb(5,5,5)_90%)] overflow-hidden">
        {/* Animated Grid Background */}
        <div 
          className="absolute inset-0 z-0 opacity-30 pointer-events-none"
          style={{
            backgroundImage: `
              linear-gradient(rgba(0, 227, 150, 0.05) 1px, transparent 1px),
              linear-gradient(90deg, rgba(0, 227, 150, 0.05) 1px, transparent 1px)
            `,
            backgroundSize: '40px 40px',
            backgroundPosition: `${offset}px ${offset}px`,
            transition: 'background-position 0.05s linear'
          }}
        />

        {/* Floating Abstract Shape */}
        <motion.div 
          animate={{ y: [0, -20, 0] }}
          transition={{ duration: 6, repeat: Infinity, ease: "easeInOut" }}
          className="absolute bottom-[-10%] left-[-10%] w-[120%] h-[60%] z-0 opacity-10 bg-gradient-to-t from-transparent to-[#00e396]/20"
          style={{ clipPath: 'polygon(0 100%, 100% 100%, 100% 40%, 85% 50%, 70% 30%, 55% 60%, 40% 20%, 25% 45%, 10% 10%, 0 35%)' }}
        />

        <div className="z-10 relative">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
          >
            {getStatusBadge()}
          </motion.div>

          <motion.h1 
            initial={{ opacity: 0, x: -50 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="text-6xl font-bold mb-6 leading-tight"
          >
            {t('login.algoTrading')}
          </motion.h1>

          <motion.p 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8, delay: 0.4 }}
            className="text-gray-400 text-lg max-w-lg mb-12"
          >
            {t('login.subtitle')}
          </motion.p>

          <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8, delay: 0.6 }}
            className="flex gap-10"
          >
            {[
              { label: t('login.stats.uptime'), value: '99.9%' },
              { label: t('login.stats.latency'), value: '<50ms' },
              { label: t('login.stats.monitoring'), value: '24/7' }
            ].map((item, index) => (
              <div key={index}>
                <div className="text-3xl font-bold">{item.value}</div>
                <div className="text-xs text-gray-500 uppercase tracking-wider">{item.label}</div>
              </div>
            ))}
          </motion.div>
        </div>
      </div>

      {/* Right Panel - Login Form */}
      <div className="flex-1 flex flex-col justify-center items-center bg-[#0a0c10] relative border-l border-white/5 shadow-[-20px_0_50px_rgba(0,0,0,0.5)] z-20">
        <div className="w-full max-w-md p-10 glass rounded-2xl">
          <div className="text-center mb-10">
            <RiseOutlined className="text-5xl text-[#00e396] mb-4" />
            <h2 className="text-2xl font-bold mb-2">{t('login.form.title')}</h2>
            <p className="text-gray-500">{t('login.form.subtitle')}</p>
          </div>

          <Form
            name="login"
            initialValues={{ remember: true, username: 'admin' }}
            onFinish={onFinish}
            layout="vertical"
            size="large"
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: 'Please input your Username!' }]}
            >
              <Input 
                prefix={<UserOutlined className="text-[#00e396]" />} 
                placeholder={t('login.form.usernamePlaceholder')}
                className="bg-white/5 border-white/10 text-white hover:border-[#00e396] focus:border-[#00e396]"
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: 'Please input your Password!' }]}
            >
              <Input.Password
                prefix={<LockOutlined className="text-[#00e396]" />}
                placeholder={t('login.form.passwordPlaceholder')}
                className="bg-white/5 border-white/10 text-white hover:border-[#00e396] focus:border-[#00e396]"
              />
            </Form.Item>

            <Form.Item>
              <Button 
                type="primary" 
                htmlType="submit" 
                className="w-full h-12 uppercase tracking-widest font-bold bg-gradient-to-r from-[#00e396] to-[#00b374] border-none hover:shadow-[0_0_20px_rgba(0,227,150,0.4)] transition-all duration-300"
                loading={loading}
              >
                {t('login.form.button')}
              </Button>
            </Form.Item>

            <div className="flex justify-between text-sm text-gray-500 mt-6">
              <span className="cursor-pointer hover:text-white transition-colors">{t('login.form.forgotPassword')}</span>
              <span className="cursor-pointer hover:text-[#00e396] transition-colors">{getSystemStatusText()}</span>
            </div>
          </Form>
        </div>
        
        <div className="absolute bottom-8 text-center text-gray-600 text-xs">
          AI 股票交易系统 ©2026 <a href="http://github.com/mwangli" target="_blank" rel="noopener noreferrer" className="hover:text-[#00e396]">@mangli</a>
        </div>
      </div>
    </div>
  );
};

export default Login;
