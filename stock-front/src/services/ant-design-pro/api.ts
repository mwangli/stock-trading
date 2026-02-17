// @ts-ignore
/* eslint-disable */
import {request} from '@umijs/max';

/** 获取当前的用户 GET /api/login/curretUserr */
export async function currentUser(options?: { [key: string]: any }) {
  return request<{
    data: API.CurrentUser;
  }>('/api/login/currentUser', {
    method: 'GET',
    ...(options || {}),
  });
}

/** 退出登录接口 POST /api/login/outLogin */
export async function outLogin(options?: { [key: string]: any }) {
  return request<Record<string, any>>('/api/login/outLogin', {
    method: 'POST',
    ...(options || {}),
  });
}

/** 登录接口 POST /api/login/account */
export async function login(body: API.LoginParams, options?: { [key: string]: any }) {
  return request<API.LoginResult>('/api/login/account', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /api/notices */
export async function getNotices(options?: { [key: string]: any }) {
  return request<API.NoticeIconList>('/api/notices', {
    method: 'GET',
    ...(options || {}),
  });
}

/** 获取交易记录 GET /api/listFoundTrading */
export async function listFoundTrading(
  params: {
    // query
    /** 当前的页码 */
    current?: number;
    /** 页面的容量 */
    pageSize?: number;
    name?: string;
    code?: string;
  },
  sort: object,
  options?: { [key: string]: any },
) {
  return request<any>('/api/tradingRecord/list', {
    method: 'GET',
    params: {
      ...params,
      sortKey: sort ? Object.keys(sort)[0] : '',
      sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}

/** 获取交易记录 GET /api/listFoundTrading */
export async function listStockInfo(
  params: {
    // query
    /** 当前的页码 */
    current?: number;
    /** 页面的容量 */
    pageSize?: number;
    name?: string;
    code?: string;
  },
  sort: object,
  options?: { [key: string]: any },
) {
  return request<any>('/api/stockInfo/list', {
    method: 'GET',
    params: {
      ...params,
      sortKey: sort ? Object.keys(sort)[0] : '',
      sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}
/** 获取交易记录 GET /api/listFoundTrading */
export async function listOrderInfo(
  params: {
    // query
    /** 当前的页码 */
    current?: number;
    /** 页面的容量 */
    pageSize?: number;
    name?: string;
    code?: string;
  },
  sort: object,
  options?: { [key: string]: any },
) {
  return request('/api/orderInfo/list', {
    method: 'GET',
    params: {
      ...params,
      sortKey: sort ? Object.keys(sort)[0] : '',
      sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}

/** 获取历史价格 GET /api/listHistoryPrices */
export async function listHistoryPrices(
  params: {
    // query
    // /** 当前的页码 */
    // current?: number;
    // /** 页面的容量 */
    // pageSize?: number;
    // name?: string;
    code?: string;
  },
  // sort: object,
  options?: { [key: string]: any },
) {
  return request<any>('/api/stockInfo/listHistoryPrices', {
    method: 'GET',
    params: {
      ...params,
      // sortKey: sort ? Object.keys(sort)[0] : '',
      // sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}


/** 选中自选股票 GET /api/selectStockInfo */
export async function selectStockInfo(
  params: {
    code?: string;
  },
  // sort: object,
  options?: { [key: string]: any },
) {
  return request<any>('/api/stockInfo/selectStockInfo', {
    method: 'GET',
    params: {
      ...params,
      // sortKey: sort ? Object.keys(sort)[0] : '',
      // sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}


/** 取消自选股票 GET /api/cancelStockInfo */
export async function cancelStockInfo(
  params: {
    code?: string;
  },
  // sort: object,
  options?: { [key: string]: any },
) {
  return request<any>('/api/stockInfo/cancelStockInfo', {
    method: 'GET',
    params: {
      ...params,
      // sortKey: sort ? Object.keys(sort)[0] : '',
      // sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}

/** 获取历史价格 GET /api/listHistoryPrices */
export async function listIncreaseRate(
  params: {
    // query
    // /** 当前的页码 */
    // current?: number;
    // /** 页面的容量 */
    // pageSize?: number;
    // name?: string;
    code?: string;
  },
  // sort: object,
  options?: { [key: string]: any },
) {
  return request('/api/stockInfo/listIncreaseRate', {
    method: 'GET',
    params: {
      ...params,
      // sortKey: sort ? Object.keys(sort)[0] : '',
      // sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}

/** 获取历史价格 GET /api/listHistoryPrices */
export async function listTestData(
  params: {
    // query
    // /** 当前的页码 */
    // current?: number;
    // /** 页面的容量 */
    // pageSize?: number;
    name?: string;
    code?: string;
  },
  // sort: object,
  options?: { [key: string]: any },
) {
  return request<any>('/api/modelInfo/listTestData', {
    method: 'GET',
    params: {
      ...params,
      // sortKey: sort ? Object.keys(sort)[0] : '',
      // sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}

/** 获取历史价格 GET /api/listHistoryPrices */
export async function listValidateData(
  params: {
    // query
    // /** 当前的页码 */
    // current?: number;
    // /** 页面的容量 */
    // pageSize?: number;
    name?: string;
    code?: string;
  },
  // sort: object,
  options?: { [key: string]: any },
) {
  return request<any>('/api/modelInfo/listValidateData', {
    method: 'GET',
    params: {
      ...params,
      // sortKey: sort ? Object.keys(sort)[0] : '',
      // sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}

/** 获取定时任务 GET /api/job */
export async function listJob(
  params: {
    // query
    /** 当前的页码 */
    current?: number;
    /** 页面的容量 */
    pageSize?: number;
    name?: string;
    code?: string;
  },
  // sort: any,
  options?: { [key: string]: any },
) {
  return request<any>('/api/job/list', {
    method: 'GET',
    params: {
      ...params,
      // sortKey: Object.keys(sort)[0],
      // sortOrder: Object.values(sort)[0],
    },
    ...(options || {}),
  });
}

/** 新建规则 PUT /api/rule */
export async function updateRule(options?: { [key: string]: any }) {
  return request<any>('/api/job/run', {
    method: 'POST',
    ...(options || {}),
  });
}

/** 新建规则 POST /api/rule */
export async function addRule(options?: { [key: string]: any }) {
  return request<any>('/api/rule', {
    method: 'POST',
    ...(options || {}),
  });
}

/** 删除规则 DELETE /api/rule */
export async function removeRule(options?: { [key: string]: any }) {
  return request<Record<string, any>>('/api/rule', {
    method: 'DELETE',
    ...(options || {}),
  });
}

export async function createJob(options?: { [key: string]: any }) {
  return request<any>('/api/job/create', {
    method: 'POST',
    ...(options || {}),
  });
}

export async function modifyJob(options?: { [key: string]: any }) {
  return request<API.ApiResponse>('/api/job/update', {
    method: 'PUT',
    ...(options || {}),
  });
}

export async function deleteJob(options?: { [key: string]: any }) {
  return request<any>('/api/job/delete', {
    method: 'DELETE',
    ...(options || {}),
  });
}

export async function runJob(options?: { [key: string]: any }) {
  return request<any>('/api/job/run', {
    method: 'POST',
    ...(options || {}),
  });
}

export async function pauseJob(options?: { [key: string]: any }) {
  return request<any>('/api/job/pause', {
    method: 'POST',
    ...(options || {}),
  });
}

export async function interruptJob(options?: { [key: string]: any }) {
  return request<any>('/api/job/interrupt', {
    method: 'POST',
    ...(options || {}),
  });
}

export async function resumeJob(options?: { [key: string]: any }) {
  return request<any>('/api/job/resume', {
    method: 'POST',
    ...(options || {}),
  });
}

export async function createStrategy(options?: { [key: string]: any }) {
  return request<any>('/api/strategy/create', {
    method: 'POST',
    ...(options || {}),
  });
}

export async function modifyStrategy(options?: { [key: string]: any }) {
  return request<API.ApiResponse>('/api/strategy/update', {
    method: 'PUT',
    ...(options || {}),
  });
}

export async function chooseStrategy(options?: { [key: string]: any }) {
  return request<API.ApiResponse>('/api/strategy/choose', {
    method: 'POST',
    ...(options || {}),
  });
}

export async function deleteStrategy(options?: { [key: string]: any }) {
  return request<any>('/api/strategy/delete', {
    method: 'DELETE',
    ...(options || {}),
  });
}

/** 获取定时任务 GET /api/job */
export async function listStrategy(
  params: {
    // query
    /** 当前的页码 */
    current?: number;
    /** 页面的容量 */
    pageSize?: number;
    name?: string;
    code?: string;
  },
  sort: object,
  options?: { [key: string]: any },
) {
  return request<any>('/api/modelInfo/list', {
    method: 'GET',
    params: {
      ...params,
      sortKey: sort ? Object.keys(sort)[0] : '',
      sortOrder: sort ? Object.values(sort)[0] : '',
    },
    ...(options || {}),
  });
}
/** 图片上传接口 POST /api/login/account */
export async function upload(file: API.LoginParams, options?: { [key: string]: any }) {
  return request<API.LoginResult>('/api/ocr/uploadImage', {
    method: 'POST',
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    data: file,
    ...(options || {}),
  });
}

/** 文件下载接口 Get /api/ocr/download**/
export async function download(file: API.LoginParams, options?: { [key: string]: any }) {
  return request<API.LoginResult>('/api/ocr/downloadExcel', {
    method: 'GET',
    // headers: {
    //   'Content-Type': 'multipart/form-data',
    // },
    responseType : 'blob',
    ...(options || {}),
  });


}
