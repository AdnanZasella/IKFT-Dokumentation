# Arkitektur, domänmodell och API

Del av [projektspecifikationen](00-oversikt.md).

## Övergripande arkitektur

Systemet består av fyra delar:

1. **Publik hemsida (`<domän>.se`)** — byggd med ett ramverk som renderar sidorna på servern
   eller i förväg (t.ex. **Next.js** eller **Astro**). Det ger snabb laddning, bra synlighet i
   sökmotorer och sidor som går att dela. Hemsidan hämtar data från backendens **publika,
   skrivskyddade API** och cachar svaren (bönetider och verksamhet ändras sällan).
2. **Admin-app (`admin.<domän>.se`)** — en React-app för inloggade användare med
   dokumentationsverktygen och publiceringsverktygen. Den kan fortfarande **installeras som PWA**
   på mobilen (ikon på hemskärmen, offline-registrering), men behöver inte göra det — den
   fungerar som en vanlig webbsida också.
3. **Backend (Spring Boot REST API)** — affärslogik, datalagring, autentisering/behörighet,
   import av bönetider, artikelhantering och rapportgenerering.
4. **Databas (PostgreSQL)** — gemensam för båda delarna.

**Varför egen subdomän för admin-delen:**
- Inloggningscookien gäller bara `admin.<domän>.se` och skickas aldrig med till den publika
  sidan, så en bugg i den publika delen kan inte komma åt admin-sessionen.
- Admin-delen kan ha striktare säkerhetsregler (Content Security Policy, inga
  tredjepartsskript) utan att påverka den publika sidan.
- Den publika sidan kan cachas aggressivt utan risk att privat data hamnar i cachen.

Backend är stateless (JWT i HttpOnly-cookie). Det publika API:et kräver ingen inloggning och
är skrivskyddat; allt som ändrar data kräver inloggning.

```
Besökare ──> <domän>.se (Next.js/Astro) ──> /api/v1/public/* (skrivskyddat, cachat)
                                                   │
Admin/Dok. ──> admin.<domän>.se (React, PWA) ──> /api/v1/*  (inloggning krävs)
                                                   │
                                            Spring Boot ──> PostgreSQL + fillagring
```

---

## Teknisk stack

