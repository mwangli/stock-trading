// AI_GENERATE_START -
import React, { useState, useEffect } from 'react';
import { Card, Table, Tag, Button, Switch, Modal, Form, Input, Space, Typography, Tooltip, message } from 'antd';
import { ScheduleOutlined, PlayCircleOutlined, EditOutlined, ClockCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import request from '../../utils/request';
const { Title, Text } = Typography;

interface Job {
  id: number;
  jobName: string;
  description: string;
  cronExpression: string;
  lastRunTime: string | null;
  status: number; // 1: Active, 0: Paused
}

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

const JobAdmin: React.FC = () => {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [editingJob, setEditingJob] = useState<Job | null>(null);
  const [loading, setLoading] = useState(false);
  const [jobs, setJobs] = useState<Job[]>([]);

  const fetchJobs = async () => {
    setLoading(true);
    try {
      const res = await request.get('/jobs') as ApiResponse<Job[]>;
      if (res.success && Array.isArray(res.data)) {
        setJobs(res.data.filter(job => job.id && job.jobName));
      } else {
        setJobs([]);
        message.error(res.message || '获取任务列表失败');
      }
    } catch (error) {
      console.error('获取任务列表失败:', error);
      setJobs([]);
      message.error('获取任务列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchJobs();
  }, []);

  const handleRun = async (job: Job) => {
    message.loading({ content: t('jobs.messages.starting', { name: job.jobName }), key: 'runJob' });
    try {
      await request.post(`/jobs/run/${job.id}`);
      message.success({ content: t('jobs.messages.started', { name: job.jobName }), key: 'runJob' });
      setTimeout(fetchJobs, 1000);
    } catch (error) {
      console.error('Failed to run job:', error);
      message.error({ content: t('jobs.messages.runError') || 'Failed to run job', key: 'runJob' });
    }
  };

  const toggleStatus = async (id: number, checked: boolean) => {
    try {
      await request.post(`/jobs/status/${id}`, { active: checked });
      setJobs(jobs.map(j => j.id === id ? { ...j, status: checked ? 1 : 0 } : j));
      message.success(checked ? t('jobs.messages.resumed') : t('jobs.messages.paused'));
    } catch (error) {
      console.error('Failed to toggle status:', error);
      message.error(t('jobs.messages.updateError') || 'Failed to update status');
    }
  };

  const showEditModal = (job: Job) => {
    setEditingJob(job);
    form.setFieldsValue(job);
    setIsModalVisible(true);
  };

  const handleUpdate = () => {
    form.validateFields().then(async values => {
      if (!editingJob) return;

      try {
        await request.post(`/jobs/cron/${editingJob.id}`, { cron: values.cronExpression });
        setJobs(jobs.map(j => j.id === editingJob.id ? { ...j, cronExpression: values.cronExpression, description: values.description } : j));
        setIsModalVisible(false);
        message.success(t('jobs.messages.updated'));
      } catch (error) {
        console.error('Failed to update cron:', error);
        message.error(t('jobs.messages.updateError') || 'Failed to update cron');
      }
    });
  };

  const columns = [
    {
      title: t('jobs.table.name'),
      dataIndex: 'jobName',
      key: 'jobName',
      render: (text: string, record: Job) => (
        <div>
          <div className="text-[#00e396] font-bold text-lg">{text}</div>
          <div className="text-gray-400 text-sm">{record.description}</div>
        </div>
      ),
    },
    {
      title: t('jobs.table.cron'),
      dataIndex: 'cronExpression',
      key: 'cronExpression',
      render: (text: string) => (
        <Tag color="blue" className="font-mono text-sm px-2 py-1 bg-blue-500/10 border-blue-500/30">
          <ClockCircleOutlined className="mr-1" />{text}
        </Tag>
      ),
    },
    {
      title: t('jobs.table.lastRun'),
      dataIndex: 'lastRunTime',
      key: 'lastRunTime',
      render: (text: string) => <span className="text-gray-300">{text ? new Date(text).toLocaleString() : '-'}</span>,
    },
    {
      title: t('jobs.table.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: number, record: Job) => (
        <Switch
          checked={status === 1}
          onChange={(checked) => toggleStatus(record.id, checked)}
          checkedChildren={t('jobs.status.active')}
          unCheckedChildren={t('jobs.status.paused')}
          className={status === 1 ? 'bg-[#00e396]' : 'bg-gray-600'}
        />
      ),
    },
    {
      title: t('jobs.table.actions'),
      key: 'actions',
      render: (_: unknown, record: Job) => (
        <Space size="middle">
          <Tooltip title={t('jobs.actions.run')}>
            <Button 
              type="primary" 
              shape="circle" 
              icon={<PlayCircleOutlined />} 
              onClick={() => handleRun(record)}
              className="bg-[#00e396] border-none hover:bg-[#00b374]"
            />
          </Tooltip>
          <Tooltip title={t('jobs.actions.edit')}>
            <Button 
              type="default" 
              shape="circle" 
              icon={<EditOutlined />} 
              onClick={() => showEditModal(record)}
              className="bg-transparent border-gray-600 text-gray-300 hover:text-[#00e396] hover:border-[#00e396]"
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center mb-6">
        <div>
          <Title level={2} className="!text-white flex items-center gap-2 mb-1">
            <ScheduleOutlined className="text-[#00e396]" />
            {t('jobs.title')}
          </Title>
          <Text className="text-gray-400">{t('jobs.subtitle')}</Text>
        </div>
        <Button 
            icon={<ReloadOutlined />} 
            onClick={fetchJobs} 
            loading={loading}
            className="bg-gray-800 text-white border-gray-700 hover:bg-gray-700 hover:text-[#00e396]"
        >
            {t('jobs.actions.refresh')}
        </Button>
      </div>

      <Card bordered={false} className="glass p-4 bg-gray-900/50 backdrop-blur-md border border-white/5 rounded-xl">
        <Table
          columns={columns}
          dataSource={jobs}
          rowKey="id"
          loading={loading}
          pagination={false}
          rowClassName="hover:bg-white/5 transition-colors"
        />
      </Card>

      <Modal
        title={<span className="text-white">{t('jobs.modal.title')}</span>}
        open={isModalVisible}
        onOk={handleUpdate}
        onCancel={() => setIsModalVisible(false)}
        okText={t('jobs.actions.save')}
        cancelText={t('jobs.actions.cancel')}
        className="dark-modal"
        styles={{ 
            header: { background: '#1f2937', borderBottom: '1px solid #374151' },
            body: { padding: '24px', background: '#1f2937' },
            footer: { background: '#1f2937', borderTop: '1px solid #374151' },
            mask: { backdropFilter: 'blur(4px)' }
        }}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="jobName" label={<span className="text-gray-300">{t('jobs.modal.name')}</span>}>
            <Input disabled className="bg-gray-800 border-gray-700 text-gray-400" />
          </Form.Item>
          <Form.Item 
            name="cronExpression" 
            label={<span className="text-gray-300">{t('jobs.modal.cron')}</span>}
            rules={[{ required: true, message: 'Please input cron expression!' }]}
          >
            <Input className="bg-gray-800 border-gray-700 text-white font-mono" />
          </Form.Item>
          <Form.Item name="description" label={<span className="text-gray-300">{t('jobs.modal.description')}</span>}>
            <Input.TextArea className="bg-gray-800 border-gray-700 text-white" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default JobAdmin;
// AI_GENERATE_END -
