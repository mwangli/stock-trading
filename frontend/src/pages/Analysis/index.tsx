import type {FC} from 'react';
import {Suspense, useState} from 'react';
import {EllipsisOutlined} from '@ant-design/icons';
import {Col, Dropdown, Menu, Row} from 'antd';
import {GridContent} from '@ant-design/pro-layout';
import type {RadioChangeEvent} from 'antd/es/radio';
// @ts-ignore
import type {RangePickerProps} from 'antd/es/date-picker/generatePicker';
import type moment from 'moment';
import IntroduceRow from './components/IntroduceRow';
import type {TimeType} from './components/SalesCard';
import SalesCard from './components/SalesCard';
import TopSearch from './components/TopSearch';
import ProportionSales from './components/ProportionSales';
import {useRequest} from 'umi';
// const actionRef = useRef<ActionType>();
import {fakeChartData} from './service';
import PageLoading from './components/PageLoading';
import {getTimeDistance} from './utils/utils';
import type {AnalysisData} from './data';
import styles from './style.less';
import {PageContainer} from "@ant-design/pro-components";

type RangePickerValue = RangePickerProps<moment.Moment>['value'];

type AnalysisProps = {
  dashboardAndanalysis: AnalysisData;
  loading: boolean;
};

type SalesType = 'all' | 'online' | 'stores';

const Analysis: FC<AnalysisProps> = () => {
  const [salesType, setSalesType] = useState<SalesType>('all');
  const [currentTabKey, setCurrentTabKey] = useState<string>('all');
  const [rangePickerValue, setRangePickerValue] = useState<RangePickerValue>();

  const {loading, data, mutate, refresh} = useRequest(fakeChartData);

  const handleData = async (dateRange: any) => {
    const startDate: any = dateRange?.["0"]?.format('YYYYMMDD');
    const endDate: any = dateRange?.["1"]?.format('YYYYMMDD');
    console.log(startDate);
    console.log(endDate);
    const {data} = await fakeChartData({startDate, endDate,});
    mutate(data)
  }

  // se

  const selectDate = async (type: TimeType) => {
    const dateRange = getTimeDistance(type);
    setRangePickerValue(dateRange);
    await handleData(dateRange);
  };

  const handleRangePickerChange = async (value: RangePickerValue) => {
    setRangePickerValue(value);
    if (value) {
      await handleData(value)
    }
  };

  const isActive = (type: TimeType) => {
    if (!rangePickerValue) {
      return '';
    }
    const value = getTimeDistance(type);
    if (!value) {
      return '';
    }
    if (!rangePickerValue[0] || !rangePickerValue[1]) {
      return '';
    }
    if (
      rangePickerValue[0].isSame(value[0] as moment.Moment, 'day') &&
      rangePickerValue[1].isSame(value[1] as moment.Moment, 'day')
    ) {
      return styles.currentDate;
    }
    return '';
  };

  let salesPieData;
  if (salesType === 'all') {
    salesPieData = data?.salesTypeData;
  } else {
    salesPieData = salesType === 'online' ? data?.salesTypeDataOnline : data?.salesTypeDataOffline;
  }

  const menu = (
    <Menu>
      <Menu.Item>操作一</Menu.Item>
      <Menu.Item>操作二</Menu.Item>
    </Menu>
  );

  const dropdownGroup = (
    <span className={styles.iconGroup}>
      <Dropdown overlay={menu} placement="bottomRight">
        <EllipsisOutlined/>
      </Dropdown>
    </span>
  );

  const handleChangeSalesType = (e: RadioChangeEvent) => {
    setSalesType(e.target.value);
  };

  const handleTabChange = (key: string) => {
    setCurrentTabKey(key);
  };

  // const activeKey = currentTabKey || (data?.offlineData[0] && data?.offlineData[0].name) || '';
  const activeKey = '';
  return (
    <PageContainer>
      <GridContent>

        {/*第一排四个小图*/}
        <Suspense fallback={<PageLoading/>}>
          <IntroduceRow loading={loading} visitData={data || {}}/>
        </Suspense>

        {/*中间的柱状图*/}
        <Suspense fallback={<PageLoading/>}>
          <SalesCard
            rangePickerValue={rangePickerValue}
            salesData={data?.incomeList || []}
            salesData2={data?.dailyRateList || []}
            incomeOrder={data?.incomeOrder || []}
            rateOrder={data?.dailyRateOrder || []}
            isActive={isActive}
            handleRangePickerChange={handleRangePickerChange}
            loading={loading}
            selectDate={selectDate}
          />
        </Suspense>

        {/*下面的统计饼图*/}
        <Row
          gutter={24}
          style={{
            marginTop: 24,
          }}
        >
          <Col xl={12} lg={24} md={24} sm={24} xs={24}>
            <Suspense fallback={<PageLoading/>}>
              <TopSearch
                loading={loading}
                searchData={data?.expectList || []}
                dropdownGroup={dropdownGroup}
              />
            </Suspense>
          </Col>
          <Col xl={12} lg={24} md={24} sm={24} xs={24}>
            <Suspense fallback={<PageLoading/>}>
              <ProportionSales
                dropdownGroup={dropdownGroup}
                salesType={salesType}
                loading={loading}
                salesPieData={data?.holdDaysList || []}
                handleChangeSalesType={handleChangeSalesType}
              />
            </Suspense>
          </Col>
        </Row>

        {/*<Suspense fallback={null}>*/}
        {/*  <OfflineData*/}
        {/*    activeKey={activeKey}*/}
        {/*    loading={loading}*/}
        {/*    offlineData={data?.offlineData || []}*/}
        {/*    offlineChartData={data?.offlineChartData || []}*/}
        {/*    handleTabChange={handleTabChange}*/}
        {/*  />*/}
        {/*</Suspense>*/}

      </GridContent>
    </PageContainer>
  );
};

export default Analysis;
