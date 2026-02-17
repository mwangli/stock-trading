import {
  chooseStrategy,
  createStrategy,
  deleteStrategy,
  listStrategy,
  listTestData, listValidateData,
  modifyStrategy
} from '@/services/ant-design-pro/api';
import type {ActionType, ProColumns} from '@ant-design/pro-components';
import {PageContainer, ProTable,} from '@ant-design/pro-components';
import {FormattedMessage, useIntl} from '@umijs/max';
import {Button, message, Modal} from 'antd';
import React, {useRef, useState} from 'react';
import {PlusOutlined} from "@ant-design/icons";
import {Line} from "@ant-design/plots";
import numeral from "numeral";

const TableList: React.FC = () => {

  const actionRef = useRef<ActionType>();
  const [currentRow, setCurrentRow] = useState<API.RuleListItem>();

  const [modalOpen, setModalOpen] = useState<boolean>(false);
  const [modalOpen2, setModalOpen2] = useState<boolean>(false);


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
      title: '参数量级',
      dataIndex: 'paramsSize',
      valueType: 'text',
      sorter: true,
      hideInTable: true,
      hideInSearch: true,
    },
    {
      title: '训练次数',
      sorter: true,
      dataIndex: 'trainTimes',
      valueType: 'text',
      hideInSearch: true,
    },
    {
      title: '训练时长',
      dataIndex: 'trainPeriod',
      valueType: 'text',
      hideInSearch: true,
      sorter: true,
    },
    {
      title: '测试误差',
      dataIndex: 'testDeviation',
      valueType: 'text',
      hideInSearch: true,
      sorter: true,
      renderText: (val: number) =>
        val != null ? `${numeral(val).format('0.0000')}`
          : '-',
    },
    {
      title: '模型评分',
      dataIndex: 'score',
      valueType: 'text',
      hideInSearch: true,
      sorter: true,
      renderText: (val: string) =>
        val != null ? `${numeral(val).format('0.00')} 分`
          : '-',
    },
    {
      title: '训练时间',
      sorter: true,
      dataIndex: 'updateTime',
      valueType: 'dateTime',
      hideInSearch: true,
    },
    {
      title: '模型状态',
      dataIndex: 'status',
      hideInForm: true,
      // hideInTable: true,
      sorter: true,
      valueEnum: {
        0: {
          text: '训练中',
          status: 'Processing',
        },
        1: {
          text: '已训练',
          status: 'Success',
        },
        2: {
          text: '已废弃',
          status: 'Error',
        },
      },
    },
    {
      title: '预测结果',
      dataIndex: 'option',
      valueType: 'option',
      render: (_, record) => [
        <a
          key="k2"
          onClick={async (_) => {
            listTestData({code: record.code}).then(res => {
              record.pricesList = res?.data?.points
              record.maxPrice = res?.data?.maxValue
              record.minPrice = res?.data?.minValue
              setCurrentRow(record);
              setModalOpen(true);
            })
          }
          }
        >
          测试数据
        </a>,

        // <a
        //   key="k3"
        //   onClick={async (_) => {
        //     listValidateData({code: record.code}).then(res => {
        //       record.pricesList = res?.data?.points
        //       record.maxPrice = res?.data?.maxValue
        //       record.minPrice = res?.data?.minValue
        //       setCurrentRow(record);
        //       setModalOpen(true);
        //     })
        //   }
        //   }
        // >
        //   验证集
        // </a>,
      ],
    }
  ];

  return (
    <PageContainer>
      <ProTable
        headerTitle={'模型列表'}
        actionRef={actionRef}
        rowKey="key"
        search={{
          labelWidth: 120,
        }}
        request={listStrategy}
        columns={columns}

      />

      <Modal
        width={1200}
        bodyStyle={{padding: '32px 40px 48px'}}
        destroyOnClose
        title='预测结果'
        open={modalOpen}
        footer={null}
        onCancel={() => {
          setModalOpen(false);
        }}
      >
        <Line
          data={currentRow?.pricesList || []}
          xField='x'
          yField='y'
          seriesField='type'
          meta={{
            x: {
              alias: '交易日期',
            },
            y: {
              alias: '日增长率',
              max: currentRow?.maxPrice,
              min: currentRow?.minPrice,
            },
          }}
        />
      </Modal>

      <Modal
        width={1200}
        bodyStyle={{padding: '32px 40px 48px'}}
        destroyOnClose
        title='日增长率'
        open={modalOpen2}
        footer={null}
        onCancel={() => {
          setModalOpen2(false);
        }}
      >
        <Line
          data={currentRow?.increaseRateList || []}
          xField='x'
          yField='y'
          seriesField='type'
          meta={{
            x: {
              alias: '交易日期',
            },
            y: {
              alias: '开盘价格',
              max: currentRow?.maxPrice,
              min: currentRow?.minPrice,
            },
          }}
        />
      </Modal>
    </PageContainer>
  );
};

export default TableList;
