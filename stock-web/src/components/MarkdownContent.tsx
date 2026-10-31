// AI_GENERATE_START -
import { useEffect, useId, useRef } from 'react'
import mermaid from 'mermaid'
import ReactMarkdown from 'react-markdown'
import remarkBreaks from 'remark-breaks'
import remarkGfm from 'remark-gfm'

mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'neutral' })

function MermaidDiagram({ source }: { source: string }) {
  const ref = useRef<HTMLDivElement>(null)
  const id = useId().replace(/:/g, '')
  useEffect(() => {
    let active = true
    mermaid.render(`diagram-${id}`, source).then(({ svg }) => {
      if (active && ref.current) ref.current.innerHTML = svg
    }).catch(() => {
      if (ref.current) ref.current.textContent = source
    })
    return () => { active = false }
  }, [id, source])
  return <div className="mermaid-diagram" ref={ref} />
}

export function MarkdownContent({ content }: { content: string }) {
  return <article className="markdown-body"><ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]} components={{
    code({ className, children, ...props }) {
      const source = String(children).replace(/\n$/, '')
      return className === 'language-mermaid' ? <MermaidDiagram source={source} /> : <code className={className} {...props}>{children}</code>
    },
  }}>{content}</ReactMarkdown></article>
}
// AI_GENERATE_END -
