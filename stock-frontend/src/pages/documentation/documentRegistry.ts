// AI_GENERATE_START ----
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
  directory: string;
  fileName: string;
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

export interface ResolvedDocumentationLink {
  href: string;
  internal: boolean;
}

const GITHUB_SOURCE_BASE = 'https://github.com/mwangli/stock-trading/blob/master/';
const DOCUMENT_MARKER = '/documents/';
const rawDocuments = import.meta.glob<string>('../../../../documents/**/*.md', {
  eager: true,
  query: '?raw',
  import: 'default',
});

const navigationPrefixes = [
  'documents/工程文档工作台.md',
  'documents/system/',
  'documents/backend/后端总览.md',
  'documents/backend/market/',
  'documents/backend/model/模型模块.md',
  'documents/backend/model/designs/',
  'documents/backend/decision/',
  'documents/backend/execution/',
  'documents/backend/schedule/',
  'documents/backend/platform/',
  'documents/agent/智能代理总览.md',
  'documents/agent/decision/',
  'documents/agent/integration/',
  'documents/agent/interaction/',
  'documents/agent/knowledge/',
  'documents/agent/runtime/',
  'documents/agent/shared/',
  'documents/agent/tooling/',
  'documents/agent/tradeplan/',
  'documents/frontend/',
];

const normalizeRoute = (route: string): string => {
  const normalized = route.replace(/\/+$/, '');
  return normalized || '/docs';
};

const normalizeSearchText = (value: string): string => (
  value.normalize('NFKC').toLocaleLowerCase('zh-CN').replace(/\s+/g, ' ').trim()
);

