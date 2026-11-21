import { readLocal, writeLocal } from "#services/local-storage.ts";
import { useEffect, useState } from "react";

export function useLocal<T>(key: string, initial: () => T) {
  const [value, setValue] = useState<T>(() => readLocal(key, initial));
  useEffect(() => writeLocal(key, value), [key, value]);
  return [value, setValue] as const;
}
