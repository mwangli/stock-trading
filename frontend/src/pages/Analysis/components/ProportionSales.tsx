import {Card, Radio, Typography} from 'antd';
import type {RadioChangeEvent} from 'antd/es/radio';
// import { Donut } from '@ant-design/charts';
// import type { DonutConfig } from '@ant-design/charts/es/donut';
import React from 'react';
import type {DataItem} from '../data';
import styles from '../style.less';
import {Datum, Pie} from '@ant-design/charts';


const {Text} = Typography;
const config = {
  appendPadding: 10,
  angleField: 'y',
  colorField: 'x',
  radius: 1,
  innerRadius: 0.6,
  label: {
    type: 'inner',
    offset: '-50%',
    content: '{name}\n数量:{value}',
    style: {
      textAlign: 'center',
      fontSize: 14,
    },
  },
  interactions: [
    {
      type: 'element-selected',
    },
    {
      type: 'element-active',
    },
  ],
  // meta: {
  //   x: {
  //     alias: "持有天数1"
  //   },
  //   y: {
  //     alias: "占比数量1"
  //   }
  // },
  statistic: {
    // title: "",
    content: {
      style: {
        whiteSpace: 'pre-wrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
      },
      // content: '总交易股数',
    },
  },
  tooltip: {
    formatter: (datum: Datum) => {
      return {name: ' 收益范围' + datum.x, value: '占比数量:' + datum.y};
    },
  },
};
const ProportionSales = ({
                           dropdownGroup,
                           salesType,
                           loading,
                           salesPieData,
                           handleChangeSalesType,
                         }: {
  loading: boolean;
  dropdownGroup: React.ReactNode;
  salesType: 'all' | 'online' | 'stores';
  salesPieData: DataItem[];
  handleChangeSalesType?: (e: RadioChangeEvent) => void;
}) => (
  <Card
    loading={loading}
    className={styles.salesCard}
    bordered={false}
    title="收益范围占比"
    style={{
      height: '100%',
    }}
    extra={
      <div className={styles.salesCardExtra}>
        {dropdownGroup}
        <div className={styles.salesTypeRadio}>
          <Radio.Group value={salesType} onChange={handleChangeSalesType}>
            {/*<Radio.Button value="all">全部渠道</Radio.Button>*/}
            {/*<Radio.Button value="online">线上</Radio.Button>*/}
            {/*<Radio.Button value="stores">门店</Radio.Button>*/}
          </Radio.Group>
        </div>
      </div>
    }
  >
    <div>
      <Text>收益范围</Text>
      <Pie
        data={salesPieData}
        {...config}
      />
    </div>
  </Card>
);

export default ProportionSales;
