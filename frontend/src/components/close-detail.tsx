import { useWorkspaceContext } from "#features/workspace";
import type { Panel } from "#features/workspace/types/records.ts";
import { X } from "lucide-react";

/**
 * Closes the details that opened beside a list, giving the list the page back.
 * Shown only on the page the details belong to: in a custom view they are a
 * widget of their own and there is nothing to return to.
 */
export function CloseDetail({ page, label }: { page: Panel; label: string }) {
  const { compactDetail, setCompactDetail, panel } = useWorkspaceContext();
  if (!compactDetail || panel !== page) return null;
  return (
    <button className="icon-btn close-detail" aria-label={label} title={label} onClick={() => setCompactDetail(false)}>
      <X />
    </button>
  );
}
