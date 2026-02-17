import {InfoCircleOutlined} from '@ant-design/icons';
import {Card, Col, Row, Table, Tooltip} from 'antd';
import {TinyArea} from '@ant-design/charts';
import React from 'react';
import numeral from 'numeral';
import type {DataItem} from '../data';

import NumberInfo from './NumberInfo';
import Trend from './Trend';
import styles from '../style.less';
import {FormattedMessage} from "@umijs/max";

const columns = [
  {
    title: '排名',
    dataIndex: 'index',
    key: 'index',
    render: (text: React.ReactNode, record: {
      dailyIncomeRate: number;
      status: number
    }, index: number) => (
      // <Trend flag={record.dailyIncomeRate > 0 ? 'up' : 'down'}>
        <span style={{marginRight: 4}}>{index + 1}</span>
       // </Trend>
    ),
  },
  {
    title: '股票信息',
    dataIndex: 'name',
    key: 'name',
    render: (text: string, record : {code: string, name :string}) => <a href={`/list?code=${record.code}`}>{record.code.concat('-').concat(record.name)}</a>,
  },
  {
    title: '买入价格',
    dataIndex: 'buyPrice',
    key: 'buyPrice',
    render: (text: React.ReactNode, record: { status: number }) => (
      `${numeral(text).format('0.00')}元`
    ),
  },
  {
    title: '卖出价格',
    dataIndex: 'salePrice',
    key: 'predictPrice',
    className: styles.alignRight,
    render: (text: React.ReactNode, record: { status: number }) => (
      `${numeral(text).format('0.00')}元`
    ),
  },
  {
    title: '收益金额',
    dataIndex: 'income',
    key: 'income',
    className: styles.alignRight,
    render: (text: React.ReactNode) => (
      `${numeral(text).format('0.00')}元`
    ),
  },
  {
    title: '日收益率',
    dataIndex: 'dailyIncomeRate',
    key: 'dailyIncomeRate',
    render: (text: React.ReactNode) => (
      `${numeral(text).format('0.0000')}%`
    ),
  },
  {
    title: '卖出时间',
    dataIndex: 'saleDateString',
    key: 'saleDateString',
    className: styles.alignRight,
  },
];

const TopSearch = ({
                     loading,
                     searchData,
                     dropdownGroup,
                   }: {
  loading: boolean;
  dropdownGroup: React.ReactNode;
  searchData: DataItem[];
}) => (
  <Card
    loading={loading}
    bordered={false}
    title="日收益率排行"
    extra={dropdownGroup}
    style={{
      height: '100%',
    }}
  >
    {/*<Row gutter={68}>*/}
      {/*<Col sm={24} xs={24} style={{ marginBottom: 24 }}>*/}
      {/*  <NumberInfo*/}
      {/*    // subTitle={*/}
      {/*    //   <span>*/}
      {/*    //     日收益率趋势*/}
      {/*    //     /!*<Tooltip title="日收益率趋势">*!/*/}
      {/*    //     /!*  <InfoCircleOutlined style={{ marginLeft: 8 }} />*!/*/}
      {/*    //     /!*</Tooltip>*!/*/}
      {/*    //   </span>*/}
      {/*    // }*/}
      {/*    // gap={8}*/}
      {/*    total={`${numeral(dailyIncomeRate).format('0.0000')}%`}*/}
      {/*    // status="up"*/}
      {/*    // subTotal={17.1}*/}
      {/*  />*/}
      {/*  /!*<TinyArea  height={108}  smooth data={visitData2?.map((i:any) => {return i.y})} color={''} />*!/*/}
      {/*</Col>*/}
      {/*<Col sm={12} xs={24} style={{ marginBottom: 24 }}>*/}
      {/*  <NumberInfo*/}
      {/*    subTitle={*/}
      {/*      <span>*/}
      {/*        日收益率趋势*/}
      {/*        <Tooltip title="日收益率趋势">*/}
      {/*          <InfoCircleOutlined style={{ marginLeft: 8 }} />*/}
      {/*        </Tooltip>*/}
      {/*      </span>*/}
      {/*    }*/}
      {/*    total={2.7}*/}
      {/*    status="down"*/}
      {/*    subTotal={26.2}*/}
      {/*    gap={8}*/}
      {/*  />*/}
      {/*  <TinyArea xField="x" height={45} forceFit yField="y" smooth data={visitData2} />*/}
      {/*</Col>*/}
    {/*</Row>*/}
    <Table<any>
      rowKey={(record) => record.index}
      size="small"
      columns={columns}
      dataSource={searchData}
      pagination={false}
      // pagination={{
      //   style: { marginBottom: 0 },
      //   pageSize: 5,
      // }}
    />
  </Card>
);

export default TopSearch;
