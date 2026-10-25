// AI_GENERATE_START --
import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  documentation: [
    'documentation-home',
    {
      type: 'category',
      label: '系统与治理',
      collapsed: false,
      link: {type: 'doc', id: 'system/project-overview'},
      items: [
        'system/documentation-governance',
        'system/adr-001-docusaurus-documentation-portal',
      ],
    },
    {
      type: 'category',
      label: 'Backend',
      collapsed: false,
      link: {type: 'doc', id: 'backend/backend-overview'},
      items: [
        'backend/market/market-overview',
        {
          type: 'category',
          label: 'Model',
          link: {type: 'doc', id: 'backend/model/model-overview'},
          items: [
            {
              type: 'category',
              label: '专题设计',
              items: [
                'backend/model/designs/ranking-lstm-v6',
                'backend/model/designs/temporal-model-ensemble',
                'backend/model/designs/mengzi-shadow-deployment',
              ],
            },
          ],
        },
        'backend/decision/decision-overview',
        'backend/execution/execution-overview',
        'backend/schedule/schedule-overview',
        'backend/platform/platform-overview',
      ],
    },
    {
      type: 'category',
      label: 'Agent',
      collapsed: false,
      link: {type: 'doc', id: 'agent/agent-overview'},
      items: [
        'agent/decision/agent-decision',
        'agent/integration/agent-integration',
        'agent/interaction/agent-interaction',
        'agent/knowledge/agent-knowledge',
        'agent/runtime/agent-runtime',
        'agent/shared/agent-shared',
        'agent/tooling/agent-tooling',
        'agent/tradeplan/agent-tradeplan',
      ],
    },
    'frontend/frontend-overview',
  ],
};

export default sidebars;
// AI_GENERATE_END --
