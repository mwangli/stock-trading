// AI_GENERATE_START------ ------------------
import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Form,
  InputNumber,
  Row,
  Select,
  Switch,
  Tag,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useAppTheme, type ThemeMode } from '../../theme/AppThemeProvider';
import request from '../../utils/request';
import { getApiErrorMessage, readApiData, type ApiResponse } from '../../utils/api';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import i18n from '../../i18n';

const { Text } = Typography;

const AUTO_REFRESH_INTERVAL_MS = 10_000;

interface SettingsFormValues {
  theme: ThemeMode;
  autoThemeEnabled: boolean;
  language: 'en' | 'zh';
  notifications: boolean;
  refreshRate: number;
  schedulerEnabled: boolean;
  startupDataSyncEnabled: boolean;
}

interface BrokerReadStatus {
  brokerName?: string;
  tradingMode?: string;
  authenticated: boolean;
  realWriteEnabled: boolean;
  liveWriteAllowed: boolean;
}

interface RealTradingStatus {
  realWriteEnabled: boolean;
  brokerAuthenticated: boolean;
  automaticExecutionEnabled: boolean;
}

interface RuntimeSettings {
  nodeId?: string;
  schedulerEnabled: boolean;
  startupDataSyncEnabled: boolean;
  updatedAt?: string;
}

interface ServiceHealth<T> {
  state: 'unknown' | 'up' | 'down';
  data?: T;
  error?: string;
}

const STORAGE_KEYS = {
  notifications: 'stock-settings-notifications',
};

const readBoolean = (key: string, fallback: boolean) => {
  const value = window.localStorage.getItem(key);
  return value === null ? fallback : value === 'true';
};

