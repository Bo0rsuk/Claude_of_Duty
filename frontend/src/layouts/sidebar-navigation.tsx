import type { Panel } from "#features/workspace";
import { type ReactNode, useLayoutEffect, useRef } from "react";

export function SidebarNavigation({
  panel,
  children,
}: {
  panel: Panel | null;
  children: ReactNode;
}) {
  const nav = useRef<HTMLElement>(null);
  const indicator = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const container = nav.current;
    const pill = indicator.current;
    if (!container || !pill) return;
    const link = container.querySelector<HTMLAnchorElement>('a[aria-current="page"]');
    if (!link) {
      pill.hidden = true;
      return;
    }

    const align = () => {
      const bounds = link.getBoundingClientRect();
      pill.hidden = !bounds.width;
      if (pill.hidden) return;
      const origin = container.getBoundingClientRect();
      pill.style.width = bounds.width + "px";
      pill.style.height = bounds.height + "px";
      pill.style.transform = `translate(${bounds.left - origin.left}px, ${bounds.top - origin.top}px)`;
    };

    align();
    const observer = new ResizeObserver(align);
    observer.observe(container);
    observer.observe(link);
    return () => observer.disconnect();
  }, [panel]);

  return (
    <nav ref={nav} className="sidebar-nav" aria-label="Main navigation">
      <div ref={indicator} className="nav-indicator" aria-hidden="true" />
      {children}
    </nav>
  );
}
