import { App } from "#app.tsx";
import { SignedIn, WorkspaceProvider } from "#features/workspace";
import { live } from "#services/backend/config.ts";
import "#index.css";
import { createRoot } from "react-dom/client";

const workspace = (
  <WorkspaceProvider>
    <App />
  </WorkspaceProvider>
);
// With a backend configured, the workspace is for signed-in users only.
createRoot(document.getElementById("root")!).render(live ? <SignedIn>{workspace}</SignedIn> : workspace);
