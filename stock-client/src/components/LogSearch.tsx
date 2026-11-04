// AI_GENERATE_START -
import React, {
  Fragment,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
  type RefObject,
} from 'react';
import { Button } from 'antd';
import { CloseOutlined, DownOutlined, SearchOutlined, UpOutlined } from '@ant-design/icons';

interface SearchMatch {
  lineIndex: number;
  start: number;
  end: number;
  matchIndex: number;
}

interface UseLogSearchOptions {
  logs: string[];
  containerRef: RefObject<HTMLDivElement | null>;
  onNavigate?: () => void;
}

export interface LogSearchController {
  activeMatchIndex: number;
  closeSearch: () => void;
  matchCount: number;
  nextMatch: () => void;
  openSearch: () => void;
  previousMatch: () => void;
  query: string;
  renderHighlightedLine: (line: string, lineIndex: number) => ReactNode;
  searchInputRef: RefObject<HTMLInputElement | null>;
  searchOpen: boolean;
  setQuery: (query: string) => void;
  handleSearchKeyDown: (event: ReactKeyboardEvent<HTMLInputElement>) => void;
}

const isEditableTarget = (target: EventTarget | null) => {
  if (!(target instanceof HTMLElement)) return false;
  return Boolean(target.closest('input, textarea, select, [contenteditable="true"]'));
};

export const useLogSearch = ({ logs, containerRef, onNavigate }: UseLogSearchOptions): LogSearchController => {
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeMatchIndex, setActiveMatchIndex] = useState(-1);
  const searchInputRef = useRef<HTMLInputElement | null>(null);

  const matchesByLine = useMemo(() => {
    const result = new Map<number, SearchMatch[]>();
    const needle = query.toLocaleLowerCase();
    if (!needle) return result;

    let matchIndex = 0;
    logs.forEach((line, lineIndex) => {
      const lowerLine = line.toLocaleLowerCase();
      let start = 0;
      while (start <= lowerLine.length - needle.length) {
        const foundAt = lowerLine.indexOf(needle, start);
        if (foundAt < 0) break;
        const lineMatches = result.get(lineIndex) ?? [];
        lineMatches.push({
          lineIndex,
          start: foundAt,
          end: foundAt + needle.length,
          matchIndex,
        });
        result.set(lineIndex, lineMatches);
        matchIndex += 1;
        start = foundAt + Math.max(needle.length, 1);
      }
    });
    return result;
  }, [logs, query]);

  const matchCount = useMemo(
    () => Array.from(matchesByLine.values()).reduce((total, matches) => total + matches.length, 0),
    [matchesByLine],
  );

  const scrollToMatch = useCallback((matchIndex: number) => {
    if (matchIndex < 0) return;
    onNavigate?.();
    window.requestAnimationFrame(() => {
      const match = containerRef.current?.querySelector<HTMLElement>(`[data-log-match-index="${matchIndex}"]`);
      match?.scrollIntoView({ block: 'center', inline: 'nearest' });
    });
  }, [containerRef, onNavigate]);

  useEffect(() => {
    if (!query || matchCount === 0) {
      setActiveMatchIndex(-1);
      return;
    }
    setActiveMatchIndex(0);
    scrollToMatch(0);
  }, [matchCount, query, scrollToMatch]);

  const moveMatch = useCallback((direction: 1 | -1) => {
    if (matchCount === 0) return;
    setActiveMatchIndex((current) => {
      const base = current < 0 ? (direction > 0 ? -1 : 0) : current;
      const next = (base + direction + matchCount) % matchCount;
      scrollToMatch(next);
      return next;
    });
  }, [matchCount, scrollToMatch]);

  const nextMatch = useCallback(() => moveMatch(1), [moveMatch]);
  const previousMatch = useCallback(() => moveMatch(-1), [moveMatch]);

  const openSearch = useCallback(() => {
    onNavigate?.();
    setSearchOpen(true);
    window.requestAnimationFrame(() => searchInputRef.current?.focus());
  }, [onNavigate]);

  const closeSearch = useCallback(() => {
    setSearchOpen(false);
    setQuery('');
    setActiveMatchIndex(-1);
  }, []);

  const handleSearchKeyDown = useCallback((event: ReactKeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeSearch();
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      if (event.shiftKey) previousMatch();
      else nextMatch();
    }
  }, [closeSearch, nextMatch, previousMatch]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === '/' && !isEditableTarget(event.target)) {
        event.preventDefault();
        openSearch();
        return;
      }
      if (event.key === 'Escape' && searchOpen) {
        event.preventDefault();
        closeSearch();
        return;
      }
      if (!query || isEditableTarget(event.target)) return;
      if (event.key === 'n') {
        event.preventDefault();
        nextMatch();
      } else if (event.key === 'N') {
        event.preventDefault();
        previousMatch();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [closeSearch, nextMatch, openSearch, previousMatch, query, searchOpen]);

  const renderHighlightedLine = useCallback((line: string, lineIndex: number): ReactNode => {
    const lineMatches = matchesByLine.get(lineIndex);
    if (!lineMatches?.length) return line;

    const fragments: ReactNode[] = [];
    let cursor = 0;
    lineMatches.forEach((match) => {
      if (match.start > cursor) fragments.push(line.slice(cursor, match.start));
      fragments.push(
        <mark
          key={`${lineIndex}-${match.start}-${match.matchIndex}`}
          className={match.matchIndex === activeMatchIndex ? 'logs-search-highlight is-active' : 'logs-search-highlight'}
          data-log-match-index={match.matchIndex}
        >
          {line.slice(match.start, match.end)}
        </mark>,
      );
      cursor = match.end;
    });
    if (cursor < line.length) fragments.push(line.slice(cursor));
    return <Fragment>{fragments}</Fragment>;
  }, [activeMatchIndex, matchesByLine]);

  return {
    activeMatchIndex,
    closeSearch,
    handleSearchKeyDown,
    matchCount,
    nextMatch,
    openSearch,
    previousMatch,
    query,
    renderHighlightedLine,
    searchInputRef,
    searchOpen,
    setQuery,
  };
};

