import {cancelStockInfo, listHistoryPrices, listStockInfo, selectStockInfo} from '@/services/ant-design-pro/api';
import type {ActionType, ProColumns} from '@ant-design/pro-components';
import {PageContainer, ProTable,} from '@ant-design/pro-components';
import {FormattedMessage, useIntl} from '@umijs/max';
import {Input, Modal} from 'antd';
import React, {useRef, useState} from 'react';
import numeral from "numeral";
import {Area} from "@ant-design/charts";

const TableList: React.FC = () => {

  const [modalOpen, setModalOpen] = useState<boolean>(false);

  const actionRef = useRef<ActionType>();
  const [currentRow, setCurrentRow] = useState<API.RuleListItem>();

  /**
   * @en-US International configuration
   * @zh-CN 国际化配置
   * */
  const intl = useIntl();

  const columns: ProColumns<API.RuleListItem>[] = [
    {
      title: '股票代码',
      dataIndex: 'code',
    },

    {
      title: <FormattedMessage id="pages.searchTable.foundName" defaultMessage="Description"/>,
      dataIndex: 'name',
      valueType: 'textarea',
    },
    {
      title: <FormattedMessage id="pages.searchTable.market" defaultMessage="market"/>,
      dataIndex: 'market',
      valueType: 'textarea',
      hideInSearch: true,
    },
    {
      title: <FormattedMessage id="pages.searchTable.nowPrice" defaultMessage="Description"/>,
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
      title: <FormattedMessage id="pages.searchTable.increase" defaultMessage="Description"/>,
      dataIndex: 'increase',
      valueType: 'textarea',
      hideInSearch: true,
      sorter: true,
      renderText: (val: string) =>
        val != null ? `${val} %` : '-',
    },
    {
      title: <FormattedMessage id="pages.searchTable.buySaleCount" defaultMessage="交易次数"/>,
      dataIndex: 'buySaleCount',
      hideInSearch: true,
      valueType: 'textarea',
      sorter: true,
      renderText: (val: string) =>
        val != null ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.count',
          defaultMessage: ' 次 ',
        })}` : '-',
    },
    {
      title: <FormattedMessage id="pages.searchTable.score" defaultMessage="score"/>,
      dataIndex: 'score',
      sorter: true,
      hideInForm: true,
      hideInSearch: true,
      renderText: (val: string) =>
        val != null ? `${numeral(val).format('0.00')} 分`
          : '-',
    },
    {
      title: <FormattedMessage id="pages.searchTable.createTime" defaultMessage="Description"/>,
      dataIndex: 'createTime',
      valueType: 'dateTime',
      hideInSearch: true,
      hideInTable: true,
      sorter: true,
    },
    {
      title: (
        <FormattedMessage id="pages.searchTable.updateTime" defaultMessage="Last updateTime"/>
      ),
      sorter: true,
      dataIndex: 'updateTime',
      valueType: 'dateTime',
      hideInSearch: true,
      renderFormItem: (item, {defaultRender, ...rest}, form) => {
        const status = form.getFieldValue('status');
        if (`${status}` === '0') {
          return false;
        }
        if (`${status}` === '3') {
          return (
            <Input
              {...rest}
              placeholder={intl.formatMessage({
                id: 'pages.searchTable.exception',
                defaultMessage: 'Please enter the reason for the exception!',
              })}
            />
          );
        }
        return defaultRender(item);
      },
    },
    {
      title: '自选股票',
      dataIndex: 'selected',
      sorter: true,
      valueEnum: {
        "1": {
          text: '选中股票',
        },
        "0": {
          text: '未选股票',
        },
      },
    },
    {
      title: <FormattedMessage id="pages.searchTable.permission" defaultMessage="交易权限"/>,
      dataIndex: 'permission',
      sorter: true,
      valueEnum: {
        1: {
          text: <FormattedMessage id="pages.searchTable.permission.allow" defaultMessage="sold"/>,
          status: 'Processing',
        },
        0: {
          text: (
            <FormattedMessage id="pages.searchTable.permission.forbidden" defaultMessage="notSold"/>
          ),
          status: 'Error',
        },
      },
    },
    {
      title: '最低价格',
      dataIndex: 'priceLow',
      valueType: 'digit',
      hideInTable: true,
    },
    {
      title: '最高价格',
      dataIndex: 'priceHigh',
      valueType: 'digit',
      hideInTable: true,
    },
    // {
    //   title: '最高价格',
    //   dataIndex: 'priceRange',
    //   valueType: 'digitRange',
    //   hideInTable: true,
    // },
    {
      title: '相关操作',
      dataIndex: 'option',
      valueType: 'option',
      render: (_, record) => [
        <a
          key="listHistoryPrices"
          onClick={() => {
            listHistoryPrices({code: record.code}).then(res => {
              record.pricesList = res?.data?.points
              record.maxPrice = res?.data?.maxValue
              record.minPrice = res?.data?.minValue
              setCurrentRow(record);
              setModalOpen(true);
            })
          }}
        >
          历史价格
        </a>,
        <a
          key="k2"
          onClick={async (_) => {
            setCurrentRow(record);
            if (record.selected == "1") {
              await cancelStockInfo(record)
            } else {
              await selectStockInfo(record)
            }
            actionRef?.current?.reload()
          }}
        >{
          record.selected == "1" ? "取消自选" : "添加自选"
        }
        </a>,
      ],
    },
  ];


  return (
    <PageContainer>
      <ProTable<API.RuleListItem, API.PageParams>
        headerTitle='股票信息'
        actionRef={actionRef}
        rowKey="key"
        search={{
          labelWidth: 120,
        }}
        request={listStockInfo}
        columns={columns}
      />

      <Modal
        width={1200}
        bodyStyle={{padding: '32px 40px 48px'}}
        destroyOnClose
        title='历史价格'
        open={modalOpen}
        footer={null}
        onCancel={() => {
          setModalOpen(false);
        }}
      >

        <Area
          smooth
          height={420}
          data={currentRow?.pricesList || []}
          xField="x"
          yField="y"
          meta={{
            x: {
              alias: '交易日期',
            },
            y: {
              alias: '开盘价格(元)',
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
