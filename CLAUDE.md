# IKFT

Hemsida och internt dokumentationssystem för Islamiska Kulturföreningen i Trollhättan.
Specifikationen finns i [docs/00-oversikt.md](docs/00-oversikt.md), som länkar till
`docs/01-*.md` till `docs/08-*.md`. Läs rätt delfil innan du ändrar något som berör
verksamhetsregler, roller eller API. Läs [docs/07-teststrategi.md](docs/07-teststrategi.md) innan du skriver eller
granskar tester och [docs/08-frontend.md](docs/08-frontend.md) innan du bygger admin-appen eller hemsidan.

## Struktur

- `backend/` – Spring Boot 4.1.1, Java 21, Maven, paket `se.ikft`.
- `docs/` – specifikationen, teststrategin och frontendplanen, uppdelad i nio filer.
- `.claude/agents/` – `testgranskare` (granskar att tester finns på rätt nivå, kör före varje PR) och
  `e2e-utforskare` (tar fram E2E-test med Playwright MCP, först när det finns en frontend). `.mcp.json` har
  Playwright MCP.
- `design/` – förebild och material för hemsidans utseende (skärmbilder, logotyp, texter). Mappen är
  publik, så inget där får vara privat. Behövs först i fas 4.
- `admin/` – admin-appen (React, Vite, strikt TypeScript). Grunden är klar (#47): typad API-klient (`src/api/`),
  session och routing (`src/app/`), Vitest med MSW (`src/test/`) och API-kontraktet (`src/api/openapi.json` och
  `schema.d.ts`). Inloggning och tvingat lösenordsbyte finns (#48, `src/auth/`, byggstenar i `src/ui/`, Tailwind med
  designvariabler i `src/styles.css`). Övriga sidor är platshållare och byggs i #49–#52.
- `e2e/` – Playwright-tester (#48): `tests/` (specar och `auth.setup.ts`), `support/` (`ApiSession` för förberedelser via
  API:et, testkontona). Backenden startas med profilen `e2e`, se Kommandon.
- `web/` (hemsidan, Astro) finns inte än (#53).

## Läget

Fas 1 är klar utom #13 (föreningens egen medlemsavgift, skjuts upp tills medlemskapet är bestämt) och
eventuellt #10 och #28. Färdigt: grunden, engångskoder, inloggning (med utloggning, förnyelse, spärr mot gissning och
byte av eget lösenord), medlemsregister (med föreningsroller och moskéavgift via FIFS), ändringslogg,
dokumenteraruppdrag (modell och `GET /documenter-assignment/current`), innehavarbyte (`HolderChangeService`) och
tvingat lösenordsbyte efter ett innehavarbyte, att utse ordinarie och tillfällig dokumenterare (`POST
/documenter-assignment` och `/temporary`, `DELETE /temporary`), ett schemalagt jobb (`HolderSyncJob`, vid midnatt
och uppstart) som byter innehavare när ett uppdrag börjar eller löper ut, och behörighetsmatrisen (#20,
`BehorighetsmatrisIntegrationTest`, och synk av docs/04). Se issue #31 (ordning för Fas 1) och
[Öppna frågor](docs/06-oppna-fragor.md). Flaggan för betald avgift gäller än så länge bara moskéavgiften
via FIFS. Föreningens egen medlemsavgift läggs till när medlemskapet är bestämt. Ändringsloggen sparar
bara födelsedatum, hemkommun och status med värden, övriga fält bara som ändrade.

Frontend: planen finns i [docs/08-frontend.md](docs/08-frontend.md) (admin-appen i React, hemsidan i Astro,
admin först och hemsidan som prototyp med exempeldata). Issues #46–#53, ordning i #31. #46 (OpenAPI-kontraktet) är
klart, liksom #47 (admin-appens grund, med typade felsvar: `ProblemDetail` är `default`-svar i kontraktet) och #48
(inloggning, tvingat lösenordsbyte och första E2E-flödet med profilen `e2e`, `e2e/` och CI-jobbet `e2e.yml`).
Inloggningen lägger kontot ur svaret i cachen (`ME_KEY`, `staleTime: Infinity`) i stället för att invalidera den, och
redan inloggade skickas bort från `/login`. Nästa är #49 (skal med navigering och Översikt, där Radix läggs till om en
dialog eller meny behövs), därefter #50–#52. Det finns ännu ingen sida i admin-appen för `POST /auth/set-password`
(engångskod), som behövs för att sätta Admins första lösenord utan API-anrop. Hemsidans prototyp (#53) tas efter
admin-spåret.
Backendens API för sammankomster, möten och det publika API:et finns inte än (Fas 2–4).

Backend är uppdelad i paket per funktion under `se.ikft`. Varje paket har en `package-info.java`
som beskriver vad paketet ansvarar för:

- `common`: konfiguration, felhantering och hjälpklasser
- `security`: Spring Security-konfiguration
- `auth`: inloggning och lösenord
- `account`: konton och innehavare
- `member`: medlemsregistret
- `audit`: ändringsloggen
- `assignment`: dokumenteraruppdrag

## Kommandon

Kör i `backend/`:

- `./mvnw spring-boot:run` – starta lokalt
- `./mvnw test` – kör tester
- `./mvnw verify` – bygg och testa

På Windows kan `JAVA_HOME` saknas i skalet. Sätt den till en JDK 21, t.ex.
`export JAVA_HOME=/c/Users/<namn>/.jdks/ms-21.0.13.0.2`. Efter att en migrering har bytt namn eller tagits
bort ska bygget köras med `./mvnw clean verify`, annars ligger den gamla filen kvar i `target/` och Flyway
hittar två migreringar med samma version.

Docker krävs. Lokalt startar `spring-boot-docker-compose` Postgres 18 från `compose.yaml`,
och testerna använder Testcontainers.

`spring-boot:run` kräver miljövariabeln `IKFT_SECURITY_JWT_SECRET` (base64, minst 32 byte, t.ex.
`openssl rand -base64 32`). Repot har ingen standardnyckel. Testerna slumpar en egen nyckel.

Kör i `admin/` (Node 22):

- `npm install` – installera. CI kör `npm ci`, så `package-lock.json` måste stämma. Peer-varningar duger inte:
  `openapi-typescript` kräver TypeScript 5, så TypeScript står på 5.9.x.
- `npm run dev` – Vite på `localhost:5173`, med `/api` proxad till backenden på `localhost:8080` (starta den först)
- `npm run lint`, `npm run typecheck`, `npm test` (Vitest) och `npm run build`
- På Windows: kör Vitest från PowerShell eller en terminal där sökvägen har stor enhetsbokstav (`C:\`). I Git Bash
  (`/c/...`) hittar Vitest inte sin körare ("failed to find the runner").

Kör i `e2e/` (Node 22, Docker igång, dev-backenden på port 8080 stoppad):

- Starta först backenden i `backend/`: `./mvnw spring-boot:test-run -Dspring-boot.run.profiles=e2e` (egen databas via
  Testcontainers, kända lösenord, rör inte dev-databasen). Vänta på `Started BackendApplication`.
- `npm ci`, första gången `npx playwright install chromium`, sedan `npm test`. Playwright startar Vite själv.
  `npm run typecheck` kontrollerar typerna. Testerna går att köra om mot samma backend.
- Kör Playwright från PowerShell, som Vitest. E2E-backenden (`-Xmx512m` räcker), Chromium och Vite gick med knappt
  1 GB ledigt minne, men kör inte E2E samtidigt som `./mvnw verify`.

## Konventioner

- Databasändringar görs med Flyway. JPA körs med `open-in-view: false` och
  `ddl-auto: validate`.
- Alla felsvar är RFC 9457 `ProblemDetail` från `common.error.GlobalExceptionHandler`, även 401
  och 403. Kasta `BadRequestException`, `NotFoundException` eller `ConflictException` för att få 400, 404
  respektive 409.
- Tidslogik använder den injicerade `Clock` (Europe/Stockholm), aldrig `now()` utan argument.
- Ett konto som ska ändras under lås läses direkt med `findForUpdateBy...`, aldrig först utan lås. En rad som redan
  lästs i transaktionen uppdateras inte när låset ges, så `tokenVersion` blir föråldrad och en samtidig ändring
  skrivs över (sessioner som ska loggas ut förblir giltiga). Samtidiga anrop testas med flera omgångar.
- API:et är versionerat under `/api/v1`, publikt under `/api/v1/public`. Behörighet kontrolleras
  i backend med Spring Security, aldrig bara i gränssnittet.
- Autentisering är stateless JWT i HttpOnly-cookie.
- Bidragsregler (åldersgränser, minsta längd och deltagarantal, tak) ska vara konfigurerbara,
  inte hårdkodade.
- Dataminimering: lagra födelsedatum, aldrig personnummer. Medlemmar inaktiveras, de raderas inte.
- Artikeltext saneras i backend innan den sparas.
- Text i JSON-förfrågningar med kontrolltecken (t.ex. NUL) avvisas med 400 redan när JSON läses
  (`common.config.JsonConfig`), utom radbrytning, vagnretur och tabb. `GlobalExceptionHandler` ger 400
  bara för SQLSTATE 22021, 22P05 och 22001. Andra fel i klass 22 är serverfel (500, loggas på ERROR).
  Gäller inte parametrar i URL:en.

## Tester

- Ingen funktion är klar utan tester på rätt nivå. En buggfix börjar med ett test som misslyckas.
- Enhetstester (JUnit 5, AssertJ) för ren logik utan Spring och databas, t.ex. bidragsberäkning,
  engångskoder och tidslogik med fast `Clock`.
- Integrationstester (`@SpringBootTest`, MockMvc, Testcontainers) för varje endpoint. Varje endpoint
  testas minst för lyckat fall, valideringsfel, 401 och 403 för fel roll. Använd `TestCsrf.csrfToken()`
  för muterande anrop.
- En ny endpoint under `/api/v1` får en rad i behörighetsmatrisen (`BehorighetsmatrisIntegrationTest.MATRIS`) och
  i tabellerna i docs/04. Matristestet misslyckas annars. Matrisen kontrollerar bara 401, 403 och tillåtet, vad
  endpointen gör testas i funktionens egna tester.
- API-kontraktet: ändrar du en endpoint eller en svarsklass misslyckas `OpenApiKontraktIntegrationTest`. Kör
  `./mvnw test -Dtest=OpenApiKontraktIntegrationTest -Dopenapi.update=true`, därefter `npm run gen:api` i `admin/`, och
  checka in `admin/src/api/openapi.json` och `schema.d.ts`. Ett svarsfält som kan vara null markeras
  `@Schema(nullable = true)`, annars blir det obligatoriskt i TypeScript-typerna. Varje endpoint har ett
  `default`-svar med `ProblemDetail` (`OpenApiConfig`), så felkroppen är typad i klienten. Se
  [docs/07-teststrategi.md](docs/07-teststrategi.md#kontrakt-mellan-backend-och-frontend).
- Admin-appens API-klient (`admin/src/api/client.ts`) sköter CSRF, förnyelse vid 401 och lösenordsbyte vid 403. Sidor
  anropar `useApi()` och behöver inte hantera det själva. Mockar i tester (MSW) använder `endpoint(...)`,
  `accountInfo(...)` och `problem(...)` ur `src/test/mocks.ts`, som är typade efter kontraktet.
- Bidragsregler testas med gränsvärden (59/60 minuter, 2/3 deltagare, ålder 6/7/25/26).
- Playwright (E2E) ligger i `e2e/` och körs mot backenden med profilen `e2e` (hjälpklassen `E2eDataInitializer` i
  `src/test`, aldrig i produktionskoden). Håll antalet E2E-tester litet och täck bara de viktigaste flödena. Valideringsvarianter och rollmatriser testas i backend, inte i webbläsaren.
  Upplägg, körmiljö och skrivregler (inga `waitForTimeout`, lokaliserare på roll och etikett, egen testdata) finns i
  [docs/07-teststrategi.md](docs/07-teststrategi.md).
- Ett test som fallerar ibland är en bugg: undersök orsaken, kör inte bara om.
- Före varje PR: kör agenten `testgranskare` och `/code-review`. CI ska vara grönt, agenternas svar är bara underlag.
- Schemalagda jobb stängs av i tester med `ikft.assignment.sync-cron=-` (`src/test/resources/application.properties`),
  så att de inte startar mitt i ett test som kör över midnatt. Testerna anropar jobbet direkt.
- Testdata är påhittad, aldrig riktiga personer.
- Integrationstester som skriver till databasen städar själva. Ordningen vid städning är
  `audit_log_change`, `audit_log`, `member_fifs_fee`, `member_role`, `documenter_assignment`, `member`, annars stoppar
  främmande nycklar raderingen. Testkontexten delas, så ett test som lämnar rader kvar kan fälla ett annat.
- Ändringsloggen skrivs med `AuditService.record(...)` i samma transaktion som ändringen. Anropet kräver
  en pågående transaktion. Ändringar som inte ändrar något, och misslyckade anrop, ska inte loggas.
- `./mvnw verify` skriver täckningsrapport (JaCoCo) till `backend/target/site/jacoco/`. Ingen hård gräns.

## Docs

- Specifikationen ligger i delfiler. Redigera rätt delfil och länka mellan filer med relativa
  länkar, inte med avsnittsnummer.

## Repo

- Repot är publikt: inga hemligheter och inga riktiga personuppgifter, inte heller i testdata.
- Ingen licensfil ska läggas till.

## Git

- Commitmeddelanden är på svenska med prefix som `docs:` och `chore:`.
