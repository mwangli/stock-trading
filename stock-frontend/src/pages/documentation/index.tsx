// AI_GENERATE_START ------------
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
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
} from 'react';
import { Button, Drawer, Empty, Input, Result, Tree, type TreeDataNode } from 'antd';
import { MenuOutlined, SearchOutlined } from '@ant-design/icons';
import { FileText, Search } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { usePageHeader } from '../../layouts/PageHeaderContext';
import { useAuth } from '../../auth/AuthContext';
import MarkdownDocument from './MarkdownDocument';
import {
  documentationDocuments,
  extractDocumentationHeadings,
  findDocumentation,
  searchDocumentation,
  type DocumentationEntry,
} from './documentRegistry';
import './styles.css';

interface MutableNavigationNode {
  path: string;
  name: string;
  indexDocument?: DocumentationEntry;
  documents: DocumentationEntry[];
  children: Map<string, MutableNavigationNode>;
}

type DocumentationSidebarSide = 'left' | 'right';

interface DocumentationResizeState {
  side: DocumentationSidebarSide;
  pointerId: number;
  startX: number;
  startWidth: number;
  currentWidth: number;
  previousCursor: string;
  previousUserSelect: string;
}

const DOCUMENTATION_LEFT_WIDTH_KEY = 'stockTrading.documentation.leftSidebarWidth';
const DOCUMENTATION_RIGHT_WIDTH_KEY = 'stockTrading.documentation.rightSidebarWidth';
const DOCUMENTATION_CONTENT_MIN_WIDTH = 640;
const DOCUMENTATION_SIDEBAR_LIMITS = {
  left: { min: 260, max: 440, fallback: 300 },
  right: { min: 200, max: 360, fallback: 240 },
} as const;

const getDocumentationSidebarKey = (side: DocumentationSidebarSide, userId: number | undefined) => {
  const key = side === 'left' ? DOCUMENTATION_LEFT_WIDTH_KEY : DOCUMENTATION_RIGHT_WIDTH_KEY;
  return `${key}.${userId ?? 'anonymous'}`;
};

const readStoredSidebarWidth = (side: DocumentationSidebarSide, userId: number | undefined): number => {
  const scopedKey = getDocumentationSidebarKey(side, userId);
  const legacyKey = side === 'left' ? DOCUMENTATION_LEFT_WIDTH_KEY : DOCUMENTATION_RIGHT_WIDTH_KEY;
  const storedValue = window.localStorage.getItem(scopedKey) ?? window.localStorage.getItem(legacyKey);
  const value = Number(storedValue);
  const limits = DOCUMENTATION_SIDEBAR_LIMITS[side];
  if (Number.isFinite(value) && value >= limits.min && value <= limits.max) {
    if (window.localStorage.getItem(scopedKey) === null) {
      window.localStorage.setItem(scopedKey, String(value));
    }
    return value;
  }
  return limits.fallback;
};

const directoryLabels: Record<string, string> = {
  system: '系统与治理',
  designs: '专题设计',
  proposals: '方案提案',
  runbooks: '运行手册',
};

const buildNavigationTree = (): TreeDataNode[] => {
  const root: MutableNavigationNode = {
    path: 'documents',
    name: 'documents',
    documents: [],
    children: new Map(),
  };
  const home = documentationDocuments.find((document) => document.route === '/docs');

  for (const document of documentationDocuments) {
    if (document === home) {
      continue;
    }
    const relativePath = document.sourcePath.slice('documents/'.length);
    const segments = relativePath.split('/');
    segments.pop();
    const directoryRoute = segments.length > 0 ? `/docs/${segments.join('/')}` : '/docs';
    let node = root;
    for (const segment of segments) {
      let child = node.children.get(segment);
      if (!child) {
        child = {
          path: `${node.path}/${segment}`,
          name: segment,
          documents: [],
          children: new Map(),
        };
        node.children.set(segment, child);
      }
      node = child;
    }
    if (document.route === directoryRoute) {
      node.indexDocument = document;
    } else {
      node.documents.push(document);
    }
  }

  const convertNode = (node: MutableNavigationNode): TreeDataNode => {
    const childNodes = [...node.children.values()].map(convertNode);
    const documentNodes = node.documents.map((document) => ({
      key: document.route,
      title: document.metadata.title,
      isLeaf: true,
    }));
    return {
      key: node.indexDocument?.route ?? `group:${node.path}`,
      title: node.indexDocument?.metadata.title ?? directoryLabels[node.name] ?? node.name,
      selectable: Boolean(node.indexDocument),
      children: [...documentNodes, ...childNodes],
    };
  };

  return [
    ...(home ? [{ key: home.route, title: home.metadata.title, isLeaf: true }] : []),
    ...[...root.children.values()].map(convertNode),
  ];
};

