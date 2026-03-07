import {listFoundTrading} from '@/services/ant-design-pro/api';
import type {ActionType, ProColumns, ProDescriptionsItemProps} from '@ant-design/pro-components';
import {PageContainer, ProDescriptions, ProTable,} from '@ant-design/pro-components';
import {FormattedMessage, useIntl} from '@umijs/max';
import {Drawer, Input} from 'antd';
import React, {useRef, useState} from 'react';
import {history} from "umi";
import numeral from "numeral";


const TableList: React.FC = () => {


  const [showDetail, setShowDetail] = useState<boolean>(false);


  const actionRef = useRef<ActionType>();
  const [currentRow, setCurrentRow] = useState<API.RuleListItem>();

  const intl = useIntl();

  const code: string = history.location.search.split("code=")[1]
  console.log(code)

  const columns: ProColumns<API.RuleListItem>[] = [
    {
      title: (
        <FormattedMessage
          id="pages.searchTable.updateForm.ruleName.nameLabel"
          defaultMessage="Rule name"
        />
      ),
      dataIndex: 'code',
      valueType: 'text',
      initialValue: code,
      // tip: 'The stock code is the unique key',
      render: (dom, entity) => {
        return (
          <a
            onClick={() => {
              setCurrentRow(entity);
              setShowDetail(true);
            }}
          >
            {dom}
          </a>
        );
      },
    },

    {
      title: <FormattedMessage id="pages.searchTable.foundName" defaultMessage="Description"/>,
      dataIndex: 'name',
      valueType: 'textarea',
    },
    {
      title: <FormattedMessage id="pages.searchTable.buyDate" defaultMessage="Description"/>,
      dataIndex: 'buyDate',
      valueType: 'date',
      sorter: true,
    },
    {
      title: <FormattedMessage id="pages.searchTable.buyPrice" defaultMessage="Description"/>,
      dataIndex: 'buyPrice',
      valueType: 'textarea',
      hideInTable: true,
      hideInSearch: true,
      sorter: true,
      renderText: (val: string) =>
        val != null ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.yuan',
          defaultMessage: ' 元 ',
        })}` : '-',
    },
    {
      title: <FormattedMessage id="pages.searchTable.buyNumber" defaultMessage="Description"/>,
      dataIndex: 'buyNumber',
      valueType: 'textarea',
      hideInTable: true,
      hideInSearch: true,
      sorter: true,
      renderText: (val: string) =>
        val != null ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.piece',
          defaultMessage: ' 股 ',
        })}` : '-',
    },
    {
      title: (
        <FormattedMessage
          id="pages.searchTable.buyAmount"
          defaultMessage="Number of service calls"
        />
      ),
      dataIndex: 'buyAmount',
      sorter: true,
      hideInForm: true,
      hideInSearch: true,
      renderText: (val: string) =>
        `${val}${intl.formatMessage({
          id: 'pages.searchTable.yuan',
          defaultMessage: ' 元 ',
        })}`,
    },
    {
      title: <FormattedMessage id="pages.searchTable.saleDate" defaultMessage="Description"/>,
      dataIndex: 'saleDate',
      valueType: 'date',
      sorter: true,
    },
    {
      title: <FormattedMessage id="pages.searchTable.salePrice" defaultMessage="Description"/>,
      dataIndex: 'salePrice',
      valueType: 'textarea',
      hideInTable: true,
      hideInSearch: true,
      sorter: true,
      renderText: (val: string) =>
        val != null ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.yuan',
          defaultMessage: ' 元 ',
        })}` : '-',
    },
    {
      title: <FormattedMessage id="pages.searchTable.saleNumber" defaultMessage="Description"/>,
      dataIndex: 'saleNumber',
      valueType: 'textarea',
      hideInTable: true,
      hideInSearch: true,
      renderText: (val: string) =>
        val != null ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.piece',
          defaultMessage: ' 元 ',
        })}` : '-',
    },
    {
      title: <FormattedMessage id="pages.searchTable.saleAmount" defaultMessage="saleAmount"/>,
      dataIndex: 'saleAmount',
      sorter: true,
      hideInForm: true,
      hideInSearch: true,
      renderText: (val: string) =>
        val != null ? `${val}${intl.formatMessage({
            id: 'pages.searchTable.yuan',
            defaultMessage: ' 元 ',
          })}`
          : '-',
    },
    {
      title: <FormattedMessage id="pages.searchTable.income" defaultMessage="income"/>,
      dataIndex: 'income',
      sortDirections: [],
      hideInForm: true,
      hideInSearch: true,
      sorter: true,
      renderText: (val: string) =>
        val != null ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.yuan',
          defaultMessage: ' 元 ',
        })}` : '-',
    },
    {
      title: '收益率',
      dataIndex: 'incomeRate',
      hideInSearch: true,
      hideInTable: true,
      renderText: (val: string) =>
        val != null ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.percent',
          defaultMessage: ' % ',
        })}` : '-',
    },
    {
      title: <FormattedMessage id="pages.searchTable.holdDays" defaultMessage="Description"/>,
      dataIndex: 'holdDays',
      valueType: 'textarea',
      sorter: true,
      renderText: (val: string) =>
        val != null ? `${val}${intl.formatMessage({
          id: 'pages.searchTable.day',
          defaultMessage: ' 天 ',
        })}` : '-',
    },
    {
      title: (
        <FormattedMessage id="pages.searchTable.dailyIncomeRate" defaultMessage="dailyIncomeRate"/>
      ),
      dataIndex: 'dailyIncomeRate',
      sorter: true,
      hideInSearch: true,
      hideInForm: true,
        renderText: (val: string) =>
          val != null ? `${numeral(val).format('0.0000')}${intl.formatMessage({
          id: 'pages.searchTable.percent',
          defaultMessage: ' % ',
        })}` : '-',
    },
    {
      title: <FormattedMessage id="pages.searchTable.createTime" defaultMessage="Description"/>,
      dataIndex: 'createTime',
      valueType: 'dateTime',
      hideInTable: true,
      hideInSearch: true,
      sorter: true,
    },
    {
      title: '更新时间',
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
      title: '出售状态',
      dataIndex: 'sold',
      hideInForm: true,
      order: 1,
      sorter: true,
      valueEnum: {
        1: {
          text: <FormattedMessage id="pages.searchTable.titleStatus.sold" defaultMessage="sold"/>,
          status: 'Success',
        },
        0: {
          text: (
            <FormattedMessage id="pages.searchTable.titleStatus.notSold" defaultMessage="notSold"/>
          ),
          status: 'Processing',
        },
      },
    },
  ];


  return (
    <PageContainer>
      <ProTable<API.RuleListItem, API.PageParams>
        headerTitle={intl.formatMessage({
          id: 'pages.searchTable.title',
          defaultMessage: 'Enquiry form',
        })}
        actionRef={actionRef}
        rowKey="key"
        search={{
          labelWidth: 120,
        }}
        request={listFoundTrading}
        columns={columns}
      />

      <Drawer
        width={600}
        open={showDetail}
        onClose={() => {
          setCurrentRow(undefined);
          setShowDetail(false);
        }}
        closable={false}
      >
        {currentRow?.name && (
          <ProDescriptions<API.RuleListItem>
            column={2}
            title={currentRow?.name}
            request={async () => ({
              data: currentRow || {},
            })}
            params={{
              id: currentRow?.name,
            }}
            columns={columns as ProDescriptionsItemProps<API.RuleListItem>[]}
          />
        )}
      </Drawer>
    </PageContainer>
  );
};

export default TableList;
