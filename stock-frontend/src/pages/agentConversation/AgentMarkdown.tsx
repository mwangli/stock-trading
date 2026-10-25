// AI_GENERATE_START--
import type { FC } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkBreaks from 'remark-breaks';
import remarkGfm from 'remark-gfm';

interface AgentMarkdownProps {
  content: string;
  compact?: boolean;
}

const AgentMarkdown: FC<AgentMarkdownProps> = ({ content, compact = false }) => (
  <div className={`agent-markdown${compact ? ' agent-markdown-compact' : ''}`}>
    <ReactMarkdown
      remarkPlugins={[remarkGfm, remarkBreaks]}
      skipHtml
      components={{
        a: ({ href, children }) => {
          const external = Boolean(href && /^https?:\/\//i.test(href));
          return (
            <a href={href} target={external ? '_blank' : undefined} rel={external ? 'noreferrer' : undefined}>
              {children}
            </a>
          );
        },
      }}
    >
      {content}
    </ReactMarkdown>
  </div>
);

export default AgentMarkdown;
// AI_GENERATE_END--
