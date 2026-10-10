// AI_GENERATE_START -
import React, { useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Select, Switch, Typography, message } from 'antd';
import { ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useAppTheme, type ThemeMode } from '../theme/AppThemeProvider';

const { Text, Title } = Typography;

interface SettingsFormValues {
  theme: ThemeMode;
  language: 'en' | 'zh';
  notifications: boolean;
  apiKey: string;
  refreshRate: number;
  riskLevel: 'conservative' | 'moderate' | 'aggressive';
  maxDrawdown: number;
}

const Settings: React.FC = () => {
  const [form] = Form.useForm<SettingsFormValues>();
  const { t, i18n } = useTranslation();
  const { mode, setMode } = useAppTheme();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: SettingsFormValues) => {
    setLoading(true);
    await new Promise<void>((resolve) => window.setTimeout(resolve, 500));
    setMode(values.theme);
    await i18n.changeLanguage(values.language);
    message.success(t('settings.successMsg'));
    setLoading(false);
  };

  return (
    <div className="app-page max-w-6xl">
      <div className="page-heading">
        <div>
          <Title level={2} className="page-title">{t('settings.title')}</Title>
          <Text className="page-subtitle">管理界面偏好、数据连接与交易风险参数</Text>
        </div>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={loading}
          onClick={() => form.submit()}
        >
          {t('settings.save')}
        </Button>
      </div>

      <Form<SettingsFormValues>
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{
          theme: mode,
          language: i18n.language.startsWith('zh') ? 'zh' : 'en',
          notifications: true,
          apiKey: '************************',
          refreshRate: 1000,
          riskLevel: 'moderate',
          maxDrawdown: 15,
        }}
      >
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <Card className="surface-card" title={t('settings.sections.general')}>
            <Form.Item label={t('settings.fields.theme')} name="theme">
              <Select
                options={[
                  { value: 'light', label: t('settings.options.theme.light') },
                  { value: 'dark', label: t('settings.options.theme.dark') },
                ]}
              />
            </Form.Item>
            <Form.Item label={t('settings.fields.language')} name="language">
              <Select
                options={[
                  { value: 'zh', label: t('settings.options.language.zh') },
                  { value: 'en', label: t('settings.options.language.en') },
                ]}
              />
            </Form.Item>
            <Form.Item label={t('settings.fields.notifications')} name="notifications" valuePropName="checked" className="!mb-0">
              <Switch />
            </Form.Item>
          </Card>

          <Card className="surface-card" title={t('settings.sections.api')}>
            <Form.Item label={t('settings.fields.apiKey')} name="apiKey">
              <Input.Password />
            </Form.Item>
            <Form.Item label={t('settings.fields.refreshRate')} name="refreshRate">
              <InputNumber min={250} max={60000} step={250} className="!w-full" />
            </Form.Item>
            <Button icon={<ReloadOutlined />} onClick={() => message.info('连接检测请求已发送')}>
              {t('settings.testConnection')}
            </Button>
          </Card>

          <Card className="surface-card lg:col-span-2" title={t('settings.sections.risk')}>
            <div className="grid grid-cols-1 gap-x-6 md:grid-cols-2">
              <Form.Item label={t('settings.fields.riskLevel')} name="riskLevel">
                <Select
                  options={[
                    { value: 'conservative', label: t('settings.options.risk.conservative') },
                    { value: 'moderate', label: t('settings.options.risk.moderate') },
                    { value: 'aggressive', label: t('settings.options.risk.aggressive') },
                  ]}
                />
              </Form.Item>
              <Form.Item label={t('settings.fields.maxDrawdown')} name="maxDrawdown">
                <InputNumber min={1} max={100} suffix="%" className="!w-full" />
              </Form.Item>
            </div>
          </Card>
        </div>
      </Form>
    </div>
  );
};

export default Settings;
// AI_GENERATE_END -