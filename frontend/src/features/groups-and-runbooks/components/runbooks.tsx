import { Box } from "#components/box.tsx";
import { CloseDetail } from "#components/close-detail.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { Save } from "#components/save.tsx";
import { bookTypes, useWorkspaceContext } from "#features/workspace";
import { unique } from "#lib/id.ts";
import { BookOpen, Plus } from "lucide-react";

export function BookList() {
  const { data, bookId, setBookId, setCompactDetail, compactDetail, panel, commit } = useWorkspaceContext();
  return (
    <Box
      title="Runbooks"
      icon={BookOpen}
      extra={
        <button
          onClick={() => {
            const id = unique();
            commit(
              {
                ...data,
                books: [
                  ...data.books,
                  { id, name: "Untitled runbook", type: "Checklist", body: "" },
                ],
              },
              "Runbook draft created locally.",
            );
            setBookId(id);
            setCompactDetail(true);
          }}
        >
          <Plus />
          Draft runbook
        </button>
      }
    >
      <div className="record-list">
        {data.books.map((b) => (
          <button
            key={b.id}
            aria-pressed={b.id === bookId && (panel !== "Runbooks" || compactDetail)}
            onClick={() => {
              setBookId(b.id);
              setCompactDetail(true);
            }}
          >
            <span>
              <strong>{b.name}</strong>
              <small>{b.type}</small>
            </span>
          </button>
        ))}
      </div>
    </Box>
  );
}
export function BookEditor() {
  const { data, bookId, commit } = useWorkspaceContext(),
    book = data.books.find((b) => b.id === bookId);
  return (
    <Box title={book ? book.name : "Runbook editor"} icon={BookOpen} extra={<CloseDetail page="Runbooks" label="Close runbook" />}>
      {book ? (
        <form
          key={JSON.stringify(book)}
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            commit(
              {
                ...data,
                books: data.books.map((b) =>
                  b.id === book.id
                    ? {
                        ...b,
                        name: String(f.get("name")),
                        type: String(f.get("type")),
                        body: String(f.get("body")),
                      }
                    : b,
                ),
              },
              "Runbook saved locally.",
            );
          }}
        >
          <Field label="Runbook title">
            <input name="name" required defaultValue={book.name} />
          </Field>
          <Field label="Runbook type">
            <select name="type" defaultValue={book.type}>
              {bookTypes.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </Field>
          <Field label="Instructions">
            <textarea name="body" required rows={6} defaultValue={book.body} />
          </Field>
          <Save label="Save runbook" />
        </form>
      ) : (
        <Empty title="Choose a runbook" />
      )}
    </Box>
  );
}
