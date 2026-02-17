import {Card, Col, DatePicker, Row, Tabs} from 'antd';
import type moment from 'moment';
import {Area, Column} from '@ant-design/charts';

import numeral from 'numeral';
import type {DataItem} from '../data';
import styles from '../style.less';
import {RangePickerProps} from "antd/lib/date-picker/generatePicker/interface";

type RangePickerValue = RangePickerProps<moment.Moment>['value'];
export type TimeType = 'today' | 'week' | 'month' | 'year';

const {RangePicker} = DatePicker;
const {TabPane} = Tabs;

const rankingListData: { title: string; total: number }[] = [];
for (let i = 0; i < 7; i += 1) {
  rankingListData.push({
    title: `工专路 ${i} 号店`,
    total: 323234,
  });
}

// @ts-ignore
const SalesCard = ({
                     rangePickerValue,
                     salesData,
                     salesData2,
                     incomeOrder,
                     rateOrder,
                     isActive,
                     handleRangePickerChange,
                     loading,
                     selectDate,
                   }: {
  rangePickerValue: RangePickerValue;
  isActive: (key: any) => string;
  salesData: DataItem[];
  salesData2: DataItem[];
  incomeOrder: DataItem[];
  rateOrder: DataItem[];
  loading: boolean;
  handleRangePickerChange: (dates: RangePickerValue, dateStrings: [string, string]) => void;
  selectDate: (key: any) => void;
}) => (
  <Card loading={loading} bordered={false} bodyStyle={{padding: 0}}>
    <div className={styles.salesCard}>
      <Tabs
        tabBarExtraContent={
          <div className={styles.salesExtraWrap}>
            <div className={styles.salesExtra}>
              <a className={isActive('today')} onClick={() => selectDate('today')}>
                今日
              </a>
              <a className={isActive('week')} onClick={() => selectDate('week')}>
                本周
              </a>
              <a className={isActive('month')} onClick={() => selectDate('month')}>
                本月
              </a>
              <a className={isActive('year')} onClick={() => selectDate('year')}>
                本年
              </a>
              <a className={isActive('all')} onClick={() => selectDate('all')}>
                全部
              </a>
            </div>
            <RangePicker
              value={rangePickerValue as any}
              onChange={handleRangePickerChange as any}
              style={{width: 256}}
            />
          </div>
        }
        size="large"
        tabBarStyle={{marginBottom: 24}}
      >
        <TabPane tab="收益金额" key="sales">
          <Row>
            <Col xl={16} lg={12} md={12} sm={24} xs={24}>
              <div className={styles.salesBar}>
                <Column
                  height={300}
                  // forceFit
                  data={salesData as any}
                  xField="x"
                  yField="y"
                  xAxis={{
                    // visible: true,
                    title: {
                      // visible: false,
                    },
                  }}
                  yAxis={{
                    // visible: true,
                    title: {
                      // visible: false,
                    },
                  }}
                  // title={''}
                  meta={{
                    y: {
                      alias: '收益金额(元)',
                    },
                    x: {
                      alias: '日期',
                    },
                  }}
                />
              </div>
            </Col>
            <Col xl={8} lg={12} md={12} sm={24} xs={24}>
              <div className={styles.salesRank}>
                <h4 className={styles.rankingTitle}>收益金额排行</h4>
                <ul className={styles.rankingList}>
                  {incomeOrder.map((item, i) => (
                    <li key={i}>
                       <span className={`${styles.rankingItemNumber} ${styles.active}`}>
                        {i + 1}
                      </span>
                      <span className={styles.rankingItemTitle} title={item.x}>
                       <a href={`/list?code=${item.x.split("-")[0]}`}>{item.x} </a>
                      </span>
                      <span className={styles.rankingItemValue}>
                        {`${numeral(item.y).format('0.00')} 元`}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            </Col>
          </Row>
        </TabPane>
        <TabPane tab="日收益率" key="views">
          <Row>
            <Col xl={16} lg={12} md={12} sm={24} xs={24}>
              <div className={styles.salesBar}>
                <Area
                  smooth
                  height={300}
                  data={salesData2 as any}
                  xField="x"
                  yField="y"
                  xAxis={{
                    // visible: true,
                    title: {
                      // visible: false,
                    },
                  }}
                  yAxis={{
                    // visible: true,
                    title: {
                      // visible: false,
                    },
                  }}
                  // title={{
                  //   visible: true,
                  //   text: '收益率统计',
                  //   style: {
                  //     fontSize: 14,
                  //   },
                  // }}
                  meta={{
                    x: {
                      alias: '日期',
                    },
                    y: {
                      alias: '日收益率(%)',
                    },
                  }}
                />
              </div>
            </Col>
            <Col xl={8} lg={12} md={12} sm={24} xs={24}>
              <div className={styles.salesRank}>
                <h4 className={styles.rankingTitle}>日收益率排行</h4>
                <ul className={styles.rankingList}>
                  {rateOrder.map((item, i) => (
                    <li key={item.x}>
                      <span className={`${styles.rankingItemNumber} ${styles.active}`}>
                        {i + 1}
                      </span>
                      <span className={styles.rankingItemTitle} title={item.x}>
                          <a href={`/list?code=${item.x.split("-")[0]}`}>{item.x} </a>
                      </span>
                      <span>{`${numeral(item.y).format('0.0000')} %`}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </Col>
          </Row>
        </TabPane>
      </Tabs>
    </div>
  </Card>
);

export default SalesCard;
