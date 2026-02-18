import {listOrderInfo} from '@/services/ant-design-pro/api';
import type {ActionType, ProColumns, ProDescriptionsItemProps} from '@ant-design/pro-components';
import {
  FooterToolbar,
  ModalForm,
  PageContainer,
  ProDescriptions,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import {FormattedMessage, useIntl} from '@umijs/max';
import {Button, Drawer, Modal} from 'antd';
import React, {useRef, useState} from 'react';
import {Area} from "@ant-design/charts";

const TableList: React.FC = () => {

  const [createModalOpen, handleModalOpen] = useState<boolean>(false);

  const [showDetail, setShowDetail] = useState<boolean>(false);

  const [modalOpen, setModalOpen] = useState<boolean>(false);
  const [modalOpen2, setModalOpen2] = useState<boolean>(false);

  const actionRef = useRef<ActionType>();
  const [currentRow, setCurrentRow] = useState<API.RuleListItem>();
  const [selectedRowsState, setSelectedRows] = useState<API.RuleListItem[]>([]);

  /**
   * @en-US International configuration
   * @zh-CN 国际化配置
   * */
  const intl = useIntl();

  const columns: ProColumns[] = [
    {
      title: '股票代码',
      dataIndex: 'code',
      valueType: 'textarea',
      sorter: true,
    },
    {
      title: '股票名称',
      dataIndex: 'name',
      valueType: 'textarea',
      sorter: true,
    },

    {
      title: '订单类型',
      dataIndex: 'type',
      valueType: 'textarea',
      hideInSearch: true,
    },
    {
      title: '订单编号',
      dataIndex: 'answerNo',
      valueType: 'textarea',
      sorter: true,
    },
    {
      title: '订单日期',
      dataIndex: 'date',
      valueType: 'textarea',
      hideInSearch: true,
      sorter: true,
    },
    {
      title: '订单数量',
      dataIndex: 'number',
      valueType: 'textarea',
      hideInSearch: true,
      sorter: true,
    },
    {
      title: '订单价格',
      dataIndex: 'price',
      valueType: 'textarea',
      hideInSearch: true,
      sorter: true,
      renderText: (val: string) =>
        val ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.yuan',
          defaultMessage: ' 元 ',
        })}` : '-',
    },
    {
      title: '订单金额',
      dataIndex: 'amount',
      valueType: 'textarea',
      hideInSearch: true,
      sorter: true,
      renderText: (val: string) =>
        val ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.yuan',
          defaultMessage: ' 元 ',
        })}` : '-',
    },
    {
      title: '手续费',
      dataIndex: 'peer',
      valueType: 'textarea',
      hideInSearch: true,
      sorter: true,
      renderText: (val: string) =>
        val ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.yuan',
          defaultMessage: ' 元 ',
        })}` : '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      hideInSearch: true,
      sorter: true,
    },
    {
      title: '订单状态',
      dataIndex: 'status',
      hideInSearch: true,
      sorter: true,
      valueEnum: {
        1: {
          text: '已成交',
          status: 'Success',
        },
        0: {
          text: '未成交',
          status: 'Error',
        },
      },
    },
  ];

  return (
    <PageContainer>
      <ProTable
        headerTitle={intl.formatMessage({
          id: 'pages.searchTable.orderInfoList',
          defaultMessage: '订单列表',
        })}
        actionRef={actionRef}
        rowKey="key"
        search={{
          labelWidth: 120,
        }}
        request={listOrderInfo}
        columns={columns}
      />
    </PageContainer>
  );
};

export default TableList;
