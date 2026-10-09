import React, { useState } from 'react';
import { Form, Input, Switch, Button, Select, Divider, message } from 'antd';
import { SaveOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

const { Option } = Select;

const Settings: React.FC = () => {
  const [form] = Form.useForm();
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: any) => {
    setLoading(true);
    await new Promise(resolve => setTimeout(resolve, 1000));
    console.log(values);
    message.success(t('settings.successMsg'));
    setLoading(false);
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
          apiKey: '************************',
          riskLevel: 'moderate',
          maxDrawdown: 15,
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

          {/* API & Data */}
          <section>
            <h3 className="text-xl font-bold text-[#00e396] mb-4">{t('settings.sections.api')}</h3>
            <Form.Item label={t('settings.fields.apiKey')} name="apiKey">
              <Input.Password className="bg-white/5 border-white/10 text-white" />
            </Form.Item>
            <Form.Item label={t('settings.fields.refreshRate')} name="refreshRate" initialValue={1000}>
               <Input type="number" className="bg-white/5 border-white/10 text-white" />
            </Form.Item>
            <Button icon={<ReloadOutlined />} className="mt-2 bg-transparent text-[#00e396] border-[#00e396]">{t('settings.testConnection')}</Button>
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
