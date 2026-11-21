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