const formatDateTime = (value?: string) => {
  if (!value) {
    return i18n.t('settings.status.notLoaded');
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return i18n.t('settings.status.notConfigured');
  }
  return new Intl.DateTimeFormat(i18n.language.startsWith('zh') ? 'zh-CN' : 'en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date);
};

const healthTag = (state: ServiceHealth<unknown>['state']) => {
  if (state === 'up') {
    return <Tag color="green">{i18n.t('settings.status.normal')}</Tag>;
  }
  if (state === 'down') {
    return <Tag color="red">{i18n.t('settings.status.error')}</Tag>;
  }
  return <Tag>{i18n.t('settings.status.unchecked')}</Tag>;
};

const Settings: React.FC = () => {
  const [form] = Form.useForm<SettingsFormValues>();
  const { t, i18n } = useTranslation();
  const { mode, autoThemeEnabled, setMode, setAutoThemeEnabled, resetToAutomatic } = useAppTheme();
  const formAutoThemeEnabled = Form.useWatch('autoThemeEnabled', form);
  const [saving, setSaving] = useState(false);
  const [checking, setChecking] = useState(false);
  const [runtimeLoading, setRuntimeLoading] = useState(true);
  const [runtimeSettings, setRuntimeSettings] = useState<RuntimeSettings | null>(null);
  const [lastStatusCheckedAt, setLastStatusCheckedAt] = useState<string>();
  const [brokerHealth, setBrokerHealth] = useState<ServiceHealth<BrokerReadStatus>>({ state: 'unknown' });
  const [tradingHealth, setTradingHealth] = useState<ServiceHealth<RealTradingStatus>>({ state: 'unknown' });
  const loadRuntimeSettings = useCallback(async () => {
    setRuntimeLoading(true);
    try {
      const response = await request.get('/systemSettings/runtime') as ApiResponse<RuntimeSettings>;
      const settings = readApiData(response, '读取运行控制配置失败');
      setRuntimeSettings(settings);
      form.setFieldsValue(settings);
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '读取运行控制配置失败'));
    } finally {
      setRuntimeLoading(false);
    }
  }, [form]);

  const checkSystemStatus = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setChecking(true);
    }
    const [brokerResult, tradingResult] = await Promise.allSettled([
      request.get('/brokerQuery/status') as Promise<ApiResponse<BrokerReadStatus>>,
      request.get('/realTrading/status') as Promise<ApiResponse<RealTradingStatus>>,
    ]);

    if (brokerResult.status === 'fulfilled') {
      try {
        setBrokerHealth({ state: 'up', data: readApiData(brokerResult.value, '读取券商状态失败') });
      } catch (cause) {
        setBrokerHealth({ state: 'down', error: getApiErrorMessage(cause, '读取券商状态失败') });
      }
    } else {
      setBrokerHealth({ state: 'down', error: getApiErrorMessage(brokerResult.reason, '券商状态接口不可用') });
    }

    if (tradingResult.status === 'fulfilled') {
      try {
        setTradingHealth({ state: 'up', data: readApiData(tradingResult.value, '读取真实交易状态失败') });
      } catch (cause) {
        setTradingHealth({ state: 'down', error: getApiErrorMessage(cause, '读取真实交易状态失败') });
      }
    } else {
      setTradingHealth({ state: 'down', error: getApiErrorMessage(tradingResult.reason, '真实交易状态接口不可用') });
    }

    setLastStatusCheckedAt(new Date().toISOString());
    if (showLoading) {
      setChecking(false);
    }
  }, []);

  useEffect(() => {
    void checkSystemStatus();
    void loadRuntimeSettings();
  }, [checkSystemStatus, loadRuntimeSettings]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void checkSystemStatus(false);
    }, AUTO_REFRESH_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [checkSystemStatus]);

  const onFinish = async (values: SettingsFormValues) => {
    setSaving(true);
    try {
      const runtimeRequest: RuntimeSettings = {
        schedulerEnabled: values.schedulerEnabled,
        startupDataSyncEnabled: values.startupDataSyncEnabled,
      };
      const response = await request.put('/systemSettings/runtime', runtimeRequest) as ApiResponse<RuntimeSettings>;
      setRuntimeSettings(readApiData(response, '保存运行控制配置失败'));
      window.localStorage.setItem(STORAGE_KEYS.notifications, String(values.notifications));
      setAutoThemeEnabled(values.autoThemeEnabled);
      if (!values.autoThemeEnabled) {
        setMode(values.theme);
      }
      await i18n.changeLanguage(values.language);
      message.success(t('settings.successMsg'));
    } catch (cause) {
      message.error(getApiErrorMessage(cause, '保存系统配置失败'));
    } finally {
      setSaving(false);
    }
  };

  const resetPreferences = () => {
    window.localStorage.removeItem(STORAGE_KEYS.notifications);
    const automaticMode = resetToAutomatic();
    form.setFieldsValue({
      theme: automaticMode,
      autoThemeEnabled: true,
      language: i18n.language.startsWith('zh') ? 'zh' : 'en',
      notifications: true,
      refreshRate: AUTO_REFRESH_INTERVAL_MS,
    });
    message.success('界面偏好已恢复默认值');
  };

  const formInitialValues: SettingsFormValues = {
    theme: mode,
    autoThemeEnabled,
    language: i18n.language.startsWith('zh') ? 'zh' : 'en',
    notifications: readBoolean(STORAGE_KEYS.notifications, true),
    refreshRate: AUTO_REFRESH_INTERVAL_MS,
    schedulerEnabled: false,
    startupDataSyncEnabled: false,
  };

  usePageHeader(t('settings.title'));

  return (
    <div className="app-page">
      <div className="settings-summary-strip">
        <div className="settings-summary-content">
          <Text type="secondary">{t('settings.summary.title')}</Text>
          {healthTag(brokerHealth.state)}
          <Tag color={brokerHealth.data?.authenticated ? 'green' : 'red'}>
            {brokerHealth.data?.authenticated ? t('settings.summary.brokerSessionValid') : t('settings.summary.brokerSessionInvalid')}
          </Tag>
          <Tag color={tradingHealth.data?.realWriteEnabled ? 'red' : 'blue'}>
            {tradingHealth.data?.realWriteEnabled ? t('settings.status.realWriteOn') : t('settings.summary.realWriteOff')}
          </Tag>
          <Tag color={tradingHealth.data?.automaticExecutionEnabled ? 'green' : 'gold'}>
            {tradingHealth.data?.automaticExecutionEnabled ? t('settings.status.autoReady') : t('settings.summary.autoExecutionStopped')}
          </Tag>
        </div>
        <Button icon={<ReloadOutlined />} loading={checking} onClick={() => void checkSystemStatus()}>{t('settings.summary.refreshStatus')}</Button>
      </div>

      <Form<SettingsFormValues>
        className="settings-form"
        form={form}
        layout="vertical"
        initialValues={formInitialValues}
        onFinish={onFinish}
      >
        <div className="settings-main-grid">
          <Card className="surface-card" title={t('settings.generalTitle')} extra={<Button onClick={resetPreferences}>{t('settings.resetPreferences')}</Button>}>
            <Row gutter={[24, 8]}>
              <Col xs={24} md={12}>
                <Form.Item label={t('settings.fields.autoTheme')} name="autoThemeEnabled" valuePropName="checked" extra={t('settings.themeAutoHint')}>
                  <Switch checkedChildren={t('settings.switchOn')} unCheckedChildren={t('settings.switchOff')} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item label={t('settings.fields.theme')} name="theme">
                  <Select disabled={formAutoThemeEnabled ?? autoThemeEnabled} options={[{ value: 'light', label: t('settings.options.theme.light') }, { value: 'dark', label: t('settings.options.theme.dark') }]} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item label={t('settings.fields.language')} name="language">
                  <Select options={[{ value: 'zh', label: t('settings.options.language.zh') }, { value: 'en', label: t('settings.options.language.en') }]} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item label={t('settings.fields.notifications')} name="notifications" valuePropName="checked">
                  <Switch checkedChildren={t('settings.switchOn')} unCheckedChildren={t('settings.switchOff')} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item label={t('settings.fields.refreshRate')} name="refreshRate" extra={t('settings.refreshHint')}>
                  <InputNumber disabled addonAfter="ms" className="!w-full" />
                </Form.Item>
              </Col>
            </Row>
          </Card>

          <Card className="surface-card settings-runtime-card" title={t('settings.runtimeTitle')} loading={runtimeLoading} extra={<Text type="secondary">{t('settings.runtime.justUpdated')}</Text>}>
            <div className="settings-runtime-list">
              <Form.Item label={t('settings.fieldsRuntime.scheduler')} name="schedulerEnabled" valuePropName="checked" extra={t('settings.runtime.schedulerHint')}>
                <Switch checkedChildren={t('settings.switchOn')} unCheckedChildren={t('settings.switchOff')} />
              </Form.Item>
              <Form.Item label={t('settings.fieldsRuntime.startupData')} name="startupDataSyncEnabled" valuePropName="checked" extra={t('settings.runtime.startupDataHint')}>
                <Switch checkedChildren={t('settings.switchOn')} unCheckedChildren={t('settings.switchOff')} />
              </Form.Item>
            </div>
            <div className="settings-safety-grid">
              <div><Text type="secondary">{t('settings.safety.realWrite')}</Text><div><Tag color={tradingHealth.data?.realWriteEnabled ? 'red' : 'blue'}>{tradingHealth.data?.realWriteEnabled ? t('settings.switchOn') : t('settings.switchOff')}</Tag></div></div>
              <div><Text type="secondary">{t('settings.safety.unattended')}</Text><div><Tag color={tradingHealth.data?.automaticExecutionEnabled ? 'green' : 'gold'}>{tradingHealth.data?.automaticExecutionEnabled ? t('settings.status.autoReady') : t('settings.safety.notReady')}</Tag></div></div>
              <div><Text type="secondary">{t('settings.safety.brokerSession')}</Text><div><Tag color={brokerHealth.data?.authenticated ? 'green' : 'red'}>{brokerHealth.data?.authenticated ? t('settings.status.sessionValid') : t('settings.status.sessionMissing')}</Tag></div></div>
            </div>
          </Card>
        </div>

        <Card className="surface-card settings-info-card" title={t('settings.info.title')} extra={<Text type="secondary">{t('settings.info.checkedAt', { time: formatDateTime(lastStatusCheckedAt) })}</Text>}>
          <div className="settings-info-layout">
            <div className="settings-info-grid">
              <div className="settings-info-item"><Text type="secondary">{t('settings.info.nodeId')}</Text><strong>{runtimeSettings?.nodeId || t('settings.status.notConfigured')}</strong></div>
              <div className="settings-info-item"><Text type="secondary">{t('settings.info.updatedAt')}</Text><strong>{formatDateTime(runtimeSettings?.updatedAt)}</strong></div>
              <div className="settings-info-item"><Text type="secondary">{t('settings.info.refreshCycle')}</Text><strong>{t('settings.info.seconds', { seconds: AUTO_REFRESH_INTERVAL_MS / 1000 })}</strong></div>
              <div className="settings-info-item"><Text type="secondary">{t('settings.info.schedulerMode')}</Text><strong>{runtimeSettings?.schedulerEnabled ? t('settings.info.unifiedScheduler') : t('settings.info.schedulerDisabled')}</strong></div>
              <div className="settings-info-item"><Text type="secondary">{t('settings.info.preferenceStorage')}</Text><strong>{t('settings.info.currentBrowser')}</strong></div>
              <div className="settings-info-item"><Text type="secondary">{t('settings.info.runtimeStorage')}</Text><strong>{t('settings.info.serverPersisted')}</strong></div>
            </div>
            <div className="settings-health-list">
              <div className="settings-health-row"><Text type="secondary">{t('settings.health.gateway')}</Text><span>{brokerHealth.data?.brokerName || t('settings.status.notConfigured')}</span>{healthTag(brokerHealth.state)}</div>
              <div className="settings-health-row"><Text type="secondary">{t('settings.health.brokerSession')}</Text><span>{brokerHealth.error || brokerHealth.data?.tradingMode || t('settings.status.readOnly')}</span><Tag color={brokerHealth.data?.authenticated ? 'green' : 'red'}>{brokerHealth.data?.authenticated ? t('settings.status.sessionValid') : t('settings.status.sessionMissing')}</Tag></div>
              <div className="settings-health-row"><Text type="secondary">{t('settings.health.tradingGate')}</Text><span>{tradingHealth.error || (tradingHealth.data?.automaticExecutionEnabled ? t('settings.status.autoReady') : t('settings.status.controlledStop'))}</span><Tag color={tradingHealth.data?.realWriteEnabled ? 'red' : 'blue'}>{tradingHealth.data?.realWriteEnabled ? t('settings.status.realWriteOn') : t('settings.status.readOnly')}</Tag></div>
            </div>
          </div>
        </Card>

        <div className="settings-save-row">
          <Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={!runtimeSettings} onClick={() => form.submit()}>{t('settings.save')}</Button>
        </div>
      </Form>
    </div>
  );
};

export default Settings;
// AI_GENERATE_END------ ------------------
