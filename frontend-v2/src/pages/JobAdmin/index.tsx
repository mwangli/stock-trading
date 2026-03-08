import React, { useState, useEffect } from 'react';
import { Card, Table, Tag, Button, Switch, Modal, Form, Input, Space, Typography, Tooltip, message } from 'antd';
import { ScheduleOutlined, PlayCircleOutlined, EditOutlined, ClockCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
const { Title, Text } = Typography;

interface Job {
  id: number;
  jobName: string;
  description: string;
  cronExpression: string;
  lastRunTime: string | null;
  status: number; // 1: Active, 0: Paused
}

const JobAdmin: React.FC = () => {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [editingJob, setEditingJob] = useState<Job | null>(null);
  const [loading, setLoading] = useState(false);
  // Mock data based on system requirements
  const mockJobs: Job[] = [
    {
      id: 1,
      jobName: 'stockListSync',
      description: '每周同步证券 API 股票代码和名称',
      cronExpression: '0 0 1 * * SUN',
      lastRunTime: '2024-03-03 01:00:00',
      status: 1,
    },
    {
      id: 2,
      jobName: 'dailyStockDataSync',
      description: '每日收盘后同步所有股票日 K 线数据',
      cronExpression: '0 0 18 * * MON-FRI',
      lastRunTime: '2024-03-08 18:00:00',
      status: 1,
    },
    {
      id: 3,
      jobName: 'historicalDataSync',
      description: '深度同步所有股票历史数据 (周维护)',
      cronExpression: '0 0 2 * * SUN',
      lastRunTime: '2024-03-03 02:00:00',
      status: 1,
    },
    {
      id: 4,
      jobName: 'newsCrawler',
      description: '每小时抓取财经新闻进行情感分析',
      cronExpression: '0 0 * * * *',
      lastRunTime: '2024-03-08 10:00:00',
      status: 0,
    },
    {
      id: 5,
      jobName: 'stockSelection',
      description: '运行双因子模型选出次日优选股',
      cronExpression: '0 0 17 ? * MON-FRI',
      lastRunTime: '2024-03-07 17:00:00',
      status: 1,
    },
    {
      id: 6,
      jobName: 'signalGeneration',
      description: '基于选股结果生成买入/卖出信号',
      cronExpression: '0 30 17 ? * MON-FRI',
      lastRunTime: '2024-03-07 17:30:00',
      status: 1,
    },
    {
      id: 7,
      jobName: 'intradayRiskControl',
      description: 'T+1 卖出检查、止损和止盈 (每分钟)',
      cronExpression: '0 * 9-11,13-15 ? * MON-FRI',
      lastRunTime: '2024-03-08 10:35:00',
      status: 1,
    },
    {
      id: 8,
      jobName: 'forceSellCheck',
      description: '收盘前强制卖出所有 T+1 持仓 (14:57)',
      cronExpression: '0 57 14 ? * MON-FRI',
      lastRunTime: '2024-03-07 14:57:00',
      status: 1,
    },
    {
      id: 9,
      jobName: 'strategySwitcher',
      description: '检查市场状况以切换活跃策略',
      cronExpression: '0 * 9-15 ? * MON-FRI',
      lastRunTime: '2024-03-08 10:35:00',
      status: 1,
    }
  ];

  const [jobs, setJobs] = useState<Job[]>(mockJobs);

  const fetchJobs = async () => {
    setLoading(true);
    // Simulate API delay
    setTimeout(() => {
        setJobs(mockJobs); // Reset to initial mock data or keep current state if persisted
        setLoading(false);
        // message.success(t('jobs.messages.fetchSuccess') || 'Jobs refreshed');
    }, 500);
    
    /* 
    try {
      const response = await axios.get('/api/jobs');
      setJobs(response.data);
    } catch (error) {
      console.error('Failed to fetch jobs:', error);
      message.error(t('jobs.messages.fetchError') || 'Failed to fetch jobs');
      // Fallback to mock data on error
      setJobs(mockJobs);
    } finally {
      setLoading(false);
    }
    */
  };

  useEffect(() => {
    // Initial load - use mock data directly
    // fetchJobs();
  }, []);

  const handleRun = async (job: Job) => {
    message.loading({ content: t('jobs.messages.starting', { name: job.jobName }), key: 'runJob' });
    // Simulate API call
    setTimeout(() => {
      message.success({ content: t('jobs.messages.started', { name: job.jobName }), key: 'runJob' });
      setJobs(jobs.map(j => j.id === job.id ? { ...j, lastRunTime: new Date().toLocaleString() } : j));
    }, 1000);

    /*
    try {
      await axios.post(`/api/jobs/${job.id}/run`);
      message.success({ content: t('jobs.messages.started', { name: job.jobName }), key: 'runJob' });
      setTimeout(fetchJobs, 1000);
    } catch (error) {
      console.error('Failed to run job:', error);
      message.error({ content: t('jobs.messages.runError') || 'Failed to run job', key: 'runJob' });
    }
    */
  };

  const toggleStatus = async (id: number, checked: boolean) => {
    // Simulate API call
    setJobs(jobs.map(j => j.id === id ? { ...j, status: checked ? 1 : 0 } : j));
    message.success(checked ? t('jobs.messages.resumed') : t('jobs.messages.paused'));

    /*
    try {
      await axios.post(`/api/jobs/${id}/status`, { active: checked });
      setJobs(jobs.map(j => j.id === id ? { ...j, status: checked ? 1 : 0 } : j));
      message.success(checked ? t('jobs.messages.resumed') : t('jobs.messages.paused'));
    } catch (error) {
      console.error('Failed to toggle status:', error);
      message.error(t('jobs.messages.updateError') || 'Failed to update status');
    }
    */
  };

  const showEditModal = (job: Job) => {
    setEditingJob(job);
    form.setFieldsValue(job);
    setIsModalVisible(true);
  };

  const handleUpdate = () => {
    form.validateFields().then(async values => {
      if (!editingJob) return;
      
      // Simulate API call
      setJobs(jobs.map(j => j.id === editingJob.id ? { ...j, cronExpression: values.cronExpression, description: values.description } : j));
      setIsModalVisible(false);
      message.success(t('jobs.messages.updated'));

      /*
      try {
        await axios.post(`/api/jobs/${editingJob.id}/cron`, { cron: values.cronExpression });
        setJobs(jobs.map(j => j.id === editingJob.id ? { ...j, cronExpression: values.cronExpression, description: values.description } : j));
        setIsModalVisible(false);
        message.success(t('jobs.messages.updated'));
      } catch (error) {
        console.error('Failed to update cron:', error);
        message.error(t('jobs.messages.updateError') || 'Failed to update cron');
      }
      */
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
      render: (_: any, record: Job) => (
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
