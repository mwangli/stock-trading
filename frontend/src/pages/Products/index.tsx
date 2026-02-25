import React, { useState } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Card, Tag, Button, Space, Image, message } from 'antd';
import { ReloadOutlined, CloudDownloadOutlined } from '@ant-design/icons';
// @ts-ignore - request is a global plugin-injected object
import { request } from 'D:/AI-Shopping/frontend/src/.umi/plugin-request';

interface Product {
  id: number;
  tsin: string;
  productTitle: string;
  mainCategory: string;
  brand: string;
  price: number;
  rating: number | null;
  collectedAt: string;
  imageUrls: string;
  isFiltered: boolean;
  filterReason?: string;
}

const ProductsPage: React.FC = () => {
  const [collecting, setCollecting] = useState(false);
  const [loading, setLoading] = useState(false);

  // 立即采集商品数据
  const handleCollect = async () => {
    setCollecting(true);
    try {
      const response = await request('/api/collection-tasks/quick-start?maxProducts=100', {
        method: 'POST',
      });
      
      if (response.success) {
        message.success('商品采集任务已启动，请稍后刷新数据');
      } else {
        message.error(response.message || '启动采集失败');
      }
    } catch (error) {
      console.error('Error collecting products:', error);
      message.error('启动采集失败，请检查后端服务');
    } finally {
      setCollecting(false);
    }
  };

  // 加载商品数据
  const loadData = async (params: any) => {
    setLoading(true);
    try {
      const response = await request('/api/products', {
        method: 'GET',
        params: {
          page: params.current || 1,
          size: params.pageSize || 10,
        },
      });
      
      if (response.success) {
        return {
          data: response.data.content || [],
          success: true,
          total: response.data.totalElements || 0,
        };
      }
      return {
        data: [],
        success: false,
        total: 0,
      };
    } catch (error) {
      console.error('Error fetching products:', error);
      message.error('获取商品数据失败，请检查后端服务是否正常运行');
      return {
        data: [],
        success: false,
        total: 0,
      };
    } finally {
      setLoading(false);
    }
  };

  // 刷新数据
  const handleRefresh = () => {
    window.location.reload();
  };

  const columns = [
    {
      title: '商品图片',
      dataIndex: 'imageUrls',
      key: 'imageUrls',
      width: 100,
      render: (_: React.ReactNode, record: Product) => {
        try {
          const urls = JSON.parse(record.imageUrls || '[]');
          return urls.length > 0 ? (
            <Image
              src={urls[0]}
              alt="商品图片"
              width={60}
              height={60}
              style={{ objectFit: 'cover' }}
              fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mN8/+F9PQAI8wNPvd7POQAAAABJRU5ErkJggg=="
            />
          ) : (
            <div style={{ width: 60, height: 60, background: '#f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              无图
            </div>
          );
        } catch {
          return <div style={{ width: 60, height: 60, background: '#f0f0f0' }}>无图</div>;
        }
      },
    },
    {
      title: 'TSIN',
      dataIndex: 'tsin',
      key: 'tsin',
      width: 120,
    },
    {
      title: '商品标题',
      dataIndex: 'productTitle',
      key: 'productTitle',
      ellipsis: true,
    },
    {
      title: '分类',
      dataIndex: 'mainCategory',
      key: 'mainCategory',
      width: 120,
      render: (_: any, record: Product) => <Tag color="blue">{record.mainCategory}</Tag>,
    },
    {
      title: '品牌',
      dataIndex: 'brand',
      key: 'brand',
      width: 120,
    },
    {
      title: '价格',
      dataIndex: 'price',
      key: 'price',
      width: 120,
      render: (_: any, record: Product) => `R ${(record.price || 0).toFixed(2)}`,
    },
    {
      title: '评分',
      dataIndex: 'rating',
      key: 'rating',
      width: 100,
      render: (_: any, record: Product) => {
        const rating = record.rating;
        if (rating === null || rating === undefined) {
          return <Tag color="default">无评分</Tag>;
        }
        return (
          <Tag color={rating >= 4.5 ? 'green' : rating >= 4.0 ? 'blue' : rating >= 3.0 ? 'orange' : 'red'}>
            {rating.toFixed(1)} ⭐
          </Tag>
        );
      },
    },
    {
      title: '采集时间',
      dataIndex: 'collectedAt',
      key: 'collectedAt',
      width: 180,
      render: (_: any, record: Product) => record.collectedAt ? new Date(record.collectedAt).toLocaleString('zh-CN') : '-',
    },
    {
      title: '状态',
      dataIndex: 'isFiltered',
      key: 'isFiltered',
      width: 120,
      render: (_: any, record: Product) => {
        if (record.isFiltered) {
          return <Tag color="red">已过滤: {record.filterReason}</Tag>;
        }
        return <Tag color="green">正常</Tag>;
      },
    },
  ];

  return (
    <PageContainer
      header={{
        title: '商品采集列表',
        subTitle: '展示从Takealot采集的商品数据',
      }}
    >
        <Card>
          <Space style={{ marginBottom: 16 }}>
            <Button 
              type="primary" 
              icon={<CloudDownloadOutlined />} 
              onClick={handleCollect}
              loading={collecting}
            >
              立即采集
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleRefresh} loading={loading}>
              刷新数据
            </Button>
          </Space>
        
        <ProTable<Product>
          columns={columns}
          rowKey="id"
          request={loadData}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
          }}
          search={false}
          toolBarRender={() => [
            <Button 
              key="collect"
              type="primary" 
              icon={<CloudDownloadOutlined />} 
              onClick={handleCollect}
              loading={collecting}
            >
              立即采集
            </Button>,
          ]}
        />
      </Card>
    </PageContainer>
  );
};

export default ProductsPage;
