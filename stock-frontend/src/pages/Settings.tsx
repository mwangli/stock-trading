// AI_GENERATE_START --------
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  InputNumber,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  CloudServerOutlined,
  LockOutlined,
  ReloadOutlined,
  SaveOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useAppTheme, type ThemeMode } from '../theme/AppThemeProvider';
import request from '../utils/request';
import { usePageHeader } from '../layouts/PageHeaderContext';

const { Text } = Typography;

interface SettingsFormValues {
  theme: ThemeMode;
  language: 'en' | 'zh';
  notifications: boolean;
  refreshRate: number;
  schedulerEnabled: boolean;
  dataCollectionEnabled: boolean;
  newsCollectionEnabled: boolean;
  startupDataSyncEnabled: boolean;
  startupHistorySyncEnabled: boolean;
  startupNewsSyncEnabled: boolean;
  modelTrainingEnabled: boolean;
}

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
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

interface SentimentHealth {
  status: string;
  service: string;
  modelLoaded: boolean;
  lastLoadedTime?: string;
}

interface RuntimeSettings {
  nodeId?: string;
  schedulerEnabled: boolean;
  dataCollectionEnabled: boolean;
  newsCollectionEnabled: boolean;
  startupDataSyncEnabled: boolean;
  startupHistorySyncEnabled: boolean;
  startupNewsSyncEnabled: boolean;
  modelTrainingEnabled: boolean;
  updatedAt?: string;
}

interface ServiceHealth<T> {
  state: 'unknown' | 'up' | 'down';
  data?: T;
  error?: string;
}

const STORAGE_KEYS = {
  notifications: 'stock-settings-notifications',
  refreshRate: 'stock-settings-refresh-rate',
};

const readBoolean = (key: string, fallback: boolean) => {
  const value = window.localStorage.getItem(key);
  return value === null ? fallback : value === 'true';
};

const readNumber = (key: string, fallback: number) => {
  const value = Number(window.localStorage.getItem(key));
  return Number.isFinite(value) && value >= 5000 && value <= 60000 ? value : fallback;
};

const formatDateTime = (value?: string) => {
  if (!value) {
    return '未加载';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '时间未知';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date);
};

const getErrorMessage = (cause: unknown, fallback: string) => {
  if (axios.isAxiosError<ApiResponse<unknown>>(cause)) {
    return cause.response?.data?.message || cause.message || fallback;
  }
  return cause instanceof Error ? cause.message : fallback;
};

const responseData = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (!response.success) {
    throw new Error(response.message || fallback);
  }
  return response.data;
};

const healthTag = (state: ServiceHealth<unknown>['state']) => {
  if (state === 'up') {
    return <Tag color="green">正常</Tag>;
  }
  if (state === 'down') {
    return <Tag color="red">异常</Tag>;
  }
  return <Tag>未检查</Tag>;
};

