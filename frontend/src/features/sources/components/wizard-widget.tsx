import { Box } from "#components/box.tsx";
import { SourceWizard } from "#features/sources/components/source-wizard.tsx";
import { Database, Plus } from "lucide-react";
import { useState } from "react";

export function WizardWidget() {
  const [show, setShow] = useState(false);
  return (
    <Box title="Connect a source" icon={Database}>
      <button onClick={() => setShow(true)}>
        <Plus />
        Add source
      </button>
      {show && <SourceWizard onClose={() => setShow(false)} />}
    </Box>
  );
}
