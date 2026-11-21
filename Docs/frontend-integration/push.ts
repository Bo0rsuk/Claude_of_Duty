/**
 * Push notifications for the web application, including when it is installed
 * to the home screen. The service worker in sw.js shows what arrives.
 *
 * Works in Chrome, Edge and Firefox on desktop and Android. On iPhone and iPad
 * it works only after the site was added to the home screen (iOS 16.4+).
 */

type Api = <T>(method: string, path: string, body?: unknown) => Promise<T>;

export function pushSupported(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

/**
 * Asks for permission and registers this device. Call it from a click handler:
 * browsers refuse a permission prompt that the user did not trigger.
 *
 * @returns false when the browser cannot do push or the user declined
 */
export async function enablePush(api: Api): Promise<boolean> {
  if (!pushSupported() || (await Notification.requestPermission()) !== 'granted') return false;

  const registration = await navigator.serviceWorker.register('/sw.js');
  await navigator.serviceWorker.ready;
  const { publicKey } = await api<{ publicKey: string }>('GET', '/push/public-key');
  const subscription = await registration.pushManager.subscribe({
    // Browsers only allow push that results in a visible notification.
    userVisibleOnly: true,
    applicationServerKey: base64UrlToBytes(publicKey),
  });
  await api('POST', '/me/push-subscriptions', subscription.toJSON());
  return true;
}

/** Stops push on this device and tells the server to forget it. */
export async function disablePush(api: Api): Promise<void> {
  const registration = await navigator.serviceWorker.getRegistration();
  const subscription = await registration?.pushManager.getSubscription();
  if (!subscription) return;
  const { items } = await api<{ items: { subscriptionId: string; endpoint: string }[] }>('GET', '/me/push-subscriptions');
  const mine = items.find((item) => item.endpoint === subscription.endpoint);
  await subscription.unsubscribe();
  if (mine) await api('DELETE', `/me/push-subscriptions/${mine.subscriptionId}`);
}

function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
  return Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
}
