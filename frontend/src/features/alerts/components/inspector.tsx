import { Badge } from "#components/badge.tsx";
import { Empty } from "#components/empty.tsx";
import { StatusTag } from "#components/status-tag.tsx";
import { Actions } from "#features/alerts/components/actions.tsx";
import { Instructions } from "#features/alerts/components/instructions.tsx";
import { Overview } from "#features/alerts/components/overview.tsx";
import { Timeline } from "#features/alerts/components/timeline.tsx";
import { type Severity, useWorkspaceContext } from "#features/workspace";
import { ArrowLeft, BookOpen, ChevronRight, History, Layers, SquareStack, X } from "lucide-react";

export function Inspector() {
  const { alert: a, alertTab, setAlertTab, compactDetail, setCompactDetail, panel } = useWorkspaceContext();
  if (!a)
    return (
      <section className="surface">
        <Empty title="Choose an alert">
          Select an alert in the feed to inspect its events and take action.
        </Empty>
      </section>
    );
  return (
    <section className="surface inspector">
      <header className="inspector-top">
        <h2>
          <SquareStack />
          Alert details
        </h2>
        <span className="inspector-tools">
          <span className="id-label">{a.id}</span>
          {/* In the alert workspace the details open over part of the feed; closing them gives the feed its full width back. */}
          {compactDetail && panel === "Alerts" && (
            <button
              className="icon-btn close-detail"
              aria-label="Close alert details"
              title="Close alert details"
              onClick={() => setCompactDetail(false)}
            >
              <X />
            </button>
          )}
        </span>
      </header>
      <div className="inspection-heading">
        <div className="detail-tags">
          <Badge
            severity={a.severity as Severity}
            resolved={a.status === "Resolved"}
          />
          <StatusTag value={a.status} />
        </div>
        <h2>{a.title}</h2>
        <Actions />
      </div>
      <div className="tabs">
        {[
          ["Overview", Layers],
          ["Timeline", History],
          ["Runbook", BookOpen],
        ].map(([label, icon]) => {
          const Icon = icon as typeof Layers;
          return (
            <button
              key={String(label)}
              aria-pressed={alertTab === label}
              className={alertTab === label ? "active" : ""}
              onClick={() => setAlertTab(String(label))}
            >
              <Icon />
              {String(label)}
            </button>
          );
        })}
      </div>
      {alertTab === "Overview" ? (
        <Overview a={a} />
      ) : alertTab === "Timeline" ? (
        <Timeline key={a.id} />
      ) : (
        <Instructions />
      )}
      <button className="detail-back" onClick={() => setCompactDetail(false)}>
        <ArrowLeft />
        Back to feed
        <ChevronRight />
      </button>
    </section>
  );
}
