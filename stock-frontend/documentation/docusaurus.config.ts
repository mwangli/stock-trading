// AI_GENERATE_START ---------
import type {Config} from '@docusaurus/types';
import type {Options, ThemeConfig} from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Stock Trading 工程文档',
  tagline: '架构、领域设计、运行边界与源码索引',
  favicon: 'img/system-mark.svg',
  url: process.env.DOCS_URL ?? 'http://localhost:5173',
  baseUrl: process.env.DOCS_BASE_URL ?? '/docs/',
  organizationName: 'mwangli',
  projectName: 'stock-trading',
  trailingSlash: false,
  onBrokenLinks: 'throw',
  markdown: {
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: 'throw',
    },
  },
  i18n: {
    defaultLocale: 'zh-Hans',
    locales: ['zh-Hans'],
  },
  presets: [
    [
      'classic',
      {
        docs: {
          path: '../../documents',
          routeBasePath: '/',
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/mwangli/stock-trading/edit/master/documents/',
          showLastUpdateAuthor: true,
          showLastUpdateTime: true,
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Options,
    ],
  ],
  themes: [
    '@docusaurus/theme-mermaid',
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        language: ['en', 'zh'],
        indexDocs: true,
        indexPages: false,
        highlightSearchTermsOnTargetPage: true,
      },
    ],
  ],
  themeConfig: {
    image: 'img/system-mark.svg',
    colorMode: {
      defaultMode: 'light',
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Stock Trading 文档',
      logo: {
        alt: 'Stock Trading',
        src: 'img/system-mark.svg',
      },
      items: [
        {to: '/system/project-overview', label: '系统总览', position: 'left'},
        {to: '/system/documentation-governance', label: '文档治理', position: 'left'},
        {to: '/backend/model/', label: 'Model', position: 'left'},
        {to: '/agent/', label: 'Agent', position: 'left'},
        {
          href: 'https://github.com/mwangli/stock-trading',
          label: '源码',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'light',
      copyright: `Stock Trading 工程文档 · ${new Date().getFullYear()}`,
    },
    prism: {
      additionalLanguages: ['java', 'bash', 'json', 'yaml', 'sql'],
    },
  } satisfies ThemeConfig,
};

export default config;
// AI_GENERATE_END ---------
