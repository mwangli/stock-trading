import React, { useState, useEffect } from 'react';
import { Form, Input, Switch, Button, Select, Divider, message } from 'antd';
import { SaveOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import request from '../utils/request';

const { Option } = Select;

const Settings: React.FC = () => {
  const [form] = Form.useForm();
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchConfig();
  }, []);

  const fetchConfig = async () => {
    try {
      await request.get('/system/config');
    } catch (error) {
      console.error('获取配置失败:', error);
    }
  };

  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      await request.put('/system/config', values);
      message.success(t('settings.successMsg'));
    } catch (error) {
      message.error(t('settings.saveFailed') || '保存失败，请检查后端服务');
    } finally {
      setLoading(false);
    }
  };

  const handleTestConnection = async () => {
    try {
      await request.post('/browser/navigate/login');
      message.success(t('settings.navigateSuccess') || '已跳转到券商登录页面');
    } catch (error) {
      message.error(t('settings.navigateFailed') || '跳转失败，请检查后端服务');
    }
  };

  return (
    <div className="max-w-4xl mx-auto p-6 glass rounded-2xl">
      <div className="flex justify-between items-center mb-8 border-b border-white/10 pb-4">
        <h1 className="text-3xl font-bold text-white">{t('settings.title')}</h1>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={loading}
          onClick={() => form.submit()}
          className="bg-[#00e396] text-black border-none font-bold"
        >
          {t('settings.save')}
        </Button>
      </div>

      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{
          theme: 'dark',
          language: 'en',
          notifications: true,
          apiKey: '',
          riskLevel: 'moderate',
          maxDrawdown: 15,
          refreshRate: 30,
        }}
        className="space-y-8"
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* General Settings */}
          <section>
            <h3 className="text-xl font-bold text-[#00e396] mb-4">{t('settings.sections.general')}</h3>
            <Form.Item label={t('settings.fields.theme')} name="theme">
              <Select className="bg-white/5 border-white/10 text-white">
                <Option value="dark">{t('settings.options.theme.dark')}</Option>
                <Option value="light" disabled>{t('settings.options.theme.light')}</Option>
              </Select>
            </Form.Item>
            <Form.Item label={t('settings.fields.language')} name="language">
              <Select className="bg-white/5 border-white/10 text-white">
                <Option value="en">{t('settings.options.language.en')}</Option>
                <Option value="zh">{t('settings.options.language.zh')}</Option>
              </Select>
            </Form.Item>
            <Form.Item label={t('settings.fields.notifications')} name="notifications" valuePropName="checked">
              <Switch className="bg-gray-600" />
            </Form.Item>
          </section>

          {/* Broker Platform */}
          <section>
            <h3 className="text-xl font-bold text-[#00e396] mb-4 flex items-center gap-3">
              <span>{t('settings.sections.brokerPlatform')}</span>
              <a
                href="https://weixin.citicsinfo.com/tztweb/deal/index.html#!/account/login.html"
                target="_blank"
                rel="noopener noreferrer"
                className="text-sm font-normal text-[#00e396]/70 hover:text-[#00e396] hover:underline decoration-dashed underline-offset-4 transition-colors duration-200"
              >
                <span className="inline-flex items-center gap-1">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-3.5 h-3.5">
                    <path fillRule="evenodd" d="M15.75 2.25a4.25 4.25 0 014.24 4.24H21a.75.75 0 010 1.5H19.5a.75.75 0 00-.75.75v10.5a.75.75 0 001.5 0V8.25h4.28a.75.75 0 000-1.5H19.8a4.25 4.25 0 00-4.05-3.75 4.25 4.25 0 00-4.25 4.25v6.75a.75.75 0 001.5 0V4.5A2.25 2.25 0 0012.5 2.25h-1.5A2.25 2.25 0 008.75 4.5v6.75a.75.75 0 001.5 0v-4.5A4.25 4.25 0 0114.25 2.25h1.5z" clipRule="evenodd" />
                  </svg>
                  {t('settings.openBrokerPlatform')}
                </span>
              </a>
            </h3>
            <Form.Item label={t('settings.fields.apiKey')} name="apiKey">
              <Input.Password className="bg-white/5 border-white/10 text-white" />
            </Form.Item>
            <Form.Item label={t('settings.fields.refreshRate')} name="refreshRate">
               <Input type="number" className="bg-white/5 border-white/10 text-white" />
            </Form.Item>
            <Button icon={<ReloadOutlined />} onClick={handleTestConnection} className="mt-2 bg-transparent text-[#00e396] border-[#00e396]">{t('settings.testConnection')}</Button>
          </section>
        </div>

        <Divider className="border-white/10" />

        {/* Risk Management */}
          <section>
            <h3 className="text-xl font-bold text-[#ff4560] mb-4">{t('settings.sections.risk')}</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              <Form.Item label={t('settings.fields.riskLevel')} name="riskLevel">
                <Select className="bg-white/5 border-white/10 text-white">
                  <Option value="conservative">{t('settings.options.risk.conservative')}</Option>
                  <Option value="moderate">{t('settings.options.risk.moderate')}</Option>
                  <Option value="aggressive">{t('settings.options.risk.aggressive')}</Option>
                </Select>
              </Form.Item>
              <Form.Item label={t('settings.fields.maxDrawdown')} name="maxDrawdown">
                 <Input type="number" suffix="%" className="bg-white/5 border-white/10 text-white" />
              </Form.Item>
            </div>
          </section>
      </Form>
    </div>
  );
};

export default Settings;
