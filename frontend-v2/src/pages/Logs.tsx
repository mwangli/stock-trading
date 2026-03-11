import React from 'react';

interface LogsProps {}

const Logs: React.FC<LogsProps> = () => {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-semibold mb-4">系统日志</h1>
      <p className="text-sm text-gray-500">
        日志页面暂未实现详细功能，后续将在此展示系统运行日志、任务执行记录等信息。
      </p>
    </div>
  );
};

export default Logs;

