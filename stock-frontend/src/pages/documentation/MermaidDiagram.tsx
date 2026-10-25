// AI_GENERATE_START --
import { useEffect, useId, useRef, useState, type FC } from 'react';
import { useAppTheme } from '../../theme/AppThemeProvider';

interface MermaidDiagramProps {
  source: string;
}

/**
 * 按需加载 Mermaid，并将受版本库管理的图表源码渲染为只读 SVG。
 */
const MermaidDiagram: FC<MermaidDiagramProps> = ({ source }) => {
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
        if (!active || !containerRef.current) {
          return;
        }
        containerRef.current.innerHTML = result.svg;
        result.bindFunctions?.(containerRef.current);
      } catch (renderError) {
        if (active) {
          setError(renderError instanceof Error ? renderError.message : '未知渲染错误');
        }
      }
    };

    void render();
    return () => {
      active = false;
      if (containerRef.current) {
        containerRef.current.replaceChildren();
      }
    };
  }, [mode, renderId, source]);

  if (error) {
    return (
      <div className="documentation-mermaid-error" role="alert">
        <strong>Mermaid 图渲染失败</strong>
        <span>{error}</span>
      </div>
    );
  }

  return <div ref={containerRef} className="documentation-mermaid" aria-label="Mermaid 图表" />;
};

export default MermaidDiagram;
// AI_GENERATE_END --