const collectExpandedKeys = (nodes: TreeDataNode[]): Key[] => nodes.flatMap((node) => [
  ...(node.children?.length ? [node.key] : []),
  ...collectExpandedKeys(node.children ?? []),
]);

const DocumentationNavigation: FC<{
  query: string;
  onQueryChange: (query: string) => void;
  onNavigate: (route: string) => void;
  selectedRoute: string;
}> = ({ query, onQueryChange, onNavigate, selectedRoute }) => {
  const navigationBodyRef = useRef<HTMLDivElement>(null);
  const treeData = useMemo(buildNavigationTree, []);
  const expandedKeys = useMemo(() => collectExpandedKeys(treeData), [treeData]);
  const results = useMemo(() => searchDocumentation(query), [query]);

  useEffect(() => {
    if (query.trim()) {
      return undefined;
    }
    const frameId = window.requestAnimationFrame(() => {
      const navigationBody = navigationBodyRef.current;
      const selectedNode = navigationBody?.querySelector<HTMLElement>('.ant-tree-node-selected');
      if (!navigationBody || !selectedNode) {
        return;
      }
      const bodyRect = navigationBody.getBoundingClientRect();
      const nodeRect = selectedNode.getBoundingClientRect();
      const visiblePadding = 16;
      if (nodeRect.bottom > bodyRect.bottom - visiblePadding) {
        navigationBody.scrollTop += nodeRect.bottom - bodyRect.bottom + visiblePadding;
      } else if (nodeRect.top < bodyRect.top + visiblePadding) {
        navigationBody.scrollTop -= bodyRect.top - nodeRect.top + visiblePadding;
      }
    });
    return () => window.cancelAnimationFrame(frameId);
  }, [query, selectedRoute]);

  return (
    <div className="documentation-navigation">
      <div className="documentation-navigation-heading">
        <strong>工程文档</strong>
        <span>{documentationDocuments.length} 篇</span>
      </div>
      <Input
        allowClear
        value={query}
        prefix={<SearchOutlined />}
        placeholder="搜索标题、正文或类名"
        onChange={(event) => onQueryChange(event.target.value)}
      />
      <div ref={navigationBodyRef} className="documentation-navigation-body custom-scrollbar">
        {query.trim() ? (
          results.length > 0 ? (
            <div className="documentation-search-results">
              {results.map((document) => (
                <button key={document.route} type="button" onClick={() => onNavigate(document.route)}>
                  <FileText size={15} />
                  <span>
                    <strong>{document.metadata.title}</strong>
                    <small>{document.sourcePath.slice('documents/'.length)}</small>
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到匹配文档" />
          )
        ) : (
          <Tree
            blockNode
            showLine
            defaultExpandedKeys={expandedKeys}
            selectedKeys={[selectedRoute]}
            treeData={treeData}
            onSelect={(keys) => {
              const route = String(keys[0] ?? '');
              if (route.startsWith('/docs')) {
                onNavigate(route);
              }
            }}
          />
        )}
      </div>
    </div>
  );
};

/**
 * 在交易前端内部提供单进程、只读的 Markdown 文档浏览工作台。
 */
const DocumentationPage: FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();
  const contentRef = useRef<HTMLDivElement>(null);
  const leftSidebarRef = useRef<HTMLElement>(null);
  const rightSidebarRef = useRef<HTMLElement>(null);
  const tocScrollRef = useRef<HTMLDivElement>(null);
  const tocLinkRefs = useRef(new Map<string, HTMLAnchorElement>());
  const resizeStateRef = useRef<DocumentationResizeState | null>(null);
  const [query, setQuery] = useState('');
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
  const [leftSidebarWidth, setLeftSidebarWidth] = useState(() => readStoredSidebarWidth('left', user?.id));
  const [rightSidebarWidth, setRightSidebarWidth] = useState(() => readStoredSidebarWidth('right', user?.id));
  const document = findDocumentation(location.pathname);
  const headings = useMemo(
    () => document ? extractDocumentationHeadings(document.body).filter((heading) => heading.level > 1) : [],
    [document],
  );
  const documentIndex = document ? documentationDocuments.indexOf(document) : -1;
  const hashHeadingId = location.hash ? decodeURIComponent(location.hash.slice(1)) : '';
  const [activeHeadingId, setActiveHeadingId] = useState(hashHeadingId);

  usePageHeader(document?.metadata.title ?? '架构、领域设计、运行边界与源码索引');

  const scrollToHeading = useCallback((headingId: string, behavior: ScrollBehavior = 'smooth') => {
    const content = contentRef.current;
    const heading = window.document.getElementById(headingId);
    if (!content || !heading) {
      return;
    }
    const contentRect = content.getBoundingClientRect();
    const headingRect = heading.getBoundingClientRect();
    const targetTop = content.scrollTop + headingRect.top - contentRect.top - 18;
    content.scrollTo({ top: Math.max(0, targetTop), behavior });
  }, []);

  useEffect(() => {
    setLeftSidebarWidth(readStoredSidebarWidth('left', user?.id));
    setRightSidebarWidth(readStoredSidebarWidth('right', user?.id));
  }, [user?.id]);

  useEffect(() => {
    const scrollTarget = () => {
      if (location.hash) {
        document?.body && scrollToHeading(hashHeadingId, 'auto');
        setActiveHeadingId(hashHeadingId);
      } else {
        contentRef.current?.scrollTo({ top: 0 });
        setActiveHeadingId(headings[0]?.id ?? '');
      }
    };
    const frameId = window.requestAnimationFrame(scrollTarget);
    return () => window.cancelAnimationFrame(frameId);
  }, [document, hashHeadingId, headings, location.hash, scrollToHeading]);

  useEffect(() => {
    const content = contentRef.current;
    if (!content || headings.length === 0) {
      setActiveHeadingId('');
      return undefined;
    }

    let frameId = 0;
    const updateActiveHeading = () => {
      frameId = 0;
      const contentRect = content.getBoundingClientRect();
      const activationLine = contentRect.top + Math.min(160, content.clientHeight * 0.25);
      const reachedBottom = content.scrollTop + content.clientHeight >= content.scrollHeight - 2;
      let nextHeadingId = headings[0].id;

      if (reachedBottom) {
        nextHeadingId = headings.at(-1)?.id ?? nextHeadingId;
      } else {
        for (const heading of headings) {
          const element = window.document.getElementById(heading.id);
          if (!element || !content.contains(element)) {
            continue;
          }
          if (element.getBoundingClientRect().top > activationLine) {
            break;
          }
          nextHeadingId = heading.id;
        }
      }

      setActiveHeadingId((currentHeadingId) => (
        currentHeadingId === nextHeadingId ? currentHeadingId : nextHeadingId
      ));
    };
    const handleScroll = () => {
      if (!frameId) {
        frameId = window.requestAnimationFrame(updateActiveHeading);
      }
    };

    content.addEventListener('scroll', handleScroll, { passive: true });
    frameId = window.requestAnimationFrame(updateActiveHeading);
    return () => {
      content.removeEventListener('scroll', handleScroll);
      if (frameId) {
        window.cancelAnimationFrame(frameId);
      }
    };
  }, [document, headings]);

  useEffect(() => {
    const tocScroll = tocScrollRef.current;
    const activeLink = tocLinkRefs.current.get(activeHeadingId);
    if (!tocScroll || !activeLink) {
      return;
    }

    const containerRect = tocScroll.getBoundingClientRect();
    const linkRect = activeLink.getBoundingClientRect();
    const visiblePadding = 12;
    if (linkRect.top < containerRect.top + visiblePadding) {
      tocScroll.scrollTo({
        top: tocScroll.scrollTop + linkRect.top - containerRect.top - visiblePadding,
        behavior: 'smooth',
      });
    } else if (linkRect.bottom > containerRect.bottom - visiblePadding) {
      tocScroll.scrollTo({
        top: tocScroll.scrollTop + linkRect.bottom - containerRect.bottom + visiblePadding,
        behavior: 'smooth',
      });
    }
  }, [activeHeadingId]);

  const navigateToHeading = (event: ReactMouseEvent<HTMLAnchorElement>, headingId: string) => {
    event.preventDefault();
    setActiveHeadingId(headingId);
    if (hashHeadingId === headingId) {
      scrollToHeading(headingId);
      return;
    }
    navigate({
      pathname: location.pathname,
      search: location.search,
      hash: `#${encodeURIComponent(headingId)}`,
    }, { replace: true });
  };

  const navigateToDocument = (route: string) => {
    setMobileNavigationOpen(false);
    navigate(route);
  };

  const getSidebarElement = (side: DocumentationSidebarSide) => (
    side === 'left' ? leftSidebarRef.current : rightSidebarRef.current
  );

  const setSidebarWidth = (side: DocumentationSidebarSide, width: number, persist: boolean) => {
    if (side === 'left') {
      setLeftSidebarWidth(width);
    } else {
      setRightSidebarWidth(width);
    }
    if (persist) {
      const key = getDocumentationSidebarKey(side, user?.id);
      window.localStorage.setItem(key, String(Math.round(width)));
    }
  };

  const resolveSidebarWidth = (side: DocumentationSidebarSide, desiredWidth: number) => {
    const limits = DOCUMENTATION_SIDEBAR_LIMITS[side];
    const oppositeSide = side === 'left' ? 'right' : 'left';
    const oppositeWidth = getSidebarElement(oppositeSide)?.getBoundingClientRect().width ?? 0;
    const availableMaximum = window.innerWidth - oppositeWidth - DOCUMENTATION_CONTENT_MIN_WIDTH;
    const maximum = Math.max(limits.min, Math.min(limits.max, availableMaximum));
    return Math.min(maximum, Math.max(limits.min, desiredWidth));
  };

  const beginSidebarResize = (
    side: DocumentationSidebarSide,
    event: ReactPointerEvent<HTMLDivElement>,
  ) => {
    const sidebar = getSidebarElement(side);
    if (!sidebar) {
      return;
    }
    const startWidth = sidebar.getBoundingClientRect().width;
    resizeStateRef.current = {
      side,
      pointerId: event.pointerId,
      startX: event.clientX,
      startWidth,
      currentWidth: startWidth,
      previousCursor: window.document.body.style.cursor,
      previousUserSelect: window.document.body.style.userSelect,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
    window.document.body.style.cursor = 'col-resize';
    window.document.body.style.userSelect = 'none';
  };

  const continueSidebarResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    const state = resizeStateRef.current;
    if (!state || state.pointerId !== event.pointerId) {
      return;
    }
    const direction = state.side === 'left' ? 1 : -1;
    const width = resolveSidebarWidth(state.side, state.startWidth + (event.clientX - state.startX) * direction);
    state.currentWidth = width;
    setSidebarWidth(state.side, width, false);
  };

  const finishSidebarResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    const state = resizeStateRef.current;
    if (!state || state.pointerId !== event.pointerId) {
      return;
    }
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    window.document.body.style.cursor = state.previousCursor;
    window.document.body.style.userSelect = state.previousUserSelect;
    setSidebarWidth(state.side, state.currentWidth, true);
    resizeStateRef.current = null;
  };

  const resizeSidebarByKeyboard = (
    side: DocumentationSidebarSide,
    event: ReactKeyboardEvent<HTMLDivElement>,
  ) => {
    if (event.key === 'Home') {
      event.preventDefault();
      const key = getDocumentationSidebarKey(side, user?.id);
      window.localStorage.removeItem(key);
      const defaultWidth = DOCUMENTATION_SIDEBAR_LIMITS[side].fallback;
      side === 'left' ? setLeftSidebarWidth(defaultWidth) : setRightSidebarWidth(defaultWidth);
      return;
    }
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
      return;
    }
    event.preventDefault();
    const currentWidth = getSidebarElement(side)?.getBoundingClientRect().width
      ?? DOCUMENTATION_SIDEBAR_LIMITS[side].fallback;
    const direction = side === 'left'
      ? (event.key === 'ArrowRight' ? 1 : -1)
      : (event.key === 'ArrowLeft' ? 1 : -1);
    setSidebarWidth(side, resolveSidebarWidth(side, currentWidth + direction * 16), true);
  };

  const resetSidebarWidth = (side: DocumentationSidebarSide) => {
    const key = getDocumentationSidebarKey(side, user?.id);
    window.localStorage.removeItem(key);
    const defaultWidth = DOCUMENTATION_SIDEBAR_LIMITS[side].fallback;
    side === 'left' ? setLeftSidebarWidth(defaultWidth) : setRightSidebarWidth(defaultWidth);
  };

  const documentationLayoutStyle = {
    '--documentation-left-sidebar-width': `${leftSidebarWidth}px`,
    '--documentation-right-sidebar-width': `${rightSidebarWidth}px`,
  } as CSSProperties;

  if (!document) {
    return (
      <div className="documentation-not-found">
        <Result
          status="404"
          title="文档不存在"
          subTitle="当前地址没有对应的 Markdown 文档。"
          extra={<Button type="primary" onClick={() => navigate('/docs')}>返回文档首页</Button>}
        />
      </div>
    );
  }

  return (
    <div className="documentation-page" style={documentationLayoutStyle}>
      <aside ref={leftSidebarRef} className="documentation-sidebar">
        <DocumentationNavigation
          query={query}
          onQueryChange={setQuery}
          onNavigate={navigateToDocument}
          selectedRoute={document.route}
        />
        <div
          className="documentation-resize-handle documentation-resize-handle-left"
          role="separator"
          aria-label="调整文档目录宽度"
          aria-orientation="vertical"
          tabIndex={0}
          title="拖动调整宽度，双击恢复默认"
          onPointerDown={(event) => beginSidebarResize('left', event)}
          onPointerMove={continueSidebarResize}
          onPointerUp={finishSidebarResize}
          onPointerCancel={finishSidebarResize}
          onKeyDown={(event) => resizeSidebarByKeyboard('left', event)}
          onDoubleClick={() => resetSidebarWidth('left')}
        />
      </aside>

      <main ref={contentRef} className="documentation-content custom-scrollbar">
        <div className="documentation-mobile-toolbar">
          <Button icon={<MenuOutlined />} onClick={() => setMobileNavigationOpen(true)}>文档目录</Button>
          <span>{document.metadata.title}</span>
        </div>
        <div className="documentation-article-shell">
          <MarkdownDocument
            key={document.route}
            document={document}
            previous={documentIndex > 0 ? documentationDocuments[documentIndex - 1] : undefined}
            next={documentIndex >= 0 ? documentationDocuments[documentIndex + 1] : undefined}
          />
        </div>
      </main>

      <aside ref={rightSidebarRef} className="documentation-toc">
        <div
          className="documentation-resize-handle documentation-resize-handle-right"
          role="separator"
          aria-label="调整本页目录宽度"
          aria-orientation="vertical"
          tabIndex={0}
          title="拖动调整宽度，双击恢复默认"
          onPointerDown={(event) => beginSidebarResize('right', event)}
          onPointerMove={continueSidebarResize}
          onPointerUp={finishSidebarResize}
          onPointerCancel={finishSidebarResize}
          onKeyDown={(event) => resizeSidebarByKeyboard('right', event)}
          onDoubleClick={() => resetSidebarWidth('right')}
        />
        <div ref={tocScrollRef} className="documentation-toc-scroll custom-scrollbar">
          <div className="documentation-toc-heading"><Search size={15} />本页目录</div>
          {headings.length > 0 ? headings.map((heading) => (
            <a
              key={heading.id}
              ref={(element) => {
                if (element) {
                  tocLinkRefs.current.set(heading.id, element);
                } else {
                  tocLinkRefs.current.delete(heading.id);
                }
              }}
              className={activeHeadingId === heading.id ? 'is-active' : undefined}
              href={`#${encodeURIComponent(heading.id)}`}
              style={{ paddingLeft: `${12 + (heading.level - 2) * 12}px` }}
              onClick={(event) => navigateToHeading(event, heading.id)}
            >
              {heading.text}
            </a>
          )) : <span className="documentation-toc-empty">暂无二级标题</span>}
        </div>
      </aside>

      <Drawer
        title="工程文档"
        placement="left"
        width="min(88vw, 340px)"
        open={mobileNavigationOpen}
        onClose={() => setMobileNavigationOpen(false)}
      >
        <DocumentationNavigation
          query={query}
          onQueryChange={setQuery}
          onNavigate={navigateToDocument}
          selectedRoute={document.route}
        />
      </Drawer>
    </div>
  );
};

export default DocumentationPage;
// AI_GENERATE_END ------------
