// AI_GENERATE_START------------------------------------
import { Suspense, lazy } from 'react';
import { BrowserRouter as Router } from 'react-router-dom';
import { Spin } from 'antd';
import { usePlatform } from './app/usePlatform';

const DesktopApp = lazy(() => import('./desktop/DesktopApp'));
const MobileApp = lazy(() => import('./mobile/MobileApp'));

const PageFallback = (
  <div className="flex min-h-[200px] items-center justify-center">
    <Spin size="large" />
  </div>
);

function App() {
  const platform = usePlatform();

  return (
    <Router>
      <Suspense fallback={PageFallback}>
        {platform === 'mobile' ? <MobileApp /> : <DesktopApp />}
      </Suspense>
    </Router>
  );
}

export default App;
// AI_GENERATE_END------------------------------------
