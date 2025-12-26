import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider, Layout, Menu } from 'antd';
import { HomeOutlined, UnorderedListOutlined } from '@ant-design/icons';
import zhCN from 'antd/locale/zh_CN';
import { useNavigate, useLocation } from 'react-router-dom';
import HomePage from './pages/HomePage';
import TaskListPage from './pages/TaskListPage';
import './App.css';

const { Header, Content } = Layout;

const AppLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    {
      key: '/',
      icon: <HomeOutlined />,
      label: '首页',
    },
    {
      key: '/tasks',
      icon: <UnorderedListOutlined />,
      label: '任务列表',
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', background: '#fff', borderBottom: '1px solid #f0f0f0' }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', marginRight: '32px' }}>
          发票识别与裁切系统
        </div>
        <Menu
          mode="horizontal"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ flex: 1, borderBottom: 'none' }}
        />
      </Header>
      <Content style={{ padding: '24px', background: '#f5f5f5' }}>
        {children}
      </Content>
    </Layout>
  );
};

const App: React.FC = () => {
  return (
    <ConfigProvider locale={zhCN}>
      <div className="app-container">
        <BrowserRouter>
          <AppLayout>
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/tasks" element={<TaskListPage />} />
            </Routes>
          </AppLayout>
        </BrowserRouter>
      </div>
    </ConfigProvider>
  );
};

export default App;

