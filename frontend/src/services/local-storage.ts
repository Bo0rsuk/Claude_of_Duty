export function readLocal<T>(key: string, initial: () => T): T {
  try {
    // Adopt layouts and records saved under the previous product name.
    const current = localStorage.getItem(key);
    if (current) return JSON.parse(current);
    const raw = localStorage.getItem(key.replace(/^fans:/, "relay:"));
    if (raw) {
      const saved = JSON.parse(raw);
      if (key === "fans:data:1" && Array.isArray(saved.members)) {
        for (const member of saved.members) {
          if (
            member.email === member.id + "@relay.example" ||
            (member.id === "guest" && member.email === "riley@relay.example")
          )
            member.email = member.email.replace(
              /@relay\.example$/,
              "@fans.example",
            );
        }
      }
      return saved;
    }
  } catch {
    /* Use fixtures if storage is unavailable. */
  }
  return initial();
}

export function writeLocal<T>(key: string, value: T) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
    localStorage.removeItem(key.replace(/^fans:/, "relay:"));
  } catch {
    /* The session remains usable in memory. */
  }
}
