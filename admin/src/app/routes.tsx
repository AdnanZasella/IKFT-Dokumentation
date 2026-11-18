import type { RouteObject } from "react-router";

import { ChangePasswordPage } from "../auth/ChangePasswordPage";
import { LoginPage } from "../auth/LoginPage";
import { Placeholder } from "./Placeholder";
import { RedirectIfSignedIn } from "./RedirectIfSignedIn";
import { RequireSession } from "./RequireSession";

// Översikten är en platshållare som byggs i #49 (skal och Översikt).
export const routes: RouteObject[] = [
  {
    element: <RedirectIfSignedIn />,
    children: [{ path: "/login", element: <LoginPage /> }],
  },
  {
    element: <RequireSession passwordChange />,
    children: [{ path: "/change-password", element: <ChangePasswordPage /> }],
  },
  {
    element: <RequireSession />,
    children: [{ path: "/", element: <Placeholder title="Översikt" /> }],
  },
];
