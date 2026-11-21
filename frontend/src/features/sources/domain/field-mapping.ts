export function detectFieldMapping(sample: string, format: string) {
  const raw =
    format === "JSON"
      ? Object.keys(JSON.parse(sample))
      : format === "key=value"
        ? [...sample.matchAll(/([\w.-]+)=/g)].map((x) => x[1])
        : [...sample.matchAll(/([\w.-]+)\s*[:=]/g)].map((x) => x[1]);
  if (!raw.length && format !== "Custom format") throw Error();
  return (raw.length ? raw : ["message"]).map((key) => key + " → " + key).join("\n");
}
