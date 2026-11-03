// AI_GENERATE_START -
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const sourceRoot = join(projectRoot, 'src');
const platformRoots = {
  desktop: join(sourceRoot, 'desktop'),
  mobile: join(sourceRoot, 'mobile'),
};
const sourceExtensions = new Set(['.ts', '.tsx', '.js', '.jsx', '.css']);

const listFiles = (directory) => readdirSync(directory).flatMap((name) => {
  const path = join(directory, name);
  return statSync(path).isDirectory() ? listFiles(path) : [path];
});

const violations = [];

for (const [platform, root] of Object.entries(platformRoots)) {
  const forbiddenPlatform = platform === 'desktop' ? 'mobile' : 'desktop';
  for (const file of listFiles(root)) {
    if (!sourceExtensions.has(extname(file))) continue;
    const source = readFileSync(file, 'utf8');
    const forbiddenPatterns = [
      `@${forbiddenPlatform}/`,
      `/src/${forbiddenPlatform}/`,
      `../${forbiddenPlatform}/`,
      `../../${forbiddenPlatform}/`,
      `../../../${forbiddenPlatform}/`,
    ];
    if (forbiddenPatterns.some((pattern) => source.includes(pattern))) {
      violations.push(`${relative(projectRoot, file)} imports ${forbiddenPlatform} code`);
    }
  }
}

if (violations.length > 0) {
  console.error(violations.join('\n'));
  process.exitCode = 1;
} else {
  console.log('Desktop and mobile source boundaries are isolated.');
}
// AI_GENERATE_END -
