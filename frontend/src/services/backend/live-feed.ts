/**
 * Live feed client for the web application: subscribes to an AppSync Events
 * channel over WebSocket. No dependencies, works in any modern browser.
 *
 * Protocol reference:
 * https://docs.aws.amazon.com/appsync/latest/eventapi/event-api-websocket-protocol.html
 */

export type LiveFeedConfig = {
  /** VITE_EVENTS_REALTIME_ENDPOINT, e.g. wss://xxx.appsync-realtime-api.eu-central-1.amazonaws.com/event/realtime */
  realtimeEndpoint: string;
  /** VITE_EVENTS_HTTP_ENDPOINT, e.g. https://xxx.appsync-api.eu-central-1.amazonaws.com/event */
  httpEndpoint: string;
  /** Returns a current Cognito ID token. Called on every (re)connect, so an expired token is replaced. */
  getIdToken: () => Promise<string>;
};

export type Subscription = {
  /** Stops receiving messages and closes the connection. */
  close: () => void;
};

type Handlers<Message> = {
  onMessage: (message: Message) => void;
  /** Called with true once subscribed and with false when the connection is lost. */
  onStatus?: (connected: boolean) => void;
  onError?: (error: unknown) => void;
};

const RECONNECT_DELAYS_MS = [1000, 2000, 5000, 10000];

/**
 * Subscribes to a channel, e.g. `/alerts/${orgId}`, and keeps the subscription
 * alive: when the connection drops it reconnects with a fresh token.
 */
export function subscribe<Message>(config: LiveFeedConfig, channel: string, handlers: Handlers<Message>): Subscription {
  let socket: WebSocket | undefined;
  let closed = false;
  let attempt = 0;
  let retry: ReturnType<typeof setTimeout> | undefined;

  async function connect() {
    try {
      // AppSync validates the token against the HTTP host, also for the WebSocket endpoint.
      const authorization = { Authorization: await config.getIdToken(), host: new URL(config.httpEndpoint).host };
      if (closed) return;
      socket = new WebSocket(config.realtimeEndpoint, [`header-${base64Url(authorization)}`, 'aws-appsync-event-ws']);

      socket.onopen = () => socket?.send(JSON.stringify({ type: 'connection_init' }));
      socket.onmessage = ({ data }) => {
        const message = JSON.parse(data as string);
        switch (message.type) {
          case 'connection_ack':
            socket?.send(JSON.stringify({ type: 'subscribe', id: crypto.randomUUID(), channel, authorization }));
            break;
          case 'subscribe_success':
            attempt = 0;
            handlers.onStatus?.(true);
            break;
          case 'data':
            // "event" is an array of JSON documents, each serialized as a string.
            for (const event of [message.event].flat()) handlers.onMessage(JSON.parse(event) as Message);
            break;
          case 'ka':
            break;
          default:
            if (String(message.type).endsWith('error')) handlers.onError?.(message);
        }
      };
      socket.onclose = () => {
        handlers.onStatus?.(false);
        scheduleReconnect();
      };
    } catch (error) {
      handlers.onError?.(error);
      scheduleReconnect();
    }
  }

  function scheduleReconnect() {
    if (closed) return;
    const delay = RECONNECT_DELAYS_MS[Math.min(attempt, RECONNECT_DELAYS_MS.length - 1)];
    attempt += 1;
    retry = setTimeout(connect, delay);
  }

  void connect();
  return {
    close() {
      closed = true;
      if (retry) clearTimeout(retry);
      socket?.close();
    },
  };
}

/** Base64 with the URL-safe alphabet and no padding, as the WebSocket subprotocol requires. */
function base64Url(value: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
