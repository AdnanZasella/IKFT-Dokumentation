import { setupServer } from "msw/node";

/** MSW-servern som tar emot alla nätverksanrop i testerna. Varje test lägger till sina egna svar med `server.use`. */
export const server = setupServer();