- **Backend:** Java, Spring Boot, Spring Data (JPA), Spring Security (JWT, stateless).
- **Databas:** PostgreSQL.
- **Publik hemsida:** Next.js eller Astro (server- eller förrenderad), med stöd för svenska och
  arabiska (se [Flerspråkighet](02-hemsida.md#flerspråkighet-svenska-och-arabiska)).
- **Admin-app:** React, byggd som PWA (webbmanifest + service worker för installation, caching
  och bakgrundssynk).
- **API:** REST, JSON, versionerat under `/api/v1/...`. Publika endpoints under
  `/api/v1/public/...`.
- **Filhantering:** uppladdade dokument och bilder (protokoll, artikelbilder, importfiler) på
  disk med backup-rutin eller i S3-kompatibel objektlagring inom EU.
- **Import:** Apache POI (Excel `.xlsx`) och CSV-läsning för bönetider; docx-konvertering
  (t.ex. docx4j eller motsvarande) för artiklar.
- **HTML-sanering:** ett vitlistebaserat bibliotek (t.ex. OWASP Java HTML Sanitizer eller
  jsoup Safelist) i backend för all artikeltext.
- **Rapportgenerering:** serversidan sammanställer verksamhetsberättelsen och exporterar den
  som PDF, formaterad så nära Trollhättans mall som möjligt.
- **Drift/DevOps:** containerbaserad drift (Docker), CI/CD-pipeline, en enkel och billig
  driftsmiljö (t.ex. en VPS) **inom EU/EES**. Den publika sidan kan med fördel ligga bakom en
  CDN/cache.

---

## Domänmodell (konceptuell, utan kod)

### Föreningsdata (internt)

- **Medlem** — förnamn, efternamn, **födelsedatum** (fullständigt personnummer lagras inte),
  adress/hemkommun (för att avgöra om medlemmen är boende i Trollhättan), medlem-sedan-datum,
  status (aktiv/inaktiv), **betald medlemsavgift per år**, samt för minderåriga uppgift om att
  vårdnadshavares medgivande finns. Åldern räknas alltid **per sammankomstens datum**. En medlem
  kan ha en eller flera **föreningsroller** över tid (t.ex. ordförande 2025, kassör 2026), som
  används i verksamhetsberättelsen och på hemsidans "Om oss"-sida (endast namn och roll).
- **Användarkonto** — Admin och Dokumenterare. Identitet, lösenordshash, roll, **tokenversion**
  (räknas upp vid innehavarbyte och lösenordsbyte så att gamla sessioner blir ogiltiga),
  flaggan **"måste byta lösenord"** (sätts vid innehavarbyte, nollställs när lösenordet byts) och
  **innehavare** — en länk till vilken medlem som just nu ansvarar för kontot.
- **Dokumenteraruppdrag** — kopplar Dokumenterare-kontot till en medlem under en period
  (startdatum, valfritt slutdatum, typ: ordinarie/tillfällig). Återställs varje årsskifte.

### Verksamhet och dokumentation

- **Verksamhet/grupp** — en återkommande verksamhet, t.ex. "Ungdomsträff", "Arabiska nybörjare",
  "Matte åk 7–9", "Koranlektioner". Innehåller namn och beskrivning **på svenska och arabiska**,
  målgrupp, **schema** (veckodag, starttid, sluttid, period), plats, ordinarie ledare,
  **kategori** (se [Bidragsregler](03-bidragsregler.md)), eventuell studieförbundskoppling, en lista med ordinarie
  deltagare, samt en **publiceringsflagga** för hemsidan.
- **Sammankomst** (tidigare "Aktivitet") — ett enskilt tillfälle: datum, **starttid och
  sluttid**, plats, **ledare**, tema/innehåll, kategori, och en närvarolista (eller ett
  sammanfattat antal). Kan vara:
  - ett tillfälle som **genereras från en grupps schema** — då är ledare, tid, kategori och
    deltagarlista redan ifyllda och dokumenteraren bockar bara av närvaro, eller
  - ett **enstaka evenemang** (t.ex. en föreläsning) som skapas direkt och kan publiceras på
    hemsidan i förväg.

  En sammankomst har status **planerad → genomförd** (eller **inställd**). Bara genomförda
  sammankomster räknas i rapporter. Varje närvaropost räknas som ett "deltagartillfälle".
  Systemet **beräknar** om sammankomsten är bidragsgrundande: rätt kategori, ledarledd, minst
  60 minuter och minst 3 deltagare 7–25 år. Varje genomförd sammankomst får ett **löpnummer per
  år**, så att den kan identifieras i ansökan och vid en kontroll.
- **Möte** — styrelsemöte, årsmöte eller medlemsmöte, med typ, datum, plats, ordförande,
  sekreterare, protokolljusterare, närvarolista och en strukturerad lista av beslut. Protokollet
  kan skrivas direkt i systemet eller laddas upp som fil.
- **Bilaga** — filer kopplade till en sammankomst eller ett möte (internt, aldrig publikt).
- **Ändringslogg** — varje skapande, ändring och inaktivering av sammankomst, närvaro, möte,
  medlem, bönetid eller artikel loggas med vem (innehavare), när och vad som ändrades
  (före/efter).
- **Verksamhetsberättelse/rapport** — ingen egen lagrad tabell, utan en beräknad
  sammanställning över ett valt datumintervall. När en rapport har lämnats in till kommunen kan
  Admin **frysa en ögonblicksbild** (PDF + underliggande siffror).

### Hemsidans innehåll

- **Bönetid** — en rad per datum: fajr, soluppgång (shuruq), dhuhr, asr, maghrib, isha, samt
  valfritt iqama-tider och tid för fredagsbön (jumu'ah). Importeras från fil och kan rättas
  manuellt per dag (se [Bönetider](02-hemsida.md#bönetider-import-och-visning)).
- **Artikel** — titel, ingress, brödtext (sanerad HTML), skribentens namn (text, inget konto),
  publiceringsdatum, **språk** (svenska/arabiska), valfri koppling till en översättning av samma
  artikel, valfri huvudbild, status (utkast/publicerad/avpublicerad).
- **Mediefil** — bilder som används i artiklar eller på hemsidan, med alt-text och
  samtyckesuppgift (se [Säkerhet och dataskydd](05-sakerhet-och-gdpr.md#säkerhet-och-dataskydd)).

Varje sammankomst, möte och artikel lagrar **vem som registrerade posten** och när. Värdet
hämtas automatiskt från kontots innehavare — aldrig bara "Admin" eller "Dokumenterare" som
etikett.

---

## API-struktur (endpoints, översikt)

Alla paths ligger under `/api/v1`. Fältnivå/request-body specificeras i kod när modellen är
avstämd.

**Publikt API (ingen inloggning, skrivskyddat, cachat)**

| Metod & path | Beskrivning |
|---|---|
| `GET /public/prayer-times?date=` | Bönetider för ett datum (standard: idag). |
| `GET /public/prayer-times?month=` | Bönetider för en månad. |
| `GET /public/groups?lang=` | Publicerade verksamheter med schema. |
| `GET /public/events?from=&to=&lang=` | Publicerade kommande evenemang. |
| `GET /public/articles?lang=&page=` | Publicerade artiklar. |
| `GET /public/articles/{slug}` | En artikel. |
| `GET /public/board` | Aktuell styrelse (namn och roll). |

**Autentisering — Fas 1**

| Metod & path | Roll | Beskrivning |
|---|---|---|
| `POST /auth/login` | öppen | Loggar in, sätter HttpOnly-cookie. Begränsat antal försök. |
| `POST /auth/logout` | inloggad | Rensar cookien. |
| `POST /auth/refresh` | inloggad | Förnyar token. |
| `GET /auth/me` | inloggad | Kontots roll och innehavarens namn. |
| `POST /auth/set-password` | öppen (med engångskod) | Sätter lösenordet med en engångskod: ett kontos första lösenord och reservväg om lösenordet glömts bort. |

**Medlemmar — Fas 1**

| Metod & path | Roll | Beskrivning |
|---|---|---|
| `GET /members` | Admin, Dokumenterare | Lista medlemmar. Dokumenterare ser bara namn och åldersgrupp. |
| `POST /members` | Admin | Lägg till ny medlem. |
| `GET /members/{id}` | Admin | Se grunduppgifter. |
| `PUT /members/{id}` | Admin | Uppdatera. |
| `DELETE /members/{id}` | Admin | Inaktivera (ingen hård radering). |
| `POST /members/{id}/roles` | Admin | Registrera föreningsroll för en period. |
| `PUT /members/{id}/fees/{year}` | Admin | Markera medlemsavgift som betald. |

**Dokumenteraruppdrag — Fas 1**

| Metod & path | Roll | Beskrivning |
|---|---|---|
| `GET /documenter-assignment/current` | Admin, Dokumenterare | Aktiv dokumenterare (eller att inget är satt). |
| `POST /documenter-assignment` | Admin | Utse ordinarie dokumenterare (loggar ut kontot, kräver lösenordsbyte vid nästa inloggning). |
| `POST /documenter-assignment/temporary` | Admin | Utse tillfällig dokumenterare med slutdatum. |
| `DELETE /documenter-assignment/temporary` | Admin | Avsluta tillfälligt uppdrag i förtid. |

**Verksamheter och sammankomster — Fas 2**

*Affärsregel: att skapa sammankomster och möten nekas om inget dokumenteraruppdrag är aktivt
och den inloggade användaren inte är Admin.*

| Metod & path | Roll | Beskrivning |
|---|---|---|
| `GET /groups` | Admin, Dokumenterare | Lista verksamheter/grupper. |
| `POST /groups` | Admin | Skapa verksamhet med schema, kategori, språkversioner. |
| `PUT /groups/{id}` | Admin | Uppdatera verksamhet (inkl. publiceringsflagga). |
| `PUT /groups/{id}/participants` | Admin, Dokumenterare | Uppdatera ordinarie deltagare. |
| `GET /sessions?from=&to=&groupId=` | Admin, Dokumenterare | Lista sammankomster (planerade och genomförda). |
| `GET /sessions/today` | Admin, Dokumenterare | Dagens sammankomster enligt scheman — startpunkten för dokumenteraren. |
| `POST /sessions` | Admin, Dokumenterare | Skapa sammankomst/evenemang (från grupp eller fristående). |
| `GET /sessions/{id}` | Admin, Dokumenterare | Detaljer inkl. närvaro och bidragsstatus med förklaring. |
| `PUT /sessions/{id}` | Admin, Dokumenterare* | Uppdatera (*inom 14 dagar under eget uppdrag). |
| `POST /sessions/{id}/attendance` | Admin, Dokumenterare | Registrera närvaro. |
| `POST /sessions/{id}/complete` / `/cancel` | Admin, Dokumenterare | Markera som genomförd eller inställd. |
| `POST /sessions/sync` | Admin, Dokumenterare | Tar emot köade poster skapade offline (Fas 7). |

**Möten och bilagor — Fas 3**

| Metod & path | Roll | Beskrivning |
|---|---|---|
| `GET /meetings` | Admin, Dokumenterare | Lista möten. |
| `POST /meetings` | Admin, Dokumenterare | Skapa möte. |
| `GET /meetings/{id}` | Admin, Dokumenterare | Protokoll, beslut, närvaro. |
| `PUT /meetings/{id}` | Admin, Dokumenterare* | Uppdatera (*inom 14 dagar under eget uppdrag). |
| `POST /meetings/{id}/decisions` | Admin, Dokumenterare | Lägg till beslutspunkt. |
| `POST /meetings/{id}/attendance` | Admin, Dokumenterare | Registrera närvaro. |
| `POST /sessions/{id}/attachments` | Admin, Dokumenterare | Ladda upp fil till sammankomst. |
| `POST /meetings/{id}/attachments` | Admin, Dokumenterare | Ladda upp fil till möte. |
| `GET /attachments/{id}` | Admin, Dokumenterare | Hämta fil. |

**Hemsidans innehåll — Fas 4**

| Metod & path | Roll | Beskrivning |
|---|---|---|
| `POST /prayer-times/import` | Admin | Ladda upp årsfil; returnerar förhandsgranskning och valideringsfel. |
| `POST /prayer-times/import/{importId}/confirm` | Admin | Bekräfta och spara importen. |
| `PUT /prayer-times/{date}` | Admin | Rätta en enskild dag. |
| `GET /articles` | Admin | Lista alla artiklar inkl. utkast. |
| `POST /articles` | Admin | Skapa artikel (text inklistrad i editor). |
| `POST /articles/import-docx` | Admin | Skapa utkast från uppladdad Word-fil. |
| `PUT /articles/{id}` | Admin | Uppdatera artikel. |
| `POST /articles/{id}/publish` / `/unpublish` | Admin | Publicera eller avpublicera. |
| `POST /media` | Admin | Ladda upp bild (med alt-text och samtyckesuppgift). |

**Rapporter, logg och inställningar — Fas 5**

| Metod & path | Roll | Beskrivning |
|---|---|---|
| `GET /reports/verksamhetsberattelse?from=&to=` | Admin | PDF-sammanställning för perioden. |
| `GET /reports/stats?year=` | Admin | JSON-statistik per medlem och sammankomst. |
| `GET /reports/export?year=&format=csv` | Admin | Tabellexport för kommunens e-tjänst. |
| `POST /reports/snapshots` | Admin | Frys en inlämnad rapport. |
| `GET /audit-log?entity=&id=` | Admin | Ändringslogg för en post. |
| `GET/PUT /settings/grant-rules` | Admin | Bidragsparametrar och redigeringsfönster. |

Behörighetsstyrningen hanteras i backend via Spring Security, inte enbart i frontend.
