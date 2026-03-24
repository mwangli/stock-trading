/**
 * 单例 WebSocket：通知通道只维护一条连接，避免 React Strict Mode / 路由切换导致重复建连与关闭
 */

type MessageHandler = (data: Record<string, unknown>) => void;

const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const port = window.location.port === '5173' ? '8080' : window.location.port;
const WS_URL = `${protocol}//${window.location.hostname}:${port}/ws/notifications`;
const DISCONNECT_DELAY_MS = 300;

let ws: WebSocket | null = null;
let subscribers = new Set<MessageHandler>();
let disconnectTimer: ReturnType<typeof setTimeout> | null = null;

function clearDisconnectTimer() {
  if (disconnectTimer != null) {
    clearTimeout(disconnectTimer);
    disconnectTimer = null;
  }
}

function connect() {
  if (ws?.readyState === WebSocket.OPEN) return;
  if (ws != null) {
    ws.close();
    ws = null;
  }
  clearDisconnectTimer();
  const socket = new WebSocket(WS_URL);
  socket.onopen = () => {
    console.log('Notification WebSocket connected (singleton)');
  };
  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data) as Record<string, unknown>;
      subscribers.forEach((cb) => cb(data));
    } catch {
      // ignore parse error
    }
  };
  socket.onclose = () => {
    ws = null;
    console.log('Notification WebSocket closed');
  };
  socket.onerror = () => {
    socket.close();
  };
  ws = socket;
}

function disconnect() {
  clearDisconnectTimer();
  if (ws != null) {
    ws.close();
    ws = null;
  }
}

function scheduleDisconnect() {
  clearDisconnectTimer();
  disconnectTimer = setTimeout(() => {
    disconnectTimer = null;
    if (subscribers.size === 0) {
      disconnect();
    }
  }, DISCONNECT_DELAY_MS);
}

/**
 * 订阅推送消息；返回取消订阅函数。
 * 首次订阅时建连，最后一次取消订阅后延迟关闭连接（避免 Strict Mode 重挂载时反复建连）
 */
export function subscribe(handler: MessageHandler): () => void {
  subscribers.add(handler);
  connect();
  return () => {
    subscribers.delete(handler);
    if (subscribers.size === 0) {
      scheduleDisconnect();
    }
  };
}