const Settings: React.FC = () => {
  const [form] = Form.useForm<SettingsFormValues>();
  const configuredRefreshRate = Form.useWatch('refreshRate', form);
  const { t, i18n } = useTranslation();
  const { mode, setMode } = useAppTheme();
  const [saving, setSaving] = useState(false);
  const [checking, setChecking] = useState(false);
  const [runtimeLoading, setRuntimeLoading] = useState(true);
  const [runtimeSettings, setRuntimeSettings] = useState<RuntimeSettings | null>(null);
  const [, setStatusError] = useState<string | null>(null);
  const [brokerHealth, setBrokerHealth] = useState<ServiceHealth<BrokerReadStatus>>({ state: 'unknown' });
  const [tradingHealth, setTradingHealth] = useState<ServiceHealth<RealTradingStatus>>({ state: 'unknown' });
  const [sentimentHealth, setSentimentHealth] = useState<ServiceHealth<SentimentHealth>>({ state: 'unknown' });
  const dataCollectionEnabled = Form.useWatch('dataCollectionEnabled', form);
  const newsCollectionEnabled = Form.useWatch('newsCollectionEnabled', form);

  const loadRuntimeSettings = useCallback(async () => {
    setRuntimeLoading(true);
    try {
      const response = await request.get('/system-settings/runtime') as ApiResponse<RuntimeSettings>;
      const settings = responseData(response, '读取运行控制配置失败');
      setRuntimeSettings(settings);
      form.setFieldsValue(settings);
    } catch (cause) {
      message.error(getErrorMessage(cause, '读取运行控制配置失败'));
    } finally {
      setRuntimeLoading(false);
    }
  }, [form]);

  const checkSystemStatus = useCallback(async () => {
    setChecking(true);
    setStatusError(null);
    const [brokerResult, tradingResult, sentimentResult] = await Promise.allSettled([
      request.get('/broker-query/status') as Promise<ApiResponse<BrokerReadStatus>>,
      request.get('/real-trading/status') as Promise<ApiResponse<RealTradingStatus>>,
      request.get('/model-sentiment/health') as Promise<ApiResponse<SentimentHealth>>,
    ]);

    if (brokerResult.status === 'fulfilled') {
      try {
        setBrokerHealth({ state: 'up', data: responseData(brokerResult.value, '读取券商状态失败') });
      } catch (cause) {
        setBrokerHealth({ state: 'down', error: getErrorMessage(cause, '读取券商状态失败') });
      }
    } else {
      setBrokerHealth({ state: 'down', error: getErrorMessage(brokerResult.reason, '券商状态接口不可用') });
    }

    if (tradingResult.status === 'fulfilled') {
      try {
        setTradingHealth({ state: 'up', data: responseData(tradingResult.value, '读取真实交易状态失败') });
      } catch (cause) {
        setTradingHealth({ state: 'down', error: getErrorMessage(cause, '读取真实交易状态失败') });
      }
    } else {
      setTradingHealth({ state: 'down', error: getErrorMessage(tradingResult.reason, '真实交易状态接口不可用') });
    }

    if (sentimentResult.status === 'fulfilled') {
      try {
        setSentimentHealth({ state: 'up', data: responseData(sentimentResult.value, '读取情感模型状态失败') });
      } catch (cause) {
        setSentimentHealth({ state: 'down', error: getErrorMessage(cause, '读取情感模型状态失败') });
      }
    } else {
      setSentimentHealth({ state: 'down', error: getErrorMessage(sentimentResult.reason, '情感模型状态接口不可用') });
    }

    if ([brokerResult, tradingResult, sentimentResult].every((result) => result.status === 'rejected')) {
      setStatusError('后端服务未响应，请确认后端已启动。');
    }
    setChecking(false);
  }, []);

  useEffect(() => {
    void checkSystemStatus();
    void loadRuntimeSettings();
  }, [checkSystemStatus, loadRuntimeSettings]);

  useEffect(() => {
    const refreshRate = configuredRefreshRate ?? readNumber(STORAGE_KEYS.refreshRate, 30000);
    const timer = window.setInterval(() => {
      void checkSystemStatus();
    }, refreshRate);
    return () => window.clearInterval(timer);
  }, [checkSystemStatus, configuredRefreshRate]);

  const onFinish = async (values: SettingsFormValues) => {
    setSaving(true);
    try {
      const runtimeRequest: RuntimeSettings = {
        schedulerEnabled: values.schedulerEnabled,
        dataCollectionEnabled: values.dataCollectionEnabled,
        newsCollectionEnabled: values.newsCollectionEnabled,
        startupDataSyncEnabled: values.startupDataSyncEnabled,
        startupHistorySyncEnabled: values.startupHistorySyncEnabled,
        startupNewsSyncEnabled: values.startupNewsSyncEnabled,
        modelTrainingEnabled: values.modelTrainingEnabled,
      };
      const response = await request.put('/system-settings/runtime', runtimeRequest) as ApiResponse<RuntimeSettings>;
      setRuntimeSettings(responseData(response, '保存运行控制配置失败'));
      window.localStorage.setItem(STORAGE_KEYS.notifications, String(values.notifications));
      window.localStorage.setItem(STORAGE_KEYS.refreshRate, String(values.refreshRate));
      setMode(values.theme);
      await i18n.changeLanguage(values.language);
      message.success(t('settings.successMsg'));
    } catch (cause) {
      message.error(getErrorMessage(cause, '保存系统配置失败'));
    } finally {
      setSaving(false);
    }
  };

  const resetPreferences = () => {
    window.localStorage.removeItem(STORAGE_KEYS.notifications);
    window.localStorage.removeItem(STORAGE_KEYS.refreshRate);
    setMode('light');
    form.setFieldsValue({
      theme: 'light',
      language: i18n.language.startsWith('zh') ? 'zh' : 'en',
      notifications: true,
      refreshRate: 30000,
    });
    message.success('界面偏好已恢复默认值');
  };

  const formInitialValues: SettingsFormValues = {
    theme: mode,
    language: i18n.language.startsWith('zh') ? 'zh' : 'en',
    notifications: readBoolean(STORAGE_KEYS.notifications, true),
    refreshRate: readNumber(STORAGE_KEYS.refreshRate, 30000),
    schedulerEnabled: false,
    dataCollectionEnabled: false,
    newsCollectionEnabled: false,
    startupDataSyncEnabled: false,
    startupHistorySyncEnabled: false,
    startupNewsSyncEnabled: false,
    modelTrainingEnabled: false,
  };

  const headerActions = useMemo(() => (
    <Space wrap>
      <Button icon={<ReloadOutlined />} loading={checking} onClick={() => void checkSystemStatus()}>检查服务状态</Button>
      <Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={!runtimeSettings} onClick={() => form.submit()}>{t('settings.save')}</Button>
    </Space>
  ), [checkSystemStatus, checking, form, runtimeSettings, saving, t]);

  usePageHeader('管理界面偏好、服务健康状态与生产安全边界', headerActions);

  return (
    <div className="app-page">
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={8}>
          <Card className="surface-card compact-card" loading={checking}>
            <Statistic title="券商只读网关" value={brokerHealth.data?.brokerName || '未配置'} prefix={<CloudServerOutlined />} />
            <div className="mt-3">{healthTag(brokerHealth.state)} <Text type="secondary" className="ml-2">{brokerHealth.data?.authenticated ? '会话有效' : '会话未建立'}</Text></div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Card className="surface-card compact-card" loading={checking}>
            <Statistic title="真实交易门禁" value={tradingHealth.data?.automaticExecutionEnabled ? '自动执行就绪' : '受控停止'} prefix={<LockOutlined />} />
            <div className="mt-3">
              <Tag color={tradingHealth.data?.realWriteEnabled ? 'red' : 'blue'}>{tradingHealth.data?.realWriteEnabled ? '真实写入开启' : '只读观察'}</Tag>
              {healthTag(tradingHealth.state)}
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={8}>
          <Card className="surface-card compact-card" loading={checking}>
            <Statistic title="情感模型" value={sentimentHealth.data?.modelLoaded ? '已加载' : '未加载'} prefix={<CheckCircleOutlined />} />
            <div className="mt-3">{healthTag(sentimentHealth.state)} <Text type="secondary" className="ml-2">{formatDateTime(sentimentHealth.data?.lastLoadedTime)}</Text></div>
          </Card>
        </Col>
      </Row>

      <Form<SettingsFormValues>
        form={form}
        layout="vertical"
        initialValues={formInitialValues}
        onFinish={onFinish}
      >
        <Card className="surface-card" title="界面偏好">
          <Row gutter={[24, 8]}>
            <Col xs={24} md={12}>
              <Form.Item label={t('settings.fields.theme')} name="theme">
                <Select options={[{ value: 'light', label: t('settings.options.theme.light') }, { value: 'dark', label: t('settings.options.theme.dark') }]} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label={t('settings.fields.language')} name="language">
                <Select options={[{ value: 'zh', label: t('settings.options.language.zh') }, { value: 'en', label: t('settings.options.language.en') }]} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label={t('settings.fields.notifications')} name="notifications" valuePropName="checked">
                <Switch checkedChildren="开" unCheckedChildren="关" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="服务状态自动检查间隔" name="refreshRate" extra="仅影响本页面状态检查，不会改变 Quartz 后端任务调度频率">
                <InputNumber min={5000} max={60000} step={5000} addonAfter="ms" className="!w-full" />
              </Form.Item>
            </Col>
          </Row>
          <Button onClick={resetPreferences}>恢复默认偏好</Button>
        </Card>

        <Card
          className="surface-card"
          title="运行控制"
          loading={runtimeLoading}
          extra={<Text type="secondary">最后更新：{formatDateTime(runtimeSettings?.updatedAt)}</Text>}
        >
          <Alert
            type="info"
            showIcon
            message="页面配置优先生效"
            description={`当前节点：${runtimeSettings?.nodeId || '未识别'}。环境变量和 application.yml 仅用于该节点首次初始化默认值，保存后写入数据库。不同节点应配置唯一的 APP_NODE_ID。`}
            className="mb-4"
          />
          <Row gutter={[24, 8]}>
            <Col xs={24} md={12} xl={8}>
              <Form.Item label="统一任务调度" name="schedulerEnabled" valuePropName="checked" extra="关闭后禁止定时和手动触发 Quartz 固定任务">
                <Switch checkedChildren="开" unCheckedChildren="关" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={8}>
              <Form.Item label="数据采集总开关" name="dataCollectionEnabled" valuePropName="checked" extra="控制股票列表、行情、历史数据和新闻采集">
                <Switch
                  checkedChildren="开"
                  unCheckedChildren="关"
                  onChange={(checked) => {
                    if (!checked) {
                      form.setFieldsValue({
                        newsCollectionEnabled: false,
                        startupDataSyncEnabled: false,
                        startupHistorySyncEnabled: false,
                        startupNewsSyncEnabled: false,
                      });
                    }
                  }}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={8}>
              <Form.Item label="新闻采集" name="newsCollectionEnabled" valuePropName="checked" extra="控制新闻和公告的定时及手动采集">
                <Switch
                  checkedChildren="开"
                  unCheckedChildren="关"
                  disabled={!dataCollectionEnabled}
                  onChange={(checked) => {
                    if (!checked) {
                      form.setFieldValue('startupNewsSyncEnabled', false);
                    }
                  }}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={8}>
              <Form.Item label="启动数据初始化" name="startupDataSyncEnabled" valuePropName="checked" extra="下次应用启动时检查并补充股票基础数据">
                <Switch checkedChildren="开" unCheckedChildren="关" disabled={!dataCollectionEnabled} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={8}>
              <Form.Item label="启动历史行情同步" name="startupHistorySyncEnabled" valuePropName="checked" extra="下次应用启动时执行全量历史行情同步">
                <Switch checkedChildren="开" unCheckedChildren="关" disabled={!dataCollectionEnabled} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={8}>
              <Form.Item label="启动新闻同步" name="startupNewsSyncEnabled" valuePropName="checked" extra="下次应用启动时执行全量新闻采集">
                <Switch checkedChildren="开" unCheckedChildren="关" disabled={!dataCollectionEnabled || !newsCollectionEnabled} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} xl={8}>
              <Form.Item label="LSTM 模型训练" name="modelTrainingEnabled" valuePropName="checked" extra="只开放训练资格，交易时段、资源和数据质量门禁仍会继续生效">
                <Switch checkedChildren="开" unCheckedChildren="关" />
              </Form.Item>
            </Col>
          </Row>
        </Card>
      </Form>

      <Card className="surface-card" title={<Space><WarningOutlined />生产安全边界</Space>}>
        <Alert
          type="warning"
          showIcon
          message="敏感配置不在浏览器中保存"
          description="券商账号、密码、验证码服务密钥和真实交易写入开关仍只允许通过后端部署配置管理，不会保存到浏览器或运行配置表。"
        />
        <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
          <div><Text type="secondary">真实写入</Text><div className="mt-1">{tradingHealth.data?.realWriteEnabled ? <Tag color="red">已开启</Tag> : <Tag color="blue">已关闭</Tag>}</div></div>
          <div><Text type="secondary">无人值守执行</Text><div className="mt-1">{tradingHealth.data?.automaticExecutionEnabled ? <Tag color="orange">已具备条件</Tag> : <Tag>未就绪</Tag>}</div></div>
          <div><Text type="secondary">券商会话</Text><div className="mt-1">{brokerHealth.data?.authenticated ? <Tag color="green">有效</Tag> : <Tag color="red">无效</Tag>}</div></div>
        </div>
      </Card>
    </div>
  );
};

export default Settings;
// AI_GENERATE_END --------
