import { Check } from "lucide-react";

export function Save({ label }: { label: string }) {
  return (
    <button className="primary" type="submit">
      <Check />
      {label}
    </button>
  );
}
