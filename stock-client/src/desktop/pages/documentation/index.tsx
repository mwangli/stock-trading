// AI_GENERATE_START --
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type FC,
  type Key,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
} from 'react';
import { Button, Empty, Input, Result, Tree, type TreeDataNode } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { FileText, ListTree } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import MarkdownDocument from './MarkdownDocument';
import {
  documentationDocuments,
  extractDocumentationHeadings,
  findDocumentation,
  searchDocumentation,
  type DocumentationEntry,
} from './documentRegistry';
import './styles.css';
import './antdOverrides.module.css';

interface MutableNavigationNode {
  path: string;
  name: string;
  documents: DocumentationEntry[];
  children: Map<string, MutableNavigationNode>;
}

type SidebarSide = 'left' | 'right';
interface ResizeState { side: SidebarSide; pointerId: number; startX: number; startWidth: number; currentWidth: number; }

const WIDTH_KEY = 'stockTrading.documentation.sidebarWidth';
const LIMITS = {
  left: { min: 240, max: 420, fallback: 292 },
  right: { min: 190, max: 340, fallback: 236 },
} as const;
const directoryLabels: Record<string, string> = {
  '01-system': '系统架构',
  '02-business': '业务设计',
  '03-code': '代码说明',
  '04-operations': '运行运维',
  '05-decisions': '架构决策',
  '06-history': '设计演进',
};

const readWidth = (side: SidebarSide) => {
  const value = Number(window.localStorage.getItem(`${WIDTH_KEY}.${side}`));
  const limit = LIMITS[side];
  return Number.isFinite(value) && value >= limit.min && value <= limit.max ? value : limit.fallback;
};

const buildNavigationTree = (): TreeDataNode[] => {
  const home = documentationDocuments.find((document) => document.route === '/docs');
  const root: MutableNavigationNode = { path: 'documents', name: 'documents', documents: [], children: new Map() };
  for (const document of documentationDocuments) {
    if (document === home) continue;
    const segments = document.sourcePath.slice('documents/'.length).split('/');
    segments.pop();
    let node = root;
    for (const segment of segments) {
      const child = node.children.get(segment) ?? {
        path: `${node.path}/${segment}`,
        name: segment,
        documents: [],
        children: new Map<string, MutableNavigationNode>(),
      };
      node.children.set(segment, child);
      node = child;
    }
    node.documents.push(document);
  }
  const convert = (node: MutableNavigationNode): TreeDataNode => ({
    key: `group:${node.path}`,
    title: directoryLabels[node.name] ?? node.name,
    selectable: false,
    children: [
      ...node.documents.map((document) => ({ key: document.route, title: document.metadata.title, isLeaf: true })),
      ...[...node.children.values()].map(convert),
    ],
  });
  return [
    ...(home ? [{ key: home.route, title: home.metadata.title, isLeaf: true }] : []),
    ...[...root.children.values()].map(convert),
  ];
};

const collectExpandedKeys = (nodes: TreeDataNode[]): Key[] => nodes.flatMap((node) => [
  ...(node.children?.length ? [node.key] : []),
  ...collectExpandedKeys(node.children ?? []),
]);

const DocumentationNavigation: FC<{
  query: string;
  selectedRoute: string;
  onQueryChange: (query: string) => void;
  onNavigate: (route: string) => void;
}> = ({ query, selectedRoute, onQueryChange, onNavigate }) => {
  const treeData = useMemo(buildNavigationTree, []);
  const expandedKeys = useMemo(() => collectExpandedKeys(treeData), [treeData]);
  const results = useMemo(() => searchDocumentation(query), [query]);
  return (
    <div className="documentation-navigation">
      <div className="documentation-navigation-heading"><strong>系统文档</strong><span>{documentationDocuments.length} 篇</span></div>
      <Input allowClear value={query} prefix={<SearchOutlined />} placeholder="搜索业务、代码或接口" onChange={(event) => onQueryChange(event.target.value)} />
      <div className="documentation-navigation-body custom-scrollbar">
        {query.trim() ? results.length > 0 ? (
          <div className="documentation-search-results">
            {results.map((document) => (
              <button key={document.route} type="button" onClick={() => onNavigate(document.route)}>
                <FileText size={15} />
                <span><strong>{document.metadata.title}</strong><small>{document.sourcePath.slice('documents/'.length)}</small></span>
              </button>
            ))}
          </div>
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到匹配文档" /> : (
          <Tree
            blockNode
            showLine
            defaultExpandedKeys={expandedKeys}
            selectedKeys={[selectedRoute]}
            treeData={treeData}
            onSelect={(keys) => {
              const route = String(keys[0] ?? '');
              if (route.startsWith('/docs')) onNavigate(route);
            }}
          />
        )}
      </div>
    </div>
  );
};

