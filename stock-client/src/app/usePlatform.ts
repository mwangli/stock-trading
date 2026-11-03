// AI_GENERATE_START -
import { useEffect, useState } from 'react';

export type AppPlatform = 'desktop' | 'mobile';

const MOBILE_QUERY = '(max-width: 991px)';

const getSnapshot = (): AppPlatform => (
  window.matchMedia(MOBILE_QUERY).matches ? 'mobile' : 'desktop'
);

export const usePlatform = (): AppPlatform => {
  const [platform] = useState<AppPlatform>(getSnapshot);

  useEffect(() => {
    const mediaQuery = window.matchMedia(MOBILE_QUERY);
    const reloadForPlatformChange = () => window.location.reload();
    mediaQuery.addEventListener('change', reloadForPlatformChange);
    return () => mediaQuery.removeEventListener('change', reloadForPlatformChange);
  }, []);

  return platform;
};
// AI_GENERATE_END -
