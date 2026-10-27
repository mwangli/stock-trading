// AI_GENERATE_START---
import React, { createContext, useContext, useLayoutEffect, useRef, type ReactNode } from 'react';

export interface PageHeaderState {
  registrationId: symbol;
  subtitle?: ReactNode;
}

type PageHeaderSetter = React.Dispatch<React.SetStateAction<PageHeaderState | null>>;

export const PageHeaderContext = createContext<PageHeaderSetter | null>(null);

/**
 * 将页面副标题注册到全局头部，并在页面卸载时清理。
 *
 * @param subtitle 页面业务副标题
 */
export const usePageHeader = (subtitle?: ReactNode) => {
  const setPageHeader = useContext(PageHeaderContext);
  const registrationId = useRef(Symbol('page-header')).current;

  useLayoutEffect(() => {
    if (!setPageHeader) {
      return undefined;
    }

    setPageHeader({ registrationId, subtitle });
    return () => {
      setPageHeader((current) => current?.registrationId === registrationId ? null : current);
    };
  }, [registrationId, setPageHeader, subtitle]);
};
// AI_GENERATE_END---