const DocumentationPage: FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const contentRef = useRef<HTMLDivElement>(null);
  const resizeState = useRef<ResizeState | null>(null);
  const [query, setQuery] = useState('');
  const [leftWidth, setLeftWidth] = useState(() => readWidth('left'));
  const [rightWidth, setRightWidth] = useState(() => readWidth('right'));
  const document = findDocumentation(location.pathname);
  const headings = useMemo(() => document ? extractDocumentationHeadings(document.body).filter((heading) => heading.level > 1) : [], [document]);
  const documentIndex = document ? documentationDocuments.indexOf(document) : -1;
  const hashHeadingId = location.hash ? decodeURIComponent(location.hash.slice(1)) : '';
  const [activeHeadingId, setActiveHeadingId] = useState(hashHeadingId);
  usePageHeader(document?.metadata.title ?? '业务、代码、运维与历史设计说明');

  const scrollToHeading = useCallback((headingId: string, behavior: ScrollBehavior = 'smooth') => {
    const content = contentRef.current;
    const heading = window.document.getElementById(headingId);
    if (!content || !heading) return;
    const top = content.scrollTop + heading.getBoundingClientRect().top - content.getBoundingClientRect().top - 18;
    content.scrollTo({ top: Math.max(0, top), behavior });
  }, []);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      if (hashHeadingId) {
        scrollToHeading(hashHeadingId, 'auto');
        setActiveHeadingId(hashHeadingId);
      } else {
        contentRef.current?.scrollTo({ top: 0 });
        setActiveHeadingId(headings[0]?.id ?? '');
      }
    });
    return () => window.cancelAnimationFrame(frame);
  }, [document, hashHeadingId, headings, scrollToHeading]);

  useEffect(() => {
    const content = contentRef.current;
    if (!content || headings.length === 0) return undefined;
    let frame = 0;
    const update = () => {
      frame = 0;
      const activationLine = content.getBoundingClientRect().top + Math.min(150, content.clientHeight * 0.25);
      let next = headings[0].id;
      for (const heading of headings) {
        const element = window.document.getElementById(heading.id);
        if (!element || element.getBoundingClientRect().top > activationLine) break;
        next = heading.id;
      }
      setActiveHeadingId(next);
    };
    const onScroll = () => { if (!frame) frame = window.requestAnimationFrame(update); };
    content.addEventListener('scroll', onScroll, { passive: true });
    frame = window.requestAnimationFrame(update);
    return () => {
      content.removeEventListener('scroll', onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, [document, headings]);

  const setWidth = (side: SidebarSide, width: number, persist = false) => {
    const limit = LIMITS[side];
    const resolved = Math.min(limit.max, Math.max(limit.min, width));
    if (side === 'left') setLeftWidth(resolved); else setRightWidth(resolved);
    if (persist) window.localStorage.setItem(`${WIDTH_KEY}.${side}`, String(Math.round(resolved)));
  };
  const beginResize = (side: SidebarSide, event: ReactPointerEvent<HTMLDivElement>) => {
    const startWidth = side === 'left' ? leftWidth : rightWidth;
    resizeState.current = { side, pointerId: event.pointerId, startX: event.clientX, startWidth, currentWidth: startWidth };
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const continueResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    const state = resizeState.current;
    if (!state || state.pointerId !== event.pointerId) return;
    const direction = state.side === 'left' ? 1 : -1;
    const nextWidth = state.startWidth + (event.clientX - state.startX) * direction;
    const limit = LIMITS[state.side];
    state.currentWidth = Math.min(limit.max, Math.max(limit.min, nextWidth));
    setWidth(state.side, state.currentWidth);
  };
  const finishResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    const state = resizeState.current;
    if (!state || state.pointerId !== event.pointerId) return;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    setWidth(state.side, state.currentWidth, true);
    resizeState.current = null;
  };
  const resizeByKeyboard = (side: SidebarSide, event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Home') {
      event.preventDefault();
      window.localStorage.removeItem(`${WIDTH_KEY}.${side}`);
      setWidth(side, LIMITS[side].fallback);
      return;
    }
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const direction = side === 'left'
      ? (event.key === 'ArrowRight' ? 1 : -1)
      : (event.key === 'ArrowLeft' ? 1 : -1);
    setWidth(side, (side === 'left' ? leftWidth : rightWidth) + direction * 16, true);
  };
  const resetWidth = (side: SidebarSide) => {
    window.localStorage.removeItem(`${WIDTH_KEY}.${side}`);
    setWidth(side, LIMITS[side].fallback);
  };

  if (!document) return <div className="documentation-not-found"><Result status="404" title="文档不存在" extra={<Button type="primary" onClick={() => navigate('/docs')}>返回文档首页</Button>} /></div>;

  return (
    <div className="documentation-page" style={{ '--documentation-left-width': `${leftWidth}px`, '--documentation-right-width': `${rightWidth}px` } as CSSProperties}>
      <aside className="documentation-sidebar">
        <DocumentationNavigation query={query} selectedRoute={document.route} onQueryChange={setQuery} onNavigate={navigate} />
        <div className="documentation-resize-handle is-left" role="separator" aria-label="调整文档目录宽度" tabIndex={0} onPointerDown={(event) => beginResize('left', event)} onPointerMove={continueResize} onPointerUp={finishResize} onPointerCancel={finishResize} onKeyDown={(event) => resizeByKeyboard('left', event)} onDoubleClick={() => resetWidth('left')} />
      </aside>
      <main ref={contentRef} className="documentation-content custom-scrollbar">
        <div className="documentation-article-shell">
          <MarkdownDocument document={document} previous={documentIndex > 0 ? documentationDocuments[documentIndex - 1] : undefined} next={documentIndex >= 0 ? documentationDocuments[documentIndex + 1] : undefined} />
        </div>
      </main>
      <aside className="documentation-toc">
        <div className="documentation-resize-handle is-right" role="separator" aria-label="调整章节目录宽度" tabIndex={0} onPointerDown={(event) => beginResize('right', event)} onPointerMove={continueResize} onPointerUp={finishResize} onPointerCancel={finishResize} onKeyDown={(event) => resizeByKeyboard('right', event)} onDoubleClick={() => resetWidth('right')} />
        <div className="documentation-toc-scroll custom-scrollbar">
          <div className="documentation-toc-heading"><ListTree size={15} />本页章节</div>
          {headings.length > 0 ? headings.map((heading) => (
            <a
              key={heading.id}
              className={activeHeadingId === heading.id ? 'is-active' : undefined}
              href={`#${encodeURIComponent(heading.id)}`}
              style={{ paddingLeft: `${10 + (heading.level - 2) * 12}px` }}
              onClick={(event) => {
                event.preventDefault();
                setActiveHeadingId(heading.id);
                scrollToHeading(heading.id, 'auto');
                navigate({ pathname: location.pathname, hash: `#${encodeURIComponent(heading.id)}` }, { replace: true });
              }}
            >{heading.text}</a>
          )) : <span className="documentation-toc-empty">暂无二级标题</span>}
        </div>
      </aside>
    </div>
  );
};

export default DocumentationPage;
// AI_GENERATE_END --
