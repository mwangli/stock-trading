// AI_GENERATE_START --
const path = require('node:path');

const REPOSITORY_ROOT = path.resolve(__dirname, '../../..');
const DOCUMENTS_ROOT = path.join(REPOSITORY_ROOT, 'documents');
const GITHUB_SOURCE_BASE = 'https://github.com/mwangli/stock-trading/blob/master/';

function isInside(parentPath, childPath) {
  const relativePath = path.relative(parentPath, childPath);
  return relativePath === ''
    || (!relativePath.startsWith(`..${path.sep}`) && relativePath !== '..' && !path.isAbsolute(relativePath));
}

function rewriteUrl(url, sourceFilePath) {
  if (!url || /^(?:[a-z][a-z\d+.-]*:|#|\/)/i.test(url)) {
    return url;
  }

  const match = url.match(/^([^?#]+)(.*)$/);
  if (!match) {
    return url;
  }

  const [, targetPath, suffix] = match;
  const absoluteTarget = path.resolve(path.dirname(sourceFilePath), decodeURIComponent(targetPath));
  if (!isInside(REPOSITORY_ROOT, absoluteTarget)
      || isInside(DOCUMENTS_ROOT, absoluteTarget)) {
    return url;
  }

  const repositoryPath = path.relative(REPOSITORY_ROOT, absoluteTarget).split(path.sep).join('/');
  return `${GITHUB_SOURCE_BASE}${encodeURI(repositoryPath)}${suffix}`;
}

module.exports = function rewriteRepositoryLinks() {
  return async (tree, file) => {
    const {visit} = await import('unist-util-visit');
    visit(tree, ['link', 'definition'], (node) => {
      node.url = rewriteUrl(node.url, file.path);
    });
  };
};
// AI_GENERATE_END --
