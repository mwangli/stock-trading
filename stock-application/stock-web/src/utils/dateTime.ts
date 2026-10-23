// AI_GENERATE_START--
const pad = (value: number) => String(value).padStart(2, '0');

/**
 * 将接口时间统一格式化为 yyyy-MM-dd HH:mm:ss。
 */
export const formatDateTime = (value?: string | null): string => {
  if (!value) {
    return '-';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};
// AI_GENERATE_END--
