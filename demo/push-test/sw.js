// Service worker for push notifications. Serve it from the site root as /sw.js.
// The server sends: { title, body, alertId, severity, url }.

self.addEventListener('push', (event) => {
  const alert = event.data ? event.data.json() : {};
  event.waitUntil(
    self.registration.showNotification(alert.title || 'New alert', {
      body: alert.body,
      // One notification per alert: a repeat replaces the earlier one instead of stacking.
      tag: alert.alertId,
      data: { url: alert.url },
      // A critical alert stays on screen until someone reacts.
      requireInteraction: alert.severity === 'CRITICAL',
    }),
  );
});

// Clicking the notification opens the alert, in the already open app when there is one.
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = event.notification.data && event.notification.data.url;
  if (!url) return;
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows.find((client) => new URL(client.url).origin === new URL(url).origin);
      return open ? open.navigate(url).then((client) => client && client.focus()) : self.clients.openWindow(url);
    }),
  );
});
