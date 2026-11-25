import { cpSync } from "node:fs";

cpSync(
  new URL("../../infrastructure/supabase/migrations/", import.meta.url),
  new URL("../dist/infrastructure/supabase/migrations/", import.meta.url),
  { recursive: true },
);
