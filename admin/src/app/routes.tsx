import type { RouteObject } from "react-router";

import { Placeholder } from "./Placeholder";
import { RequireSession } from "./RequireSession";

// Sidorna är platshållare. De byggs i #48 (inloggning och lösenordsbyte) och #49 (skal och Översikt).
export const routes: RouteObject[] = [
  { path: "/login", element: <Placeholder title="Inloggning" /> },
  {
    element: <RequireSession passwordChange />,
    children: [{ path: "/change-password", element: <Placeholder title="Byt lösenord" /> }],
  },
  {
    element: <RequireSession />,
    children: [{ path: "/", element: <Placeholder title="Översikt" /> }],
  },
];
