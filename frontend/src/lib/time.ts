export function relative(time: number) {
  const minutes = Math.max(0, Math.floor((Date.now() - time) / 60000));
  return minutes === 0
    ? "Just now"
    : minutes < 60
      ? minutes + "m ago"
      : minutes < 1440
        ? Math.floor(minutes / 60) + "h ago"
        : Math.floor(minutes / 1440) + "d ago";
}
export const clock = (time: number) =>
  new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(time);

const day = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

/** How long ago something happened, to the second while it is fresh: what an on-call person reads a feed for. */
export function ago(time: number, now: number) {
  const seconds = Math.max(0, Math.floor((now - time) / 1000));
  if (seconds < 5) return "now";
  if (seconds < 60) return seconds + " s ago";
  if (seconds < 3600) return Math.floor(seconds / 60) + " min ago";
  if (seconds < 86400) return Math.floor(seconds / 3600) + " h ago";
  return day.format(time);
}

const dated = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });

/** A moment with seconds; the date is added when it was not today, so yesterday's 01:20 is not read as today's. */
export function stamp(time: number, now = Date.now()) {
  const sameDay = new Date(time).toDateString() === new Date(now).toDateString();
  return sameDay ? clock(time) : dated.format(time) + ", " + clock(time);
}