const stripQuotes = (value: string): string => value.trim().replace(/^['"]|['"]$/g, '');

const parseFrontMatter = (content: string): { metadata: DocumentationFrontMatter; body: string } => {
  const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  if (!match) {
    throw new Error('Markdown 文档缺少 Front Matter');
  }

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

  const title = String(values.title ?? '').trim();
  const id = String(values.id ?? '').trim();
  if (!title || !id) {
    throw new Error('Markdown 文档缺少 id 或 title');
  }

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

const sourcePathFromGlobKey = (key: string): string => {
  const normalized = key.replaceAll('\\', '/');
  const markerIndex = normalized.lastIndexOf(DOCUMENT_MARKER);
  if (markerIndex < 0) {
    throw new Error(`无法识别文档路径: ${key}`);
  }
  return `documents/${normalized.slice(markerIndex + DOCUMENT_MARKER.length)}`;
};

const routeFromSource = (sourcePath: string, slug?: string): string => {
  if (slug) {
    return normalizeRoute(`/docs/${slug.replace(/^\/+|\/+$/g, '')}`);
  }

  const relativePath = sourcePath.slice('documents/'.length).replace(/\.md$/i, '');
  const routePath = relativePath === 'index' ? '' : relativePath.replace(/\/index$/i, '');
  return normalizeRoute(`/docs/${routePath}`);
};

const stripMarkdownForSearch = (value: string): string => value
  .replace(/```[\s\S]*?```/g, ' ')
  .replace(/`([^`]+)`/g, '$1')
  .replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')
  .replace(/<[^>]+>/g, ' ')
  .replace(/[#>*_|~-]/g, ' ');

const navigationRank = (sourcePath: string): number => {
  const exactIndex = navigationPrefixes.indexOf(sourcePath);
  if (exactIndex >= 0) {
    return exactIndex;
  }
  const prefixIndex = navigationPrefixes.findIndex((prefix) => prefix.endsWith('/') && sourcePath.startsWith(prefix));
  return prefixIndex >= 0 ? prefixIndex : navigationPrefixes.length;
};

export const documentationDocuments: DocumentationEntry[] = Object.entries(rawDocuments)
  .map(([key, content]) => {
    const sourcePath = sourcePathFromGlobKey(key);
    const { metadata, body } = parseFrontMatter(content);
    const lastSlash = sourcePath.lastIndexOf('/');
    const entry: DocumentationEntry = {
      sourcePath,
      route: routeFromSource(sourcePath, metadata.slug),
      directory: sourcePath.slice(0, lastSlash),
      fileName: sourcePath.slice(lastSlash + 1),
      metadata,
      body,
      searchText: '',
    };
    entry.searchText = normalizeSearchText([
      metadata.title,
      metadata.owner,
      metadata.type,
      metadata.status,
      stripMarkdownForSearch(body),
    ].filter(Boolean).join(' '));
    return entry;
  })
  .sort((left, right) => (
    navigationRank(left.sourcePath) - navigationRank(right.sourcePath)
    || left.metadata.sidebarPosition - right.metadata.sidebarPosition
    || left.metadata.title.localeCompare(right.metadata.title, 'zh-CN')
  ));

export const documentationByRoute = new Map(
  documentationDocuments.map((document) => [normalizeRoute(document.route), document]),
);

export const documentationBySourcePath = new Map(
  documentationDocuments.map((document) => [document.sourcePath, document]),
);

export const findDocumentation = (pathname: string): DocumentationEntry | undefined => (
  documentationByRoute.get(normalizeRoute(decodeURI(pathname)))
);

export const searchDocumentation = (query: string): DocumentationEntry[] => {
  const normalizedQuery = normalizeSearchText(query);
  if (!normalizedQuery) {
    return [];
  }
  return documentationDocuments.filter((document) => document.searchText.includes(normalizedQuery));
};

const normalizeRepositoryPath = (path: string): string => {
  const segments: string[] = [];
  for (const segment of path.split('/')) {
    if (!segment || segment === '.') {
      continue;
    }
    if (segment === '..') {
      segments.pop();
      continue;
    }
    segments.push(segment);
  }
  return segments.join('/');
};

export const resolveDocumentationLink = (
  document: DocumentationEntry,
  rawHref?: string,
): ResolvedDocumentationLink => {
  if (!rawHref || rawHref.startsWith('#')) {
    return { href: rawHref ?? '', internal: false };
  }
  if (/^[a-z][a-z\d+.-]*:/i.test(rawHref) || rawHref.startsWith('//')) {
    return { href: rawHref, internal: false };
  }
  if (rawHref === '/docs' || rawHref.startsWith('/docs/')) {
    return { href: rawHref, internal: true };
  }
  if (rawHref.startsWith('/')) {
    return { href: rawHref, internal: true };
  }

  const match = rawHref.match(/^([^?#]+)(.*)$/);
  if (!match) {
    return { href: rawHref, internal: false };
  }

  const [, targetPath, suffix] = match;
  const baseDirectory = document.sourcePath.slice(0, document.sourcePath.lastIndexOf('/'));
  const repositoryPath = normalizeRepositoryPath(`${baseDirectory}/${decodeURIComponent(targetPath)}`);
  const targetDocument = documentationBySourcePath.get(repositoryPath);
  if (targetDocument) {
    return { href: `${targetDocument.route}${suffix}`, internal: true };
  }
  return { href: `${GITHUB_SOURCE_BASE}${encodeURI(repositoryPath)}${suffix}`, internal: false };
};

export const createHeadingSlugger = (): ((value: string) => string) => {
  const counts = new Map<string, number>();
  return (value: string) => {
    const base = value
      .normalize('NFKC')
      .trim()
      .toLocaleLowerCase('zh-CN')
      .replace(/[^\p{Letter}\p{Number}\s-]/gu, '')
      .replace(/\s+/g, '-')
      .replace(/-+/g, '-') || 'section';
    const count = counts.get(base) ?? 0;
    counts.set(base, count + 1);
    return count === 0 ? base : `${base}-${count}`;
  };
};

const cleanHeadingText = (value: string): string => value
  .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
  .replace(/[`*_~]/g, '')
  .replace(/<[^>]+>/g, '')
  .trim();

export const extractDocumentationHeadings = (body: string): DocumentationHeading[] => {
  const headings: DocumentationHeading[] = [];
  const slug = createHeadingSlugger();
  let fenced = false;
  for (const [lineIndex, line] of body.split(/\r?\n/).entries()) {
    if (/^\s*```/.test(line)) {
      fenced = !fenced;
      continue;
    }
    if (fenced) {
      continue;
    }
    const match = line.match(/^\s{0,3}(#{1,4})\s+(.+?)\s*#*\s*$/);
    if (!match) {
      continue;
    }
    const text = cleanHeadingText(match[2]);
    headings.push({
      id: slug(text),
      level: match[1].length,
      sourceLine: lineIndex + 1,
      text,
    });
  }
  return headings;
};
// AI_GENERATE_END ----
