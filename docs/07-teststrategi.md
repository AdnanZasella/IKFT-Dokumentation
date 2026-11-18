# Teststrategi

Del av [projektspecifikationen](00-oversikt.md).

Målet är få buggar utan onödigt många tester. Varje sak testas på den lägsta nivå där den går att testa
tillförlitligt. Det som är dyrt (webbläsare, hela systemet) används bara för det som inte går att bevisa
billigare.

## Nivåer

| Nivå | Verktyg | Testar | Status |
|---|---|---|---|
| 1. Enhet | JUnit 5, AssertJ | Ren logik utan Spring och databas: bidragsregler med gränsvärden, engångskoder, tidslogik med fast `Clock`. | Finns |
| 2. Integration (API) | `@SpringBootTest`, MockMvc, Testcontainers (Postgres 18) | Varje endpoint mot en riktig databas: behörighet, validering, transaktioner, Flyway-migreringar. Här ligger nästan all affärslogik. | Finns |
| 3. Frontend-komponenter | Vitest, Testing Library | Formulär, validering och visning, utan webbläsare och utan backend. | Finns |
| 4. E2E | Playwright (`e2e/`) | De få flöden där webbläsare, frontend, backend och databas måste fungera ihop. | Finns (första inloggningen) |
| 5. Utforskning | Playwright MCP med Claude | Interaktiv felsökning och att ta fram nya E2E-tester. Bevisar ingenting i CI. | Kan användas |

Regel: ett fel som kan fångas på nivå 1–2 ska inte bara fångas på nivå 4. Valideringsvarianter,
gränsvärden och rollmatriser testas i backend, inte i webbläsaren.

## Nivå 1–2: backend (gäller redan)

- **Ny endpoint:** lyckat fall, valideringsfel, 401 utan inloggning, 403 för fel roll och saknad CSRF-token
  (`TestCsrf.csrfToken()`) för muterande anrop. Rollkontrollen testas för varje roll som inte ska ha åtkomst.
- **Regel med tal:** gränsvärden på båda sidor (59/60 minuter, 2/3 deltagare, ålder 6/7/25/26).
- **Tid:** fast `Clock` i enhetstester. Integrationstester räknar datum från den injicerade klockan.
- **Databasändring:** täcks av integrationstesterna, som kör alla Flyway-migreringar mot en riktig Postgres.
  Constraints testas när de uttrycker en affärsregel (till exempel att ett uppdrag inte kan sluta efter
  31 december).
- **Bugg:** börjar med ett test som misslyckas.
- **Städning:** testet städar själv, i ordningen i [CLAUDE.md](../CLAUDE.md). Testkontexten delas, så ett test
  som lämnar rader kvar kan fälla ett annat.
- **Täckning:** JaCoCo rapporterar men har ingen hård gräns. Siffran är ett verktyg för att hitta luckor, inte
  ett mål. Vid införandet var den 97 % för instruktioner och 91 % för grenar.
- **Ett test som fallerar ibland är en bugg**, inte något att köra om. Undersök orsaken.
- **Tid i databastester:** jämför aldrig appens tidpunkter med databasens `now()` utan marginal. Appen skriver med
  den injicerade `Clock`, och Docker-VM:ens klocka kan avvika från värdens (till exempel efter en omstart av datorn).
  Använd `Clock` som parameter, eller en marginal på minst en minut. Det fällde
  `AdminSetupCodeInitializerTest.aterstallningErsatterTidigareKod` och `AccountSetupCodeIntegrationTest.enNyKodErsatterDenForra`
  intermittent, och båda går igenom när de körs för sig. Orsaken är inte bevisad genom mätning, men testerna är nu
  oberoende av databasens klocka.

## Nivå 3: frontend-komponenter

- Vitest och Testing Library, med frågor på roll och etikett (`getByRole`, `getByLabelText`), aldrig på CSS-klass.
- Testa beteende som användaren ser: att ett fel visas, att knappen är avstängd under sändning, att svenska och
  arabiska (höger-till-vänster) renderas.
- Anrop mot API:et mockas på nätverksnivå (MSW). Mockarna ska följa API-typerna så att de inte glider isär.
- Inga ögonblicksbilder av hela sidor.

## Nivå 4: E2E med Playwright

**Omfång.** Håll det litet, några få flöden per fas. Ett flöde är med om det bryter något som en användare
inte kan komma runt:

