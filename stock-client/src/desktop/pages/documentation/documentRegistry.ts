// AI_GENERATE_START
export interface DocumentationFrontMatter {
  id: string;
  title: string;
  sidebarPosition: number;
  owner?: string;
  type?: string;
  status?: string;
  lastVerified?: string;
  slug?: string;
  sourcePaths: string[];
}

export interface DocumentationEntry {
  sourcePath: string;
  route: string;
  metadata: DocumentationFrontMatter;
  body: string;
  searchText: string;
}

export interface DocumentationHeading {
  id: string;
  level: number;
  sourceLine: number;
  text: string;
}

const DOCUMENT_MARKER = '/documents/';
const GITHUB_SOURCE_BASE = 'https://github.com/mwangli/stock-trading/blob/master/';
const rawDocuments = import.meta.glob<string>('../../../../../documents/**/*.md', {
  eager: true,
  query: '?raw',
  import: 'default',
});

const directoryOrder = [
  'documents/index.md',
  'documents/01-system/',
  'documents/02-business/',
  'documents/03-code/',
  'documents/04-operations/',
  'documents/05-decisions/',
  'documents/06-history/',
];

const normalizeRoute = (route: string) => route.replace(/\/+$/, '') || '/docs';
const stripQuotes = (value: string) => value.trim().replace(/^['"]|['"]$/g, '');
const normalizeSearchText = (value: string) => value
  .normalize('NFKC')
  .toLocaleLowerCase('zh-CN')
  .replace(/\s+/g, ' ')
  .trim();

const parseFrontMatter = (content: string): { metadata: DocumentationFrontMatter; body: string } => {
  const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  if (!match) throw new Error('Markdown 文档缺少 Front Matter');

  const values: Record<string, string | string[]> = {};
  let activeList: string | null = null;
  for (const line of match[1].split(/\r?\n/)) {
    const listItem = line.match(/^\s{2}-\s+(.+)$/);
    if (listItem && activeList) {
      (values[activeList] as string[]).push(stripQuotes(listItem[1]));
      continue;
    }
    const property = line.match(/^([a-zA-Z_]+):\s*(.*)$/);
    if (!property) {
      activeList = null;
      continue;
    }
    const [, key, rawValue] = property;
    if (rawValue === '') {
      values[key] = [];
      activeList = key;
    } else {
      values[key] = stripQuotes(rawValue);
      activeList = null;
    }
  }

  const id = String(values.id ?? '').trim();
  const title = String(values.title ?? '').trim();
  if (!id || !title) throw new Error('Markdown 文档缺少 id 或 title');
  return {
    metadata: {
      id,
      title,
      sidebarPosition: Number(values.sidebar_position ?? Number.MAX_SAFE_INTEGER),
      owner: String(values.owner ?? '').trim() || undefined,
      type: String(values.type ?? '').trim() || undefined,
      status: String(values.status ?? '').trim() || undefined,
      lastVerified: String(values.last_verified ?? '').trim() || undefined,
      slug: String(values.slug ?? '').trim() || undefined,
      sourcePaths: Array.isArray(values.source_paths) ? values.source_paths : [],
    },
    body: content.slice(match[0].length).replace(/^<DocMetadata\s*\/>\s*\r?\n?/m, ''),
  };
};

const sourcePathFromGlobKey = (key: string) => {
  const normalized = key.replaceAll('\\', '/');
  const markerIndex = normalized.lastIndexOf(DOCUMENT_MARKER);
  if (markerIndex < 0) throw new Error(`无法识别文档路径: ${key}`);
  return `documents/${normalized.slice(markerIndex + DOCUMENT_MARKER.length)}`;
};

const routeFromSource = (sourcePath: string, slug?: string) => {
  if (slug) return normalizeRoute(`/docs/${slug.replace(/^\/+|\/+$/g, '')}`);
  const relative = sourcePath.slice('documents/'.length).replace(/\.md$/i, '');
  return normalizeRoute(`/docs/${relative === 'index' ? '' : relative.replace(/\/index$/i, '')}`);
};

const stripMarkdownForSearch = (value: string) => value
  .replace(/```[\s\S]*?```/g, ' ')
  .replace(/`([^`]+)`/g, '$1')
  .replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')
  .replace(/<[^>]+>/g, ' ')
  .replace(/[#>*_|~-]/g, ' ');

const navigationRank = (sourcePath: string) => {
  const exact = directoryOrder.indexOf(sourcePath);
  if (exact >= 0) return exact;
  const prefix = directoryOrder.findIndex((entry) => entry.endsWith('/') && sourcePath.startsWith(entry));
  return prefix >= 0 ? prefix : directoryOrder.length;
};

export const documentationDocuments: DocumentationEntry[] = Object.entries(rawDocuments)
  .map(([key, content]) => {
    const sourcePath = sourcePathFromGlobKey(key);
    const { metadata, body } = parseFrontMatter(content);
    return {
      sourcePath,
      route: routeFromSource(sourcePath, metadata.slug),
      metadata,
      body,
      searchText: normalizeSearchText(`${metadata.title} ${metadata.owner ?? ''} ${stripMarkdownForSearch(body)}`),
    };
  })
  .sort((left, right) => navigationRank(left.sourcePath) - navigationRank(right.sourcePath)
    || left.metadata.sidebarPosition - right.metadata.sidebarPosition
    || left.metadata.title.localeCompare(right.metadata.title, 'zh-CN'));

const documentsByRoute = new Map(documentationDocuments.map((document) => [normalizeRoute(document.route), document]));
const documentsBySource = new Map(documentationDocuments.map((document) => [document.sourcePath, document]));

export const findDocumentation = (pathname: string) => documentsByRoute.get(normalizeRoute(decodeURI(pathname)));

export const searchDocumentation = (query: string) => {
  const normalized = normalizeSearchText(query);
  return normalized ? documentationDocuments.filter((document) => document.searchText.includes(normalized)) : [];
};

const normalizeRepositoryPath = (path: string) => {
  const segments: string[] = [];
  for (const segment of path.split('/')) {
    if (!segment || segment === '.') continue;
    if (segment === '..') segments.pop();
    else segments.push(segment);
  }
  return segments.join('/');
};

export const resolveDocumentationLink = (document: DocumentationEntry, rawHref?: string) => {
  if (!rawHref || rawHref.startsWith('#')) return { href: rawHref ?? '', internal: false };
  if (/^[a-z][a-z\d+.-]*:/i.test(rawHref) || rawHref.startsWith('//')) return { href: rawHref, internal: false };
  if (rawHref === '/docs' || rawHref.startsWith('/docs/')) return { href: rawHref, internal: true };
  if (rawHref.startsWith('/')) return { href: rawHref, internal: true };

  const match = rawHref.match(/^([^?#]+)(.*)$/);
  if (!match) return { href: rawHref, internal: false };
  const [, targetPath, suffix] = match;
  const directory = document.sourcePath.slice(0, document.sourcePath.lastIndexOf('/'));
  const repositoryPath = normalizeRepositoryPath(`${directory}/${decodeURIComponent(targetPath)}`);
  const target = documentsBySource.get(repositoryPath);
  return target
    ? { href: `${target.route}${suffix}`, internal: true }
    : { href: `${GITHUB_SOURCE_BASE}${encodeURI(repositoryPath)}${suffix}`, internal: false };
};

export const createHeadingSlugger = () => {
  const counts = new Map<string, number>();
  return (value: string) => {
    const base = value.normalize('NFKC').trim().toLocaleLowerCase('zh-CN')
      .replace(/[^\p{Letter}\p{Number}\s-]/gu, '')
      .replace(/\s+/g, '-')
      .replace(/-+/g, '-') || 'section';
    const count = counts.get(base) ?? 0;
    counts.set(base, count + 1);
    return count === 0 ? base : `${base}-${count}`;
  };
};

export const extractDocumentationHeadings = (body: string): DocumentationHeading[] => {
  const headings: DocumentationHeading[] = [];
  const slug = createHeadingSlugger();
  let fenced = false;
  for (const [lineIndex, line] of body.split(/\r?\n/).entries()) {
    if (/^\s*```/.test(line)) {
      fenced = !fenced;
      continue;
    }
    if (fenced) continue;
    const match = line.match(/^\s{0,3}(#{1,4})\s+(.+?)\s*#*\s*$/);
    if (!match) continue;
    const text = match[2].replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').replace(/[`*_~]/g, '').trim();
    headings.push({ id: slug(text), level: match[1].length, sourceLine: lineIndex + 1, text });
  }
  return headings;
};
// AI_GENERATE_END
