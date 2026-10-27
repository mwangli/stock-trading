// AI_GENERATE_START ---
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = path.resolve(frontendRoot, '..');
const docsRoot = path.join(repositoryRoot, 'documents');
const allowedStatuses = new Set([
  'PROPOSED',
  'SOURCE_IMPLEMENTED',
  'COMPILED',
  'RUNTIME_VERIFIED',
  'PRODUCTION_VERIFIED',
  'SUPERSEDED',
]);

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(entryPath) : [entryPath];
  });
}

function parseFrontMatter(content) {
  const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
  if (!match) {
    return null;
  }
  const values = {};
  let activeList = null;
  for (const line of match[1].split(/\r?\n/)) {
    const listItem = line.match(/^\s{2}-\s+(.+)$/);
    if (listItem && activeList) {
      values[activeList].push(listItem[1].trim().replace(/^['"]|['"]$/g, ''));
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
      values[key] = rawValue.trim().replace(/^['"]|['"]$/g, '');
      activeList = null;
    }
  }
  return values;
}

const errors = [];
const documentIds = new Map();
const documentSlugs = new Map();
for (const file of walk(docsRoot).filter((item) => /\.mdx?$/.test(item))) {
  const relativeFile = path.relative(repositoryRoot, file).replaceAll('\\', '/');
  const content = fs.readFileSync(file, 'utf8');
  const metadata = parseFrontMatter(content);
  if (!metadata) {
    errors.push(`${relativeFile}: 缺少首行 Front Matter`);
    continue;
  }
  for (const required of ['id', 'title', 'owner', 'type', 'status', 'last_verified', 'slug']) {
    if (!metadata[required]) {
      errors.push(`${relativeFile}: 缺少 ${required}`);
    }
  }
  if (metadata.status && !allowedStatuses.has(metadata.status)) {
    errors.push(`${relativeFile}: 非法状态 ${metadata.status}`);
  }
  if (metadata.id) {
    if (documentIds.has(metadata.id)) {
      errors.push(`${relativeFile}: 文档 id 与 ${documentIds.get(metadata.id)} 重复`);
    }
    documentIds.set(metadata.id, relativeFile);
  }
  if (metadata.slug) {
    const normalizedSlug = `/${metadata.slug.replace(/^\/+|\/+$/g, '')}`;
    if (documentSlugs.has(normalizedSlug)) {
      errors.push(`${relativeFile}: 文档 slug 与 ${documentSlugs.get(normalizedSlug)} 重复`);
    }
    documentSlugs.set(normalizedSlug, relativeFile);
  }
  for (const sourcePath of metadata.source_paths ?? []) {
    const absoluteSource = path.resolve(repositoryRoot, sourcePath);
    if (!absoluteSource.startsWith(repositoryRoot + path.sep) || !fs.existsSync(absoluteSource)) {
      errors.push(`${relativeFile}: 关联源码不存在 ${sourcePath}`);
    }
  }
  for (const match of content.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const rawTarget = match[1].trim();
    if (rawTarget.startsWith('pathname://')) {
      errors.push(`${relativeFile}: 禁止使用 pathname://，请改用标准相对文件链接 ${rawTarget}`);
      continue;
    }
    if (/^(?:https?:|mailto:|#|\/)/.test(rawTarget)) {
      continue;
    }
    const target = rawTarget.split('#', 1)[0].split('?', 1)[0];
    if (!target || !path.extname(target)) {
      continue;
    }
    const absoluteTarget = path.resolve(path.dirname(file), target);
    const isDocumentLink = /\.mdx?$/.test(target) && absoluteTarget.startsWith(docsRoot + path.sep);
    if (!absoluteTarget.startsWith(repositoryRoot + path.sep) || !fs.existsSync(absoluteTarget)) {
      const linkType = isDocumentLink ? '站内文档' : '关联文件';
      errors.push(`${relativeFile}: ${linkType}链接不存在 ${rawTarget}`);
    }
  }
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`文档静态检查通过，共检查 ${documentIds.size} 篇文档。`);
}
// AI_GENERATE_END ---
