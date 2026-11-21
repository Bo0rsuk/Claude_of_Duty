import { X } from "lucide-react";
import { type ReactNode, useEffect, useId, useRef } from "react";

export function Modal({
  title,
  onClose,
  children,
  drawer = false,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  drawer?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null),
    id = useId();
  useEffect(() => {
    const d = ref.current,
      trigger = document.activeElement;
    d?.showModal();
    return () => {
      d?.close();
      if (trigger instanceof HTMLElement && trigger.isConnected)
        trigger.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby={id}
      className={drawer ? "drawer" : ""}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="dialog-inner">
        <header>
          <h2 id={id}>{title}</h2>
          <button
            aria-label="Close dialog"
            className="icon-btn"
            onClick={onClose}
          >
            <X />
          </button>
        </header>
        {children}
      </div>
    </dialog>
  );
}
