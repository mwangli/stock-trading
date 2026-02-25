import React from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Card, Alert, Typography } from 'antd';

const { Title, Paragraph } = Typography;

const HomePage: React.FC = () => {
  return (
    <PageContainer ghost>
      <Card>
        <Alert
          message="欢迎使用 AI Shopping 管理系统"
          type="success"
          showIcon
          banner
          style={{
            margin: -12,
            marginBottom: 48,
          }}
        />
        <Typography>
          <Title level={2}>AI Shopping 智能购物系统</Title>
          <Paragraph>
            这是一个基于 Spring Boot 3.0 + React + Ant Design Pro 构建的全栈应用。
          </Paragraph>
          <Paragraph>
            <ul>
              <li>后端：Spring Boot 3.0 + JDK 17</li>
              <li>前端：React 18 + Ant Design Pro 5</li>
              <li>数据库：H2 (开发环境)</li>
            </ul>
          </Paragraph>
        </Typography>
      </Card>
    </PageContainer>
  );
};

export default HomePage;
