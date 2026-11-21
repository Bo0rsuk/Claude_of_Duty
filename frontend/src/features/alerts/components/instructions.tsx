import { Empty } from "#components/empty.tsx";
import { useWorkspaceContext } from "#features/workspace";
import { BookOpen } from "lucide-react";

export function Instructions() {
  const { alert: a, data, commit } = useWorkspaceContext(),
    book = data.books.find((b) => b.id === a?.runbook);
  if (!book || !a)
    return (
      <Empty title="No runbook selected">
        Select an alert with a linked runbook.
      </Empty>
    );
  return (
    <div className="runbook">
      <h3>
        <BookOpen />
        {book.name}
      </h3>
      <p className="muted">{book.type}</p>
      {book.type === "Checklist" ? (
        book.body
          .split("\n")
          .filter(Boolean)
          .map((line, i) => (
            <label className="check-line" key={i}>
              <input
                type="checkbox"
                checked={a.checks.includes(i)}
                onChange={(e) =>
                  commit(
                    {
                      ...data,
                      alerts: data.alerts.map((x) =>
                        x.id === a.id
                          ? {
                              ...x,
                              checks: e.target.checked
                                ? [...x.checks, i]
                                : x.checks.filter((v) => v !== i),
                            }
                          : x,
                      ),
                    },
                    "Runbook progress saved locally.",
                  )
                }
              />
              {line}
            </label>
          ))
      ) : book.type === "Wiki link" ? (
        <details>
          <summary>Open demo wiki reference</summary>
          <p>{book.body}</p>
          <p>
            Compare the latest instrument quote with the last healthy market
            snapshot.
          </p>
        </details>
      ) : (
        book.body
          .split("\n")
          .map((line, i) =>
            line.startsWith("## ") ? (
              <h4 key={i}>{line.slice(3)}</h4>
            ) : (
              <p key={i}>{line}</p>
            ),
          )
      )}
    </div>
  );
}
