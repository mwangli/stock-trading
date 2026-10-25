// AI_GENERATE_START --
import React from 'react';
import {useDoc} from '@docusaurus/plugin-content-docs/client';
import {CheckCircle2, CircleDot, Code2, ExternalLink, FileClock, ShieldAlert} from 'lucide-react';
import clsx from 'clsx';
import styles from './styles.module.css';

type DocumentationFrontMatter = {
  owner?: string;
  type?: string;
  status?: string;
  last_verified?: string;
  source_paths?: string[];
};

const SOURCE_BASE_URL = 'https://github.com/mwangli/stock-trading/blob/master/';

const statusIcons: Record<string, React.ComponentType<{size?: number}>> = {
  PROPOSED: CircleDot,
  SOURCE_IMPLEMENTED: Code2,
  COMPILED: CheckCircle2,
  RUNTIME_VERIFIED: CheckCircle2,
  PRODUCTION_VERIFIED: CheckCircle2,
  SUPERSEDED: ShieldAlert,
};

export default function DocMetadata(): React.JSX.Element {
  const {metadata} = useDoc();
  const frontMatter = metadata.frontMatter as DocumentationFrontMatter;
  const owner = frontMatter.owner ?? '未指定';
  const type = frontMatter.type ?? '未指定';
  const status = frontMatter.status ?? 'PROPOSED';
  const lastVerified = frontMatter.last_verified ?? '未核对';
  const sources = frontMatter.source_paths ?? [];
  const StatusIcon = statusIcons[status] ?? CircleDot;

  return (
    <section className={styles.metadata} aria-label="文档元数据">
      <div className={styles.summary}>
        <span className={styles.item}><Code2 size={16} />领域：{owner}</span>
        <span className={styles.item}>类型：{type}</span>
        <span className={clsx(styles.item, styles.status)}>
          <StatusIcon size={16} />状态：{status}
        </span>
        <span className={styles.item}><FileClock size={16} />核对：{lastVerified}</span>
      </div>
      {sources.length > 0 && (
        <div className={styles.sources}>
          <strong>关联源码</strong>
          <div className={styles.sourceLinks}>
            {sources.map((sourcePath) => (
              <a
                key={sourcePath}
                href={`${SOURCE_BASE_URL}${sourcePath}`}
                target="_blank"
                rel="noreferrer"
              >
                {sourcePath.split('/').at(-1)}
                <ExternalLink size={14} />
              </a>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
// AI_GENERATE_END --
