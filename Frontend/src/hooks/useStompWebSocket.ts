import { useEffect, useRef, useState } from 'react';

interface StompHookOptions<T> {
  url: string;
  fallbackUrl?: string;
  topic: string;
  onMessage: (payload: T) => void;
}

export function useStompWebSocket<T>({ url, fallbackUrl, topic, onMessage }: StompHookOptions<T>) {
  const [connected, setConnected] = useState(false);
  const [lastEventTime, setLastEventTime] = useState<string | null>(null);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    let ws: WebSocket | null = null;
    let reconnectTimer: number | undefined;
    let isUnmounted = false;
    let useFallback = false;

    const connect = () => {
      if (isUnmounted) return;
      const targetUrl = useFallback && fallbackUrl ? fallbackUrl : url;

      try {
        ws = new WebSocket(targetUrl);
      } catch {
        setConnected(false);
        useFallback = !useFallback;
        reconnectTimer = window.setTimeout(connect, 3000);
        return;
      }

      ws.onopen = () => {
        if (!ws || isUnmounted) return;
        const connectFrame = 'CONNECT\naccept-version:1.1,1.2\nheart-beat:10000,10000\n\n\0';
        ws.send(connectFrame);
      };

      ws.onmessage = (event: MessageEvent) => {
        if (isUnmounted || typeof event.data !== 'string') return;
        const raw = event.data;

        if (raw.startsWith('CONNECTED')) {
          setConnected(true);
          const subId = 'sub-' + Math.random().toString(36).substring(2, 9);
          const subscribeFrame = `SUBSCRIBE\nid:${subId}\ndestination:${topic}\n\n\0`;
          ws?.send(subscribeFrame);
          return;
        }

        if (raw.startsWith('MESSAGE')) {
          const bodyStart = raw.indexOf('\n\n');
          if (bodyStart !== -1) {
            const jsonBody = raw.substring(bodyStart + 2).replace(/\0/g, '').trim();
            if (jsonBody) {
              try {
                const parsed = JSON.parse(jsonBody) as T;
                setLastEventTime(new Date().toLocaleTimeString());
                onMessageRef.current(parsed);
              } catch (err) {
                console.error('Error parseando trama STOMP JSON:', err);
              }
            }
          }
        }
      };

      ws.onerror = () => {
        setConnected(false);
      };

      ws.onclose = () => {
        setConnected(false);
        if (!isUnmounted) {
          useFallback = !useFallback;
          reconnectTimer = window.setTimeout(connect, 3000);
        }
      };
    };

    connect();

    return () => {
      isUnmounted = true;
      if (reconnectTimer) window.clearTimeout(reconnectTimer);
      if (ws) {
        ws.close();
      }
    };
  }, [url, fallbackUrl, topic]);

  return { connected, lastEventTime };
}
