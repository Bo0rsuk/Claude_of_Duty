import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { Save } from "#components/save.tsx";
import { type Contact, type Group, useWorkspaceContext } from "#features/workspace";
import { UserPlus, X } from "lucide-react";
import { useState } from "react";

const blank: Contact = { name: "", email: "", phone: "" };

/** People outside the organization who are notified with the group: they have no account, only an address. */
export function GroupContacts({ group }: { group: Group }) {
  const { data, commit } = useWorkspaceContext();
  const [adding, setAdding] = useState(false),
    [draft, setDraft] = useState(blank),
    [error, setError] = useState("");
  const contacts = group.contacts ?? [];
  const save = (next: Contact[], message: string) =>
    commit({ ...data, groups: data.groups.map((g) => (g.id === group.id ? { ...g, contacts: next } : g)) }, message);
  return (
    <section className="group-contacts">
      <h4>Outside contacts</h4>
      {contacts.length ? (
        contacts.map((contact, index) => (
          <div className="check-line person" key={index}>
            <span>
              <strong>{contact.name}</strong>
              <small>{[contact.email, contact.phone].filter(Boolean).join(" · ")}</small>
            </span>
            <button
              type="button"
              className="icon-btn"
              aria-label={"Remove " + contact.name}
              title="Remove from the group"
              onClick={() =>
                save(
                  contacts.filter((_, other) => other !== index),
                  "Contact removed locally.",
                )
              }
            >
              <X />
            </button>
          </div>
        ))
      ) : (
        <p className="hint">
          Nobody from outside the organization. Add a contact to notify someone who has no account here, such as a
          vendor's support line.
        </p>
      )}
      <div className="buttons">
        <button
          type="button"
          onClick={() => {
            setDraft(blank);
            setError("");
            setAdding(true);
          }}
        >
          <UserPlus />
          Add outside contact
        </button>
      </div>
      {adding && (
        <Modal title="Add outside contact" onClose={() => setAdding(false)}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const contact = { name: draft.name.trim(), email: draft.email.trim(), phone: draft.phone.trim() };
              if (!contact.email && !contact.phone) {
                setError("Enter an email address or a phone number, so the contact can be reached.");
                return;
              }
              save([...contacts, contact], "Contact added locally.");
              setAdding(false);
            }}
          >
            <Field label="Name">
              <input
                required
                pattern=".*\S.*"
                value={draft.name}
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              />
            </Field>
            <Field label="Email · for the email channel">
              <input type="email" value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} />
            </Field>
            <Field label="Phone · for SMS and calls">
              <input
                type="tel"
                pattern="\+\d{6,15}"
                title="International form, for example +48600100200"
                placeholder="+48600100200"
                value={draft.phone}
                onChange={(e) => setDraft({ ...draft, phone: e.target.value })}
              />
            </Field>
            <p className="hint">In-app and push notifications need an account, so they do not reach outside contacts.</p>
            {error && (
              <p role="alert" className="error">
                {error}
              </p>
            )}
            <div className="buttons">
              <button type="button" onClick={() => setAdding(false)}>
                Cancel
              </button>
              <Save label="Add contact" />
            </div>
          </form>
        </Modal>
      )}
    </section>
  );
}