1. Första inloggningen: logga in, tvingas byta lösenord (#22), kommer in.
2. Rollspärr: Dokumenterare når inte Admin-sidor, och utloggad skickas till inloggningen.
3. Dokumenteraren registrerar en sammankomst och närvaro, i mobilstorlek (Fas 2).
4. Admin utser dokumenterare och ser påminnelsen vid årsskiftet (Fas 1–2).
5. Publika hemsidan visar bönetider och byter språk (Fas 4).

Allt annat, till exempel valideringsfel och varje rollkombination, hör hemma i nivå 2–3.

**Körmiljö.** E2E körs mot den riktiga backenden och en riktig Postgres, aldrig mot mockar:

- Backenden startas med `./mvnw spring-boot:test-run -Dspring-boot.run.profiles=e2e`, som startar
  `TestBackendApplication` med Testcontainers. Det kräver ingen separat databas och ingen kod som följer med i den
  byggda appen. Databasen är ny för varje start och rör aldrig dev-databasen i `compose.yaml`.
- Testkontona `admin` och `dokumenterare` skapas av migreringen utan lösenord. Därför finns hjälpklassen
  `E2eDataInitializer` (`se.ikft.e2e` i `src/test`, `@Profile("e2e")`): den sätter kända lösenord och skapar tre
  påhittade medlemmar. Den ligger inte i produktionskoden och är avstängd i den vanliga profilen.
  `E2eDataInitializerIntegrationTest` visar vad den gör och `E2eProfilAvstangdIntegrationTest` att den inte finns
  utan profilen. `application-e2e.properties` stänger av Compose-stödet, eftersom Testcontainers redan ger databasen.
  Lösenorden är publika, gäller bara E2E-databasen och står både i hjälpklassen och i `e2e/support/konton.ts`.
  Lösenordet i loggen vid uppstart (`AdminSetupCodeInitializer`) används aldrig i E2E, eftersom det vore skört att
  läsa loggar.
- Playwrights `webServer` startar frontenden (Vite). CI startar backenden före Playwright och väntar på
  `/actuator/health/readiness`, som blir UP först när alla `ApplicationRunner` har körts, alltså efter att profilen
  `e2e` har satt lösenorden. Jobbet ligger i [e2e.yml](../.github/workflows/e2e.yml). Det bygger backenden själv och
  körs därför vid sidan av backend-jobbet, inte efter det.

**Köra lokalt.** Docker ska vara igång, och dev-backenden på port 8080 ska vara stoppad.

1. `cd backend && ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=e2e` och vänta på `Started BackendApplication`.
2. `cd e2e && npm ci`, och första gången `npx playwright install chromium`.
3. `npm test` i `e2e/`. Starta inte om backenden mellan körningarna: testerna städar efter sig och går att köra om.

**Skrivregler.**

- Lokaliserare på roll, etikett och text (`getByRole`, `getByLabel`), aldrig CSS-väljare eller XPath.
  Hellre ett `data-testid` än ett skört val, men bara när roll eller etikett inte räcker.
- Inga `waitForTimeout`. Playwrights `expect` väntar själv. Ett test som behöver en paus har fel väntevillkor.
- Varje test skapar sin egen data med unika namn och är oberoende av ordning. Börja med `workers: 1` mot den
  delade databasen, och öka först när testerna är isolerade per arbetare.
- Inloggning görs en gång per roll i ett uppsättningssteg (`tests/auth.setup.ts`, via API:et) och sparas som
  `storageState` i `e2e/.auth/`. Ett test väljer roll med `test.use({ storageState: ADMIN.sparadInloggning })`. Ett
  test går igenom inloggningen i gränssnittet (`forsta-inloggningen.spec.ts`).
- Förberedelser och städning görs via API:et med `ApiSession` (`support/api.ts`), som loggar in på nytt för varje
  test. Själva flödet som testas går alltid genom gränssnittet. Admin skapar till exempel en medlem och utser den till
  dokumenterare via API:et, och det gör att Dokumenterare-kontot måste byta lösenord vid nästa inloggning.
- Ett test som ändrar ett delat konto sätter tillbaka det i `afterEach`, så att det går att köra om mot samma backend.
  Ett innehavarbyte gör kontots sparade inloggning ogiltig (tokenversionen räknas upp), så testet skapar om den.
- Playwrights webbläsare tillåter `Secure`-cookies på `localhost`, så `__Host-`-cookies i `storageState` fungerar över
  `http://localhost`.
- Projekt för Chromium på skrivbord och en mobilprofil (Pixel). Firefox och WebKit läggs till först om något
  visar behov.
- Ett tillgänglighetstest per huvudsida med `@axe-core/playwright`.
- `retries: 1` i CI, ingen lokalt. `trace: 'on-first-retry'`. Trace, video och HTML-rapport sparas som artefakt när
  något fallerar.

## Nivå 5: Playwright MCP och Claude-agenter

- **Playwright MCP** (`.mcp.json`) låter Claude öppna den körande appen, klicka igenom ett flöde och se fel
  direkt. Det används medan vi bygger en skärm, och för att ta fram ett nytt E2E-test. Ett MCP-klick
  upprepas inte i CI, så beviset är alltid ett fast test i `e2e/`.
- **`testgranskare`** (`.claude/agents/testgranskare.md`) granskar en ändring mot den här filen: saknas
  något av 401, 403 och gränsvärden, ligger testet på rätt nivå, städar det efter sig. Den skriver inga ändringar.
  Körs före varje PR, tillsammans med `/code-review`.
- **`e2e-utforskare`** (`.claude/agents/e2e-utforskare.md`) utforskar ett flöde med Playwright MCP och
  skriver sedan testet enligt reglerna ovan. Används först när admin-appen har en första sida.
- Agenternas resultat är underlag, inte godkännande. Det som avgör är att CI är grönt.

## Kontrakt mellan backend och frontend

TypeScript-typerna i admin-appen genereras ur backendens OpenAPI-beskrivning (springdoc och
`openapi-typescript`). Två incheckade filer håller ihop kontraktet, och CI kontrollerar båda, så att en ändrad
endpoint stoppar bygget i stället för att ge en tyst bugg:

1. `admin/src/api/openapi.json` skapas ur backendens kod. `OpenApiKontraktIntegrationTest` misslyckas när den
   skiljer sig från koden. Uppdatera den med `./mvnw test -Dtest=OpenApiKontraktIntegrationTest -Dopenapi.update=true`.
2. `admin/src/api/schema.d.ts` genereras ur den med `npm run gen:api` i `admin/`. Arbetsflödet `api-kontrakt`
   misslyckas när den inte är aktuell.

Beskrivningen är avstängd i drift (`OpenApiAvstangdIntegrationTest`). Alla svarsfält är obligatoriska utom de som är
markerade `@Schema(nullable = true)`.

Varje endpoint har också ett `default`-svar av typen `application/problem+json` med schemat `ProblemDetail`
(RFC 9457, med `errors` för ogiltiga fält och `mustChangePassword` för det tvingade lösenordsbytet). Det läggs till av
`OpenApiConfig`, så felkroppen är typad i klienten. `OpenApiKontraktIntegrationTest` kontrollerar att varje endpoint har
det, och att ett verkligt 400-svar bara har egenskaper som schemat beskriver. `ProblemDetailSchemaTest` gör samma sak för
säkerhetskedjans 401, 403 och 403 med `mustChangePassword`.

## CI

- **Backend:** `verify` på varje PR och på `main` ([backend-ci.yml](../.github/workflows/backend-ci.yml)).
  Test- och täckningsrapporter sparas som artefakt.
- **Admin-appen:** lint, typkontroll, Vitest och bygge ([admin-ci.yml](../.github/workflows/admin-ci.yml)). Jobbet
  behöver ingen backend, eftersom anropen mockas med MSW, så det körs vid sidan av backend-jobbet och inte efter det.
- **E2E:** Playwright mot backend, admin-app och databas ([e2e.yml](../.github/workflows/e2e.yml)). Rapport, trace
  och video sparas som artefakt. Alla jobb ska vara obligatoriska kontroller för att få merga till `main`.
- Obligatoriska kontroller och skydd av `main` ställs in i GitHub (Settings → Branches), inte i koden.

## Vad vi medvetet inte gör

- Ingen hård täckningsgräns och ingen mutationstestning. Det ger mer arbete än nytta i det här läget.
- Inga visuella ögonblicksbilder av admin-appen. Hemsidan kan få det i Fas 4 om utseendet blir viktigt.
- Inga prestandatester före Fas 7 (offline) eller ett faktiskt problem.
- Inga riktiga personer, aldrig, i testdata. Repot är publikt.
