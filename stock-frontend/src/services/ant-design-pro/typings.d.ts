// @ts-ignore
/* eslint-disable */

declare namespace API {
  type CurrentUser = {
    name?: string;
    avatar?: string;
    userid?: string;
    email?: string;
    signature?: string;
    title?: string;
    group?: string;
    tags?: { key?: string; label?: string }[];
    notifyCount?: number;
    unreadCount?: number;
    country?: string;
    access?: string;
    geographic?: {
      province?: { label?: string; key?: string };
      city?: { label?: string; key?: string };
    };
    address?: string;
    phone?: string;
  };

  type LoginResult = {
    errorMessage: string;
    success: boolean;
    data: any;
    status?: string;
    type?: string;
    currentAuthority?: string;
  };

  type PageParams = {
    current?: number;
    pageSize?: number;
  };

  type RuleListItem = {
    selected: string;
    className: any;
    running: string;
    minRate: any;
    maxRate: any;
    minPrice: any;
    maxPrice: any;
    increaseRateList: Record<string, any>[];
    pricesList: Record<string, any>[];
    prices: any[];
    dailyIncomeRate: {};
    rateOrder: [];
    dailyRateList: any[];
    success: any;
    status: any;
    data: any;
    name: string,
    accountAmount: number,
    accountDate: string,
    buyAmount: number,
    buyDate: string,
    code: string,
    createTime: string,
    expectedIncome: number,
    expectedIncomeRate: number,
    id: number,
    realIncome: number,
    realIncomeRate: number,
    saleAmount: number,
    saleDate: string,
    updateTime: string,
    key?: number;
    // disabled?: boolean;
    // href?: string;
    // avatar?: string;
    // name?: string;
    // owner?: string;
    desc?: string;
    callNo?: number;
    // status?: number;
    // updatedAt?: string;
    // createdAt?: string;
    // progress?: number;
  };

  type RuleList = {
    data?: RuleListItem[];
    /** 列表的内容总数 */
    total?: number;
    success?: boolean;
  };

  type FakeCaptcha = {
    code?: number;
    status?: string;
  };

  type LoginParams = {
    username?: string;
    password?: string;
    autoLogin?: boolean;
    type?: string;
  };

  type ErrorResponse = {
    /** 业务约定的错误码 */
    errorCode: string;
    /** 业务上的错误信息 */
    errorMessage?: string;
    /** 业务上的请求是否成功 */
    success?: boolean;
  };

  type NoticeIconList = {
    data?: NoticeIconItem[];
    /** 列表的内容总数 */
    total?: number;
    success?: boolean;
  };

  type NoticeIconItemType = 'notification' | 'message' | 'event';

  type NoticeIconItem = {
    id?: string;
    extra?: string;
    key?: string;
    read?: boolean;
    avatar?: string;
    title?: string;
    status?: string;
    datetime?: string;
    description?: string;
    type?: NoticeIconItemType;
  };
}
