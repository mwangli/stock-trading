// AI_GENERATE_START
import { useEffect, useId, useRef, useState, type FC } from 'react';
import { useAppTheme } from '@shared/theme/AppThemeProvider';

const MermaidDiagram: FC<{ source: string }> = ({ source }) => {
  const { mode } = useAppTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const renderId = useId().replaceAll(':', '');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const render = async () => {
      try {
        setError(null);
        const { default: mermaid } = await import('mermaid');
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict',
          theme: mode === 'dark' ? 'dark' : 'neutral',
          fontFamily: 'Inter, "Microsoft YaHei", "Segoe UI", sans-serif',
        });
        const result = await mermaid.render(`documentation-mermaid-${renderId}`, source);
        if (active && containerRef.current) {
          containerRef.current.innerHTML = result.svg;
          result.bindFunctions?.(containerRef.current);
        }
      } catch (renderError) {
        if (active) setError(renderError instanceof Error ? renderError.message : '未知渲染错误');
      }
    };
    void render();
    return () => {
      active = false;
      containerRef.current?.replaceChildren();
    };
  }, [mode, renderId, source]);

  if (error) return <div className="documentation-mermaid-error" role="alert">图表渲染失败：{error}</div>;
  return <div ref={containerRef} className="documentation-mermaid" aria-label="Mermaid 图表" />;
};

export default MermaidDiagram;
// AI_GENERATE_END
