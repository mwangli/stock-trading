// AI_GENERATE_START ---
import {
  isValidElement,
  useMemo,
  useState,
  type ComponentPropsWithoutRef,
  type FC,
  type ReactNode,
} from 'react';
import { Check, Copy, ExternalLink, FileClock, GitBranch, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router-dom';
import ReactMarkdown, { type Components, type ExtraProps } from 'react-markdown';
import { Highlight, themes, type Language } from 'prism-react-renderer';
import remarkBreaks from 'remark-breaks';
import remarkGfm from 'remark-gfm';
import { useAppTheme } from '../../theme/AppThemeProvider';
import MermaidDiagram from './MermaidDiagram';
import {
  extractDocumentationHeadings,
  resolveDocumentationLink,
  type DocumentationEntry,
} from './documentRegistry';

interface MarkdownDocumentProps {
  document: DocumentationEntry;
  previous?: DocumentationEntry;
  next?: DocumentationEntry;
}

type MarkdownHeadingProps = ComponentPropsWithoutRef<'h1'> & ExtraProps;

const GITHUB_SOURCE_BASE = 'https://github.com/mwangli/stock-trading/blob/master/';
const supportedLanguages = new Set<Language>([
  'bash', 'css', 'java', 'javascript', 'json', 'jsx', 'markup', 'sql', 'tsx', 'typescript', 'yaml',
]);

const childrenToText = (children: ReactNode): string => {
  if (typeof children === 'string' || typeof children === 'number') {
    return String(children);
  }
  if (Array.isArray(children)) {
    return children.map(childrenToText).join('');
  }
  if (isValidElement<{ children?: ReactNode }>(children)) {
    return childrenToText(children.props.children);
  }
  return '';
};

const CodeBlock: FC<{ code: string; language: string; dark: boolean }> = ({ code, language, dark }) => {
  const [copied, setCopied] = useState(false);
  const prismLanguage = supportedLanguages.has(language as Language) ? language as Language : 'markup';
  const copy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1_500);
  };

  return (
    <div className="documentation-code-block">
      <div className="documentation-code-toolbar">
        <span>{language || 'text'}</span>
        <button type="button" onClick={() => void copy()} title="复制代码" aria-label="复制代码">
          {copied ? <Check size={15} /> : <Copy size={15} />}
        </button>
      </div>
      <Highlight theme={dark ? themes.vsDark : themes.github} code={code} language={prismLanguage}>
        {({ className, style, tokens, getLineProps, getTokenProps }) => (
          <pre className={className} style={style}>
            {tokens.map((line, lineIndex) => (
              <div key={lineIndex} {...getLineProps({ line })}>
                {line.map((token, tokenIndex) => (
                  <span key={tokenIndex} {...getTokenProps({ token })} />
                ))}
              </div>
            ))}
          </pre>
        )}
      </Highlight>
    </div>
  );
};

/**
 * 渲染工程 Markdown、证据元数据、源码入口和相邻文档导航。
 */
const MarkdownDocument: FC<MarkdownDocumentProps> = ({ document, previous, next }) => {
  const { mode } = useAppTheme();
  const headings = useMemo(() => extractDocumentationHeadings(document.body), [document.body]);
  const components = useMemo<Components>(() => {
    const headingIdsBySourceLine = new Map(headings.map((item) => [item.sourceLine, item.id]));
    const heading = (level: 1 | 2 | 3 | 4) => ({ node, children, ...props }: MarkdownHeadingProps) => {
      const text = childrenToText(children);
      const sourceLine = node?.position?.start.line;
      const id = sourceLine
        ? headingIdsBySourceLine.get(sourceLine)
        : headings.find((item) => item.level === level && item.text === text)?.id;
      const Heading = `h${level}` as 'h1' | 'h2' | 'h3' | 'h4';
      return <Heading {...props} id={id}>{children}</Heading>;
    };

    return {
      h1: heading(1),
      h2: heading(2),
      h3: heading(3),
      h4: heading(4),
      a: ({ href, children }) => {
        const resolved = resolveDocumentationLink(document, href);
        if (resolved.internal) {
          return <Link to={resolved.href}>{children}</Link>;
        }
        const external = /^https?:\/\//i.test(resolved.href);
        return (
          <a href={resolved.href} target={external ? '_blank' : undefined} rel={external ? 'noreferrer' : undefined}>
            {children}
            {external && <ExternalLink size={13} aria-hidden="true" />}
          </a>
        );
      },
      pre: ({ children }) => <>{children}</>,
      code: ({ className, children }) => {
        const code = childrenToText(children).replace(/\n$/, '');
        const language = className?.match(/language-([^\s]+)/)?.[1] ?? '';
        if (language === 'mermaid') {
          return <MermaidDiagram source={code} />;
        }
        if (language || code.includes('\n')) {
          return <CodeBlock code={code} language={language} dark={mode === 'dark'} />;
        }
        return <code>{children}</code>;
      },
      table: ({ children }) => <div className="documentation-table-wrap"><table>{children}</table></div>,
    };
  }, [document, headings, mode]);

  return (
    <>
      <header className="documentation-metadata">
        <div className="documentation-metadata-summary">
          {document.metadata.owner && <span><GitBranch size={15} />领域：{document.metadata.owner}</span>}
          {document.metadata.type && <span>类型：{document.metadata.type}</span>}
          {document.metadata.status && <span><ShieldCheck size={15} />状态：{document.metadata.status}</span>}
          {document.metadata.lastVerified && <span><FileClock size={15} />核对：{document.metadata.lastVerified}</span>}
        </div>
        {document.metadata.sourcePaths.length > 0 && (
          <div className="documentation-source-links">
            <strong>关联源码</strong>
            {document.metadata.sourcePaths.map((sourcePath) => (
              <a key={sourcePath} href={`${GITHUB_SOURCE_BASE}${sourcePath}`} target="_blank" rel="noreferrer">
                {sourcePath.split('/').at(-1)}
                <ExternalLink size={13} />
              </a>
            ))}
          </div>
        )}
      </header>

      <article className="documentation-markdown">
        <ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]} skipHtml components={components}>
          {document.body}
        </ReactMarkdown>
      </article>

      <nav className="documentation-neighbor-navigation" aria-label="相邻文档">
        {previous ? (
          <Link to={previous.route} className="documentation-neighbor documentation-neighbor-previous">
            <span>上一篇</span>
            <strong>{previous.metadata.title}</strong>
          </Link>
        ) : <span />}
        {next && (
          <Link to={next.route} className="documentation-neighbor documentation-neighbor-next">
            <span>下一篇</span>
            <strong>{next.metadata.title}</strong>
          </Link>
        )}
      </nav>

      <aside className="documentation-mobile-toc" aria-label="本页目录">
        {headings.filter((item) => item.level > 1).map((item) => (
          <a key={item.id} href={`#${item.id}`} style={{ paddingLeft: `${(item.level - 2) * 12}px` }}>
            {item.text}
          </a>
        ))}
      </aside>
    </>
  );
};

export default MarkdownDocument;
// AI_GENERATE_END ---
