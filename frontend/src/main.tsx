import { App } from "#app.tsx";
import { WorkspaceProvider } from "#features/workspace";
import "#index.css";
import { createRoot } from "react-dom/client";

createRoot(document.getElementById("root")!).render(
  <WorkspaceProvider>
    <App />
  </WorkspaceProvider>,
);
