import { request } from '@umijs/max';

// 示例 API 服务
export async function queryProducts(params?: any) {
  return request('/api/products', {
    method: 'GET',
    params,
  });
}

export async function queryOrders(params?: any) {
  return request('/api/orders', {
    method: 'GET',
    params,
  });
}

export async function getProductDetail(id: string) {
  return request(`/api/products/${id}`, {
    method: 'GET',
  });
}
