import { summarizeActivity } from "#features/alerts/domain/activity.ts";
import { useWorkspaceContext } from "#features/workspace";
import { Activity, Clock3 } from "lucide-react";
import { useState } from "react";

const NO_BACKGROUND = Array<number>(72).fill(0);

export function ActivityOverview() {
  const { data, account } = useWorkspaceContext(),
    [period, setPeriod] = useState("24");
  const { active, total, samples, max } = summarizeActivity(
    data.alerts,
    Number(period),
    Date.now(),
    // The made-up background traffic belongs to the demo only.
    account ? NO_BACKGROUND : undefined,
  );
  return (
    <section className="surface activity-card">
      <header>
        <h2>
          <Activity />
          Activity overview
        </h2>
        <label className="period">
          <Clock3 />
          <select
            aria-label="Activity period"
            value={period}
            onChange={(e) => setPeriod(e.target.value)}
          >
            <option value="24">Last 24 hours</option>
            <option value="6">Last 6 hours</option>
            <option value="1">Last hour</option>
          </select>
        </label>
      </header>
      <div className="activity-content">
        <div className="metrics">
          <div>
            <span>Active alerts</span>
            <strong>{active.length}</strong>
          </div>
          <div>
            <span>
              <i className="metric-dot" />
              Critical
            </span>
            <strong>
              {active.filter((a) => a.severity === "Critical").length}
            </strong>
          </div>
          <div>
            <span>Grouped events</span>
            <strong>{total.toLocaleString("en-US")}</strong>
          </div>
        </div>
        <figure className="volume">
          <figcaption>
            <span className="legend-square" />
            Event volume
          </figcaption>
          <div
            className="bars"
            role="img"
            aria-label={
              (account ? "Event volume of " : "Simulated inbound volume of ") +
              samples.reduce((sum, count) => sum + count, 0) +
              " events over " +
              period +
              " hours"
            }
          >
            {samples.map((v, i) => (
              <i
                key={i}
                title={v + " events"}
                style={{ height: Math.max(2, (100 * v) / max) + "%" }}
              />
            ))}
          </div>
          <div className="axis">
            <span>−{period}h</span>
            <span>−{Number(period) / 2}h</span>
            <span>Now</span>
          </div>
        </figure>
      </div>
    </section>
  );
}
