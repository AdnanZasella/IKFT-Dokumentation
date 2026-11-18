# Frontend: admin-appen och hemsidan

Del av [projektspecifikationen](00-oversikt.md). Arkitekturen finns i
[Övergripande arkitektur](04-domanmodell-och-api.md#övergripande-arkitektur) och testerna i
[Teststrategi](07-teststrategi.md). Den här filen beskriver hur de två frontendarna byggs och hur de ska se ut.

Frontenden byggs i två spår. **Admin-appen** byggs först, eftersom backenden för Fas 1 redan finns och går att
testa mot. **Hemsidan** börjar som en designprototyp med exempeldata, så att föreningen kan tycka till om
utseendet innan det publika API:et (Fas 4) finns.

## Mappar

| Mapp | Innehåll |
|---|---|
| `admin/` | Admin-appen (React, Vite). |
| `web/` | Publik hemsida (Astro). |
| `e2e/` | Playwright-tester mot admin-appen och hemsidan ([Teststrategi](07-teststrategi.md#nivå-4-e2e-med-playwright)). |

## Gemensamt

- **TypeScript** överallt, med strikt typkontroll.
- **Kontraktet mot backenden** genereras ur OpenAPI (springdoc och `openapi-typescript`). Den genererade filen
  checkas in, och CI misslyckas om den inte är aktuell ([Kontrakt](07-teststrategi.md#kontrakt-mellan-backend-och-frontend)).
- **Logiska CSS-egenskaper** (start/end, inte left/right), så att samma design fungerar höger-till-vänster.
- **Tillgänglighet:** semantisk HTML, synlig fokusmarkering, kontrast enligt WCAG AA, och ett axe-test per huvudsida.
- **Inga tredjepartsskript** och inga externa typsnittstjänster. Typsnitt ligger som egna filer i appen.

## Admin-appen

### Teknik

| Del | Val |
|---|---|
| Ramverk | React och Vite. |
| Routing | React Router. |
| Datahämtning | TanStack Query med en typad klient (`openapi-fetch`). Cookies skickas med (`credentials: 'include'`). |
| Formulär | React Hook Form. Valideringen i backenden är sanningen, klientens validering är bara hjälp. |
| Stil | Tailwind CSS med egna designvariabler. Tillgängliga byggstenar (dialog, meny) från Radix. |
| Test | Vitest, Testing Library och MSW ([Nivå 3](07-teststrategi.md#nivå-3-frontend-komponenter)). |

### Inloggning, CSRF och sessioner

- Appen börjar varje sidladdning med `GET /auth/me`. Det sätter CSRF-cookien och visar om användaren är inloggad.
- Alla muterande anrop skickar `X-XSRF-TOKEN` ur cookien `__Host-XSRF-TOKEN`. Klienten läser den vid varje anrop.
- Ett 401 försöker först `POST /auth/refresh` en gång. Lyckas det inte skickas användaren till inloggningen.
- Ett 403 med `mustChangePassword: true` skickar användaren till sidan för lösenordsbyte. Där finns bara
  lösenordsbyte och utloggning, tills flaggan är nollställd.
- Navigeringen döljer det som rollen inte får använda, men det är bara en hjälp: behörigheten kontrolleras alltid i
  backenden.
- **Inloggningssidan** (`/login`) hämtar kontot först, eftersom det anropet ger CSRF-cookien som inloggningen kräver.
  Den som redan är inloggad skickas till startsidan. Efter en lyckad inloggning läggs kontot ur svaret i cachen
  (`ME_KEY`) och användaren skickas vidare. Kontot hämtas inte på nytt: cachen har `staleTime: Infinity` och skulle
  annars säga "utloggad" tills hämtningen var klar.
- **Fel** visas i klartext: backendens `detail` för fel i begäran (till exempel "Fel användarnamn eller lösenord."),
  en egen text för serverfel och nätverksfel, och vid 429 hur länge spärren varar (ur `Retry-After`). Fel som backenden
  pekar ut per fält (`errors`) visas vid fältet. Knappen är avstängd medan formuläret skickas.
- **Det tvingade lösenordsbytet** (`/change-password`) har tre fält (nuvarande, nytt, upprepa nytt) och utloggning.
  Klienten kontrollerar bara att fälten är ifyllda och att det nya lösenordet skrevs lika två gånger. Längd och övriga
  regler kommer ur backendens svar, så de kan ändras i konfigurationen utan att klienten följer med.

### Utseende

Admin-appen är ett verktyg och behöver inte se ut som hemsidan: lugn, läsbar och utan prydnad.

- **Designvariabler** ligger i `admin/src/styles.css` (Tailwinds `@theme`): ljus pappersbakgrund, mörk bläckfärg, en
  dämpad grön accent och en röd för fel. Alla färgpar för text ger minst 4,5:1 i kontrast och kanter på fält minst 3:1
  (WCAG AA). Axe-testet i `e2e/` fångar om en ändring bryter det.
- **Typsnitt:** systemets, så ingen typsnittstjänst anropas och inga typsnittsfiler behövs.
- **Former:** tunna kanter, små rundningar, inga skuggor och inga gradienter. Synlig fokusmarkering på allt som går att
  tangentbordsstyra.
- **Logiska egenskaper** (`ps-`, `pe-`, `text-start`), så att samma utseende fungerar höger-till-vänster.
- **Byggstenar** i `admin/src/ui/`: `TextField` (etikett och fel kopplade till fältet), `Button` och `Alert`. Radix
  läggs till när första dialogen eller menyn behövs (skalet, #49).

### Körmiljö

- **Lokalt:** Vite-servern proxar `/api` till backenden på `localhost:8080`, så webbläsaren ser en enda ursprung och
  cookies (`Secure`, `SameSite=Strict`) fungerar. Chromium och Firefox tillåter `Secure`-cookies på `localhost`.
  Kontrollerat i Chromium via proxyn (#47): `__Host-XSRF-TOKEN` sätts redan av det första `GET /auth/me` (även när
  svaret är 401), inloggningscookien och förnyelsecookien sätts och skickas, och CSRF-token byts vid inloggning och
  förnyelse. Därför läser klienten token vid varje anrop.
- **Drift:** `admin.<domän>.se` serverar de byggda filerna och `/api` från samma ursprung bakom en omvänd proxy. Ingen CORS.

### Layout

En app med **vertikal navigering till vänster** och innehållet till höger, som i ett SaaS-system. På mobil blir
navigeringen en utfällbar meny. Innehållet i varje sektion har en sidrubrik, en rad med handlingar och sedan
tabellen eller formuläret. Navigeringen visar bara det rollen får använda och bara det som är byggt.

| Sektion | Roll | Innehåll | Fas |
|---|---|---|---|
| Översikt | Admin, Dokumenterare | Aktiv dokumenterare, påminnelse om nästa års dokumenterare (Admin), snabbval. Dokumenteraren får senare dagens sammankomster. | 1 |
| Sammankomster | Admin, Dokumenterare | Dagens sammankomster, närvaro, lista och detalj med bidragsstatus. | 2 |
| Möten | Admin, Dokumenterare | Möten, protokoll, beslut och bilagor. | 3 |
| Medlemmar | Admin (Dokumenterare ser namn och åldersgrupp) | Register, föreningsroller, moskéavgift via FIFS, inaktivera och aktivera. | 1 |
| Dokumenterare | Admin | Utse ordinarie och tillfällig dokumenterare, avsluta tillfälligt uppdrag. | 1 |
| Verksamheter | Admin | Grupper med schema och publiceringsflagga. | 2 |
| Hemsidan | Admin | Bönetider (import och rättning), artiklar, bilder. | 4–5 |
| Rapporter | Admin | Verksamhetsberättelse, statistik, export. | 5 |
| Ändringslogg | Admin | Ändringar per post. | 1 |
| Konto | Admin, Dokumenterare | Byt lösenord, skapa engångskod (Admin), logga ut. | 1 |

Dokumenteraren har bara ett urval: Översikt, Sammankomster, Möten, Medlemmar (skrivskyddat) och Konto.

### Första sidorna

1. Inloggning, och därefter tvingat lösenordsbyte (klart, #48).
2. Skal med navigering, och Översikt.
3. Medlemmar, Dokumenterare, Ändringslogg och Konto.

Det första E2E-flödet är första inloggningen med tvingat lösenordsbyte
([Teststrategi](07-teststrategi.md#nivå-4-e2e-med-playwright)).

## Hemsidan

### Teknik

- **Astro**, statiskt förrenderad där det går, med små öar av JavaScript bara där det behövs (nästa bön,
  språkväljare).
- **Adresser:** `/sv/...` och `/ar/...` ([Flerspråkighet](02-hemsida.md#flerspråkighet-svenska-och-arabiska)).
  Fasta texter i översättningsfiler. `dir="rtl"` på arabiska.
- **Data:** tills det publika API:et finns läser sidorna påhittad exempeldata ur JSON-filer i `web/`. Samma
  datalager byts sedan mot anrop till `/api/v1/public/*`. Exempeldatan innehåller aldrig riktiga personer.
- **Bönetider:** dagens tider med nästa bön markerad, månadstabell och en utskriftsvänlig version. Föreningens
  Excel-fil importeras av backenden i Fas 4 ([Bönetider](02-hemsida.md#bönetider-import-och-visning)). Tills
  dess visas exempeldata.

### Sidor

Enligt [Sidor](02-hemsida.md#sidor): Startsida, Bönetider, Verksamhet, Artiklar, Stöd moskén, Bli medlem och
Om oss/Kontakt, samt en diskret länk till inloggningen i sidfoten.

### Utseende

Föreningen har ännu inte gett någon förebild (mappen `design/` är tom), så första versionen är ett förslag som
de får tycka till om. Riktningen: **ett studie- och lärandeställe, varmt och seriöst**. Det ska inte likna en
webbshop, ett storföretag eller en generisk AI-mall.

- **Känsla:** kursplan, bibliotek och lärosäte. Lugn och läsbar, med text och tabeller i centrum.
- **Färger:** varm, ljus pappersbakgrund, mörk bläckfärg för text och en dämpad grön som accent. Få färger.
- **Typsnitt:** en seriffad rubrikfont och en ren sans för brödtext. För arabiska ett typsnitt med bra Naskh-stöd.
  Typsnitten ligger lokalt i appen.
- **Former:** tunna linjer, tabeller och mycket luft. Inga kort med skuggor, inga gradienter, inga
  glaseffekter, inga stockbilder och inga rader med generiska ikoner.
- **Verksamhet** visas som ett **schema**: veckodag, tid, målgrupp och plats, som i en kursplan.
- **Startsidan** börjar med dagens bönetider och nästa bön, sedan veckans verksamhet och senaste artiklarna.
  "Stöd moskén" och "Bli medlem" är två tydliga men lugna knappar.
- **Mobil först.** De flesta besökare använder telefon.

När föreningen har skickat en förebild, logotyp och texter läggs de i `design/` och utseendet justeras mot dem.

## Beslut som är tagna

- Admin-appen är React och Vite, hemsidan är Astro (beslutat 2026-11-18).
- Admin-appen byggs före hemsidan, men hemsidan får en prototyp med exempeldata så att föreningen kan reagera tidigt.
- OpenAPI-typerna genereras med springdoc och `openapi-typescript`.

## Öppet

- Föreningens önskemål om utseende, logotyp och texter ([Öppna frågor](06-oppna-fragor.md)).
- Om hemsidan och admin-appen ska dela designvariabler (färger, typsnitt) eller ha var sin uppsättning.
  Admin-appen är ett verktyg och behöver inte se ut som hemsidan.
