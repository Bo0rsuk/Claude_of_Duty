import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

export function Box({
  title,
  icon: Icon,
  children,
  extra,
}: {
  title: string;
  icon: LucideIcon;
  children: ReactNode;
  extra?: ReactNode;
}) {
  return (
    <section className="surface">
      <header className="box-head">
        <h2>
          <Icon />
          {title}
        </h2>
        {extra}
      </header>
      <div className="box-body">{children}</div>
    </section>
  );
}
