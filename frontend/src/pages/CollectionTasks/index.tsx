import React, { useState, useEffect } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Card, Tag, Button, Space, Modal, Form, Input, Select, InputNumber, message, Popconfirm } from 'antd';
import { PlusOutlined, StopOutlined, DeleteOutlined, SyncOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';
import LogViewer from '@/components/LogViewer';

interface CollectionTask {
  id: number;
  taskName: string;
  taskType: string;
  status: string;
  categoryFilter?: string;
  maxProducts: number;
  cronExpression?: string;
  nextRunTime?: string;
  actualCount?: number;
  successCount?: number;
  failedCount?: number;
  filteredCount?: number;
  durationSeconds?: number;
  errorMessage?: string;
  isEnabled: boolean;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

const CollectionTasksPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<CollectionTask[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>(undefined);

  // 获取任务列表
  const fetchTasks = async () => {
    setLoading(true);
    try {
      const response = await request('/api/collection-tasks', {
        method: 'GET',
      });
      
      if (response.success) {
        setData(response.data || []);
      } else {
        message.error('获取任务列表失败');
      }
    } catch (error) {
      console.error('Error fetching tasks:', error);
      message.error('获取任务列表失败');
    } finally {
      setLoading(false);
    }
  };

  // 停止任务
  const stopTask = async (taskId: number) => {
    try {
      const response = await request(`/api/collection-tasks/${taskId}/stop`, {
        method: 'POST',
      });
      
      if (response.success) {
        message.success('任务已停止');
        fetchTasks();
      } else {
        message.error(response.message || '停止任务失败');
      }
    } catch (error) {
      console.error('Error stopping task:', error);
      message.error('停止任务失败');
    }
  };

  // 删除任务
  const deleteTask = async (taskId: number) => {
    try {
      const response = await request(`/api/collection-tasks/${taskId}`, {
        method: 'DELETE',
      });
      
      if (response.success) {
        message.success('任务删除成功');
        fetchTasks();
      } else {
        message.error(response.message || '删除任务失败');
      }
    } catch (error) {
      console.error('Error deleting task:', error);
      message.error('删除任务失败');
    }
  };

  // 启用/禁用任务
  const toggleTask = async (taskId: number) => {
    try {
      const response = await request(`/api/collection-tasks/${taskId}/toggle`, {
        method: 'POST',
      });
      
      if (response.success) {
        message.success(response.message);
        fetchTasks();
      } else {
        message.error(response.message || '操作失败');
      }
    } catch (error) {
      console.error('Error toggling task:', error);
      message.error('操作失败');
    }
  };

  // 创建新任务
  const handleSubmit = async (values: any) => {
    try {
      const response = await request('/api/collection-tasks', {
        method: 'POST',
        data: values,
      });
      
      if (response.success) {
        message.success('任务创建成功');
        setModalVisible(false);
        form.resetFields();
        fetchTasks();
      } else {
        message.error(response.message || '创建任务失败');
      }
    } catch (error) {
      console.error('Error creating task:', error);
      message.error('创建任务失败');
    }
  };

  useEffect(() => {
    fetchTasks();
  }, []);

  // 状态标签渲染
  const renderStatus = (status: string) => {
    const statusMap: Record<string, { color: string; text: string }> = {
      PENDING: { color: 'default', text: '等待中' },
      RUNNING: { color: 'processing', text: '执行中' },
      COMPLETED: { color: 'success', text: '已完成' },
      FAILED: { color: 'error', text: '失败' },
    };
    const config = statusMap[status] || { color: 'default', text: status };
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  // 任务类型标签
  const renderTaskType = (type: string) => {
    return type === 'SCHEDULED' ? (
      <Tag color="blue">定时任务</Tag>
    ) : (
      <Tag>手动任务</Tag>
    );
  };

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 60,
    },
    {
      title: '任务名称',
      dataIndex: 'taskName',
      key: 'taskName',
      width: 180,
    },
    {
      title: '类型',
      dataIndex: 'taskType',
      key: 'taskType',
      width: 100,
      render: (type: string) => renderTaskType(type),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => renderStatus(status),
    },
    {
      title: 'Cron表达式',
      dataIndex: 'cronExpression',
      key: 'cronExpression',
      width: 140,
      render: (cron: string) => cron || '-',
    },
    {
      title: '下次执行',
      dataIndex: 'nextRunTime',
      key: 'nextRunTime',
      width: 160,
      render: (time: string) => time ? new Date(time).toLocaleString('zh-CN') : '-',
    },
    {
      title: '采集数量',
      key: 'count',
      width: 120,
      render: (_: any, record: CollectionTask) => {
        if (!record.actualCount) return '-';
        return (
          <Space direction="vertical" size={0}>
            <span>总数: {record.actualCount}</span>
            <Tag color="green">成功: {record.successCount || 0}</Tag>
            <Tag color="red">失败: {record.failedCount || 0}</Tag>
          </Space>
        );
      },
    },
    {
      title: '耗时(秒)',
      dataIndex: 'durationSeconds',
      key: 'durationSeconds',
      width: 80,
      render: (seconds: number) => seconds || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (date: string) => new Date(date).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: any, record: CollectionTask) => (
        <Space size="small">
          {record.status === 'RUNNING' ? (
            <Popconfirm
              title="确定要停止这个任务吗？"
              onConfirm={() => stopTask(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button size="small" danger icon={<StopOutlined />}>
                停止
              </Button>
            </Popconfirm>
          ) : null}
          <Button
            size="small"
            icon={<SyncOutlined />}
            onClick={() => toggleTask(record.id)}
          >
            {record.isEnabled ? '禁用' : '启用'}
          </Button>
          <Popconfirm
            title="确定要删除这个任务吗？"
            onConfirm={() => deleteTask(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer
      header={{
        title: '任务管理',
        subTitle: '管理商品采集任务，支持手动执行和定时调度',
      }}
    >
      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Button 
            type="primary" 
            icon={<PlusOutlined />} 
            onClick={() => {
              form.resetFields();
              setModalVisible(true);
            }}
          >
            新增任务
          </Button>
          <Button icon={<SyncOutlined />} onClick={fetchTasks} loading={loading}>
            刷新
          </Button>
        </Space>
        
        <ProTable<CollectionTask>
          columns={columns}
          dataSource={data}
          loading={loading}
          rowKey="id"
          pagination={false}
          search={false}
          dateFormatter="string"
          onRow={(record) => ({
            onClick: () => setSelectedTaskId(record.id),
            style: { cursor: 'pointer' },
          })}
        />
        
        {selectedTaskId && (
          <LogViewer 
            taskId={selectedTaskId > 0 ? selectedTaskId : undefined} 
            autoScroll={true}
          />
        )}
      </Card>

      {/* 新建任务弹窗 */}
      <Modal
        title="新建采集任务"
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        onOk={() => form.submit()}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            taskType: 'MANUAL',
            maxProducts: 100,
            isEnabled: true,
          }}
        >
          <Form.Item
            name="taskName"
            label="任务名称"
            rules={[{ required: true, message: '请输入任务名称' }]}
          >
            <Input placeholder="请输入任务名称" />
          </Form.Item>
          <Form.Item
            name="taskType"
            label="任务类型"
            rules={[{ required: true, message: '请选择任务类型' }]}
          >
            <Select>
              <Select.Option value="MANUAL">手动任务</Select.Option>
              <Select.Option value="SCHEDULED">定时任务</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="maxProducts"
            label="最大采集数量"
            rules={[{ required: true, message: '请输入最大采集数量' }]}
          >
            <InputNumber min={1} max={1000} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="categoryFilter"
            label="分类过滤"
          >
            <Input placeholder="请输入分类关键词，多个用逗号分隔" />
          </Form.Item>
          <Form.Item
            name="cronExpression"
            label="Cron表达式"
            dependencies={['taskType']}
            rules={({ getFieldValue }) => [
              { required: getFieldValue('taskType') === 'SCHEDULED', message: '定时任务需要设置Cron表达式' },
            ]}
          >
            <Input placeholder="如: 0 0 0 * * ? (每天0点执行)" />
          </Form.Item>
          <Form.Item
            name="isEnabled"
            label="是否启用"
            valuePropName="checked"
          >
            <Select>
              <Select.Option value={true}>启用</Select.Option>
              <Select.Option value={false}>禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default CollectionTasksPage;