export const LogSearchButton: React.FC<{ search: LogSearchController }> = ({ search }) => (
  <Button
    className="logs-search-toggle"
    type="text"
    icon={<SearchOutlined />}
    aria-label="搜索日志"
    aria-expanded={search.searchOpen}
    title="搜索日志（/）"
    onClick={search.openSearch}
  />
);

const LogSearchBar: React.FC<{ search: LogSearchController }> = ({ search }) => {
  if (!search.searchOpen) return null;

  const resultLabel = search.query
    ? search.matchCount > 0
      ? `${search.activeMatchIndex + 1}/${search.matchCount}`
      : '无匹配'
    : '输入关键词';

  return (
    <div className="logs-search-bar" role="search">
      <span className="logs-search-prefix" aria-hidden="true">/</span>
      <input
        ref={search.searchInputRef}
        className="logs-search-input"
        value={search.query}
        aria-label="搜索日志内容"
        placeholder="搜索日志"
        autoComplete="off"
        spellCheck={false}
        onChange={(event) => search.setQuery(event.target.value)}
        onKeyDown={search.handleSearchKeyDown}
      />
      <span className="logs-search-count" aria-live="polite">{resultLabel}</span>
      <Button type="text" icon={<UpOutlined />} aria-label="上一个匹配" title="上一个匹配（N）" disabled={search.matchCount === 0} onClick={search.previousMatch} />
      <Button type="text" icon={<DownOutlined />} aria-label="下一个匹配" title="下一个匹配（n）" disabled={search.matchCount === 0} onClick={search.nextMatch} />
      <Button type="text" icon={<CloseOutlined />} aria-label="关闭搜索" title="关闭搜索（Esc）" onClick={search.closeSearch} />
    </div>
  );
};

export default LogSearchBar;
// AI_GENERATE_END -
