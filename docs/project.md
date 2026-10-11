# Project.md — Hemsida och dokumentationssystem för Islamiska Kulturföreningen i Trollhättan

## 1. Bakgrund och syfte

Projektet består av två delar som delar samma backend och databas:

1. **En publik hemsida** för moskén, där vem som helst kan se bönetider, moskéns verksamhet
   (t.ex. ungdomsträffar, matte-, arabiska- och koranlektioner, föreläsningar), läsa artiklar
   som moskén publicerat, stödja moskén och hitta information om hur man blir medlem.
   Hemsidan är på **svenska och arabiska**.
2. **Ett internt dokumentationssystem** som bara Admin och Dokumenterare når genom att logga in
   via hemsidan. Där registreras sammankomster, närvaro, möten och protokoll, och där
   publiceras innehållet på hemsidan (bönetider, verksamhet, artiklar).

Föreningen bedriver löpande verksamhet (aktiviteter, samlingar, möten) och behöver enligt
Trollhättans kommuns riktlinjer för föreningsbidrag kunna dokumentera och redovisa denna
verksamhet årligen. Idag hanteras detta manuellt, vilket gör det lätt att glömma bort att
dokumentera i tid, tappa bort underlag, eller hamna i tidsnöd inför årsredovisning och
bidragsansökan.

Syftet med dokumentationssystemet är att föreningen löpande — under eller direkt efter varje
sammankomst och möte — registrerar vad som skett, vilka som deltog, och vem som ansvarade.
Systemet sammanställer sedan informationen till underlag för verksamhetsberättelse och
bidragsredovisning till kommunen.

Eftersom hemsidan och systemet bygger på **samma data** behöver informationen bara föras in en
gång: en verksamhet som läggs upp för att visas på hemsidan är samma verksamhet som sedan
dokumenteras internt när den genomförs.

Systemet är inte tänkt att ersätta bokföring/ekonomisk redovisning — det fokuserar på
**verksamhetsdokumentation**: aktiviteter, närvaro, möten och protokoll. Ekonomisk redovisning
och revisionsberättelse hanteras även fortsättningsvis separat (t.ex. av kassören), men
projektet bör hålla dörren öppen för att koppla på det senare om föreningen vill.

---

## 2. Vilka krav ska systemet stödja?

### 2.1 Hemsidan

- **Bönetider** för dagen och för hela månaden/året, baserade på moskéns egna bönetider som
  importeras från en fil (se avsnitt 9).
- **Verksamhet** — moskéns återkommande verksamheter med schema (t.ex. "Arabiska, tisdagar
  18:00–19:30") samt enstaka evenemang som föreläsningar.
- **Artiklar** — krönikor och artiklar om händelser i moskén, skrivna av olika skribenter och
  publicerade av Admin (se avsnitt 10).
- **Stöd moskén** — en knapp som leder till FIFS sida för moskéavgift via Skatteverket
  (`https://medlem.fifs.se/IKFT`). Hemsidan hanterar inga uppgifter i den processen.
- **Bli medlem** — en separat väg för medlemskap i föreningen. Exakt hur det ska gå till är inte
  bestämt ännu (se avsnitt 2b och 17).
- **Svenska och arabiska**, med korrekt höger-till-vänster-layout för arabiska (se avsnitt 11).
- **Logga in** — en diskret länk för Admin och Dokumenterare. Inga andra har konton.

### 2.2 Dokumentationssystemet

Baserat på Trollhättans stads riktlinjer för föreningsbidrag (se avsnitt 2a) behöver
föreningen kunna visa:

- **Regelbunden, dokumenterad verksamhet.** Bidrag bedöms bland annat utifrån antal
  deltagartillfällen och sammankomster under året — inte bara att aktiviteter ägt rum, utan att
  de går att räkna och styrka i efterhand.
- **Åldersbaserad bidragsberäkning.** Verksamhetsbidraget för barn- och ungdomsverksamhet gäller
  åldrarna **7–25 år** och räknas per deltagartillfälle. En sammankomst räknas bara om den är
  **ledarledd**, varar **minst 60 minuter** och har **minst 3 deltagare i åldern 7–25 år**.
  Taket är **högst 3 aktiviteter per vecka i högst 40 veckor**, vilket ger högst 120
  deltagartillfällen per medlem och år. Systemet ska kunna räkna fram detta per medlem och per
  sammankomst, inte bara ge en totalsumma.
- **Bidragsgrundande medlemmar.** Endast medlemmar som är matrikelförda, har betalat beslutad
  medlemsavgift, deltar regelbundet och är **boende i Trollhättans kommun** räknas. Personer som
  enbart deltar i öppen verksamhet räknas inte. Systemet behöver därför veta medlemsavgiftsstatus
  per år och hemkommun för varje medlem.
- **Årsmöteshandlingar.** Protokoll, verksamhetsberättelse, ekonomisk redovisning och
  revisionsberättelse ska kunna presenteras och godkännas på årsmötet. Systemet **genererar**
  själv verksamhetsberättelsen (från registrerade sammankomster/möten) och årsmötesprotokollet
  (skrivet direkt i systemet eller uppladdat som fil). Ekonomisk redovisning och
  revisionsberättelse tas fram utanför systemet, men kan **bifogas som filer** så att alla
  handlingar finns samlade på ett ställe.
- **Styrelsens sammansättning och uppdrag.** Verksamhetsberättelsen ska visa vilka personer
  som haft vilka uppdrag i styrelsen under året, vilket kräver att systemet håller reda på
  roller över tid, inte bara aktuell status.
- **Spårbarhet.** Varje registrerad post visar vem som förde in den och när. Kommunen har rätt
  att ta del av medlemsförteckning och underlag, och föreningen ska spara medlemsmatrikel och
  underlag för verksamhetsbidraget i **minst fyra år** efter verksamhetsårets slut.

Systemet ska alltså inte bara vara ett fritt anteckningsverktyg, utan strukturera
dokumentationen så att den direkt går att omvandla till det underlag kommunen efterfrågar.

---

## 2a. Trollhättans bidragsregler — vad som gäller för en moské/islamisk kulturförening

**Källa:** *Riktlinjer/beräkningsgrunder för föreningsbidrag*, antagna av kommunfullmäktige
2017-02-06 (Kfn 2016/00450), publicerade på trollhattan.se. Det är den senaste version som
hittats publicerad. Föreningen bör bekräfta med Kultur- och fritidsförvaltningen
(Föreningsstöd) att den fortfarande gäller och om några demokrativillkor eller andra krav har
tillkommit sedan dess. Systemet ska därför ha **bidragsreglerna som konfigurerbara värden**
(åldersgränser, minsta deltagarantal, minsta längd, tak per vecka/år), inte hårdkodade.

**Grundkrav för att få bidrag över huvud taget:**
- Antagna stadgar, stadgeenligt vald styrelse och revisorer, årsmöte där verksamhets-,
  ekonomi- och revisionsberättelse föredras och godkänns.
- Föreningen ska vara uppbyggd och fungera enligt vedertagna demokratiska principer och bör
  tillhöra en riksorganisation.
- Verksamhet i kommunen i minst 6 månader, organisationsnummer, plus- eller bankgiro,
  registrering i Trollhättans Stads föreningsregister.
- Kassabok enligt bokföringslagen. Kommunen har rätt att granska räkenskaper, verifikationer
  och medlemsförteckning.
- Felaktiga uppgifter kan leda till återbetalningsskyldighet — ett starkt skäl till att
  systemets siffror måste vara spårbara ner till enskild närvaropost.

**Bidragsformer som är relevanta för föreningen:**

| Bidrag | Vad det kräver | Sista ansökan | Vad systemet behöver leverera |
|---|---|---|---|
| Verksamhetsbidrag (barn/unga) | Minst 10 medlemmar 7–25 år; ledarledda sammankomster enligt avsnitt 2.2 | 31 maj (för nästa år) | Deltagartillfällen per medlem och sammankomst, med åldersfilter och tak |
| Lokalbidrag | Egen planerad barn- och ungdomsverksamhet i lokalen minst 3 gånger/vecka, årskontrakt | 31 maj | Antal sammankomster per vecka i lokalen |
| Kulturbidrag | Allmänkulturell verksamhet (t.ex. sång, litteratur, kulturarv), oavsett medlemmarnas ålder | 31 maj | Kulturaktiviteter separat redovisade |
| Lovbidrag | Verksamhet under sport-, sommar- och höstlov | Ansökan 6 v före lov, redovisning 1 mån efter | Sammankomster filtrerade på lovperiod |
| Bidrag för särskilda insatser | T.ex. integration, inkludering, förebyggande arbete, unga som inte är föreningsaktiva | Löpande | Projektmärkta sammankomster med egen rapport |
| Driftbidrag till organisationer | För organisationer som inte passar i ordinarie bidragssystem | 31 oktober | Verksamhetsberättelse |

Till ansökningarna med sista dag 31 maj ska bifogas **verksamhetsplan för nästkommande år**
samt **verksamhets-, ekonomisk och revisionsberättelse för föregående år**. Systemet påminner
Admin i god tid före dessa datum.

**Särskilt för en moské/religiös förening:**
- Trollhättans riktlinjer utesluter inte uttryckligen trossamfund eller religiösa föreningar,
  men bidragen är inriktade på **fritids-, ungdoms- och kulturverksamhet**. Det är rimligt att
  anta att rent religiös verksamhet — t.ex. fredagsbön eller koranlektioner — inte bedöms som
  bidragsgrundande, medan t.ex. läxhjälp, mattelektioner, idrott, kulturkvällar, ungdomsträffar
  och lovaktiviteter kan vara det. **Detta bör bekräftas med kommunen.**
- Bidrag utgår inte till förening som för **motsvarande verksamhet** redan får bidrag av
  skattemedel från annat håll. Två fall är aktuella:
  - Moskén är ansluten till **FIFS**, ett trossamfund som får statligt stöd via Myndigheten för
    stöd till trossamfund (SST).
  - Om någon verksamhet (t.ex. arabiskalektioner) bedrivs i samarbete med ett **studieförbund**
    (t.ex. Ibn Rushd) får den redan folkbildningsbidrag.
  Systemet måste kunna visa vilken verksamhet som finansieras var, så att samma verksamhet inte
  redovisas två gånger.
- **Konsekvens för systemet:** varje verksamhet får en **kategori** (t.ex. barn/ungdom,
  utbildning, kultur, lovaktivitet, särskild insats, religiös verksamhet, öppen verksamhet,
  intern) och kan markeras som **studieförbundsverksamhet**. Rapporten räknar bara de
  kategorier som är markerade som bidragsgrundande. Religiös verksamhet dokumenteras
  fortfarande (den hör hemma i verksamhetsberättelsen), men hålls isär i bidragsunderlaget.

---

## 2b. Medlemskap: föreningen och moskéavgiften via FIFS

Det finns två olika saker som lätt blandas ihop:

- **Moskéavgift via FIFS** ("Stöd moskén"). Den som är minst 18 år kan via
  `medlem.fifs.se/IKFT` ge samtycke med BankID till att 1 % av den beskattningsbara inkomsten
  dras via Skatteverket och förs vidare till moskén genom FIFS. Uppgifterna delas med
  Skatteverket och den valda moskén.
- **Medlemskap i föreningen (IKFT).** Det är föreningens medlemmar som räknas för kommunens
  bidrag. Kommunen kräver att medlemmen är matrikelförd i föreningen och har betalat
  föreningens beslutade medlemsavgift.

Konsekvenser:
- **Barn och ungdomar (7–17 år) kan inte gå via FIFS**, men det är just de som ger
  verksamhetsbidrag. Föreningen behöver därför ett eget medlemskap som fungerar för alla
  åldrar, med vårdnadshavares medgivande för minderåriga.
- Det är oklart om moskéavgift via FIFS räknas som "betald medlemsavgift i föreningen" för
  kommunens bidrag. Det beror på föreningens stadgar och kommunens bedömning (se avsnitt 17).
- **Medlemsregistret i systemet är föreningens eget.** Admin lägger in medlemmar manuellt.
  Om föreningen får en medlemslista från FIFS kan en importfunktion byggas senare, på samma sätt
  som bönetidsimporten.
- På hemsidan visas därför två tydligt skilda knappar: **"Stöd moskén"** (länk till FIFS) och
  **"Bli medlem"** (föreningens egen process, som bestäms senare).

---

## 3. Målgrupp och användarroller

| Roll | Konto | Vad rollen gör |
|---|---|---|
| Besökare | Inget | Läser hemsidan: bönetider, verksamhet, artiklar, stöd/medlemskap. |
| Admin | 1 konto | Allt: medlemsregister, dokumentation, rapporter, publicering på hemsidan (bönetider, verksamhet, artiklar). |
| Dokumenterare | 1 konto, kopplat till ett uppdrag | Registrerar sammankomster, möten och närvaro. |

**Skribenter** (de som skriver krönikor och artiklar) har inga konton. De skickar sin text till
Admin, som publicerar den (se avsnitt 10). Skribentens namn anges som text på artikeln.

- **Admin (1 konto):** knutet till en funktion i föreningen (t.ex. ordförande eller den som är
  administrativt ansvarig), inte till en specifik individ.
- **Dokumenterare (1 konto, kopplat till ett aktivt uppdrag):** snarare än att vara knutet till
  en fast person är kontot knutet till ett **uppdrag** — en specifik medlem som Admin utser till
  "aktiv dokumenterare" för en period. Uppdraget, inte inloggningen, avgör vem som räknas som
  ansvarig.

**Årligt uppdrag, inte permanent tilldelning.** Från och med 1 januari varje år räknas
dokumenterare-uppdraget som obestämt igen, oavsett om det blir samma person som föregående år
eller inte. Admin måste aktivt utse (eller bekräfta) vem som är dokumenterare för det nya året.
Om årsskiftet passerar utan att någon är utsedd:
- blockeras nya registreringar av sammankomster/möten för Dokumenterare-kontot tills uppdraget
  är satt, **och**
- Admin ser en tydlig påminnelse i gränssnittet redan innan 1 januari, om nästa års
  dokumenterare inte är förbestämd.

**Byte av dokumenterare under året** kan ske på två sätt:
- **Tillfälligt** (t.ex. vid sjukdom/semester) — uppdraget ges till en annan medlem med ett
  bestämt slutdatum, och rollen återgår automatiskt till den ordinarie dokumenteraren när
  perioden passerat.
- **Permanent framöver** — avslutar det nuvarande uppdraget direkt och sätter en ny ordinarie
  dokumenterare, gällande tills vidare eller till nästa årsskifte.

**Byte av innehavare kräver nytt lösenord.** Eftersom kontona delas över tid vet systemet bara
vem som *borde* använda kontot, inte vem som faktiskt är inloggad. För att "Registrerat av" ska
vara trovärdigt gäller därför:
- När ett uppdrag byter innehavare (ordinarie, tillfälligt eller när ett tillfälligt uppdrag
  löper ut) **loggas alla befintliga sessioner för kontot ut** och kontot kräver ett **nytt
  lösenord**, som sätts av den nya innehavaren via en engångskod som Admin delar ut.
- Samma sak gäller Admin-kontot när innehavaren byts (t.ex. efter årsmötesval).

**Automatiskt ifyllt "Registrerat av".** Fältet fylls i automatiskt utifrån vem som innehar
uppdraget just nu, istället för att skrivas in manuellt vid varje registrering.

**Admin som reserv.** Om inget dokumenterare-uppdrag är satt kan Admin själv registrera
sammankomster och möten under tiden.

**Redigeringsfönster.** Dokumenteraren kan redigera poster som registrerats under det egna
pågående uppdraget i **14 dagar** efter registrering (konfigurerbart av Admin). Därefter, eller
om uppdraget har avslutats, kan endast Admin ändra posten. Alla ändringar loggas.

Notera att **medlemmar i föreningen inte har egna användarkonton**. Medlemsregistret är helt
separat från inloggningssystemet — de allra flesta medlemmar förekommer bara som namn i
närvarolistor.

---

## 4. Hemsidan (publik del)

### 4.1 Sidor

| Sida | Innehåll | Datakälla |
|---|---|---|
| Startsida | Dagens bönetider, nästa bön, kommande verksamhet denna vecka, senaste artiklar, knapparna "Stöd moskén" och "Bli medlem" | Bönetider, Verksamhet, Artiklar |
| Bönetider | Dagens tider samt månadstabell; utskrivbar | Bönetider |
| Verksamhet | Lista över återkommande verksamheter (ungdomsträff, matte, arabiska, koran, m.m.) med schema, målgrupp och plats, samt kommande evenemang (t.ex. föreläsningar) | Verksamhet/grupp, Sammankomst |
| Artiklar | Lista och enskild artikel med skribent, datum och språk | Artikel |
| Stöd moskén | Kort förklaring och länk till `medlem.fifs.se/IKFT` | Statisk text |
| Bli medlem | Information om föreningens medlemskap (process bestäms senare) | Statisk text |
| Om oss / Kontakt | Adress, karta, kontaktuppgifter, styrelse | Statisk text + Föreningsroller |
| Logga in | Diskret länk till admin-delen | — |

### 4.2 Vad som aldrig visas publikt

Närvarolistor, medlemsregister, protokoll, bidragsstatistik, interna anteckningar och
"Registrerat av"-uppgifter visas aldrig på hemsidan. Varje verksamhet och evenemang har en
**publiceringsflagga**, och bara publicerade poster och de publika fälten (namn, beskrivning,
schema, målgrupp, plats) exponeras via det publika API:et.

### 4.3 Inloggning från hemsidan

Hemsidan har en länk "Logga in" (t.ex. i sidfoten). Inloggningen och hela det interna systemet
ligger på en **egen subdomän**, t.ex. `admin.<domän>.se` (se avsnitt 5). Efter inloggning
möts Admin/Dokumenteraren av systemets översikt med dokumentationsverktygen, och Admin även av
publiceringsverktygen för hemsidan.

---

## 5. Övergripande arkitektur

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

## 6. Teknisk stack

- **Backend:** Java, Spring Boot, Spring Data (JPA), Spring Security (JWT, stateless).
- **Databas:** PostgreSQL.
- **Publik hemsida:** Next.js eller Astro (server- eller förrenderad), med stöd för svenska och
  arabiska (se avsnitt 11).
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

## 7. Domänmodell (konceptuell, utan kod)

### 7.1 Föreningsdata (internt)

- **Medlem** — förnamn, efternamn, **födelsedatum** (fullständigt personnummer lagras inte),
  adress/hemkommun (för att avgöra om medlemmen är boende i Trollhättan), medlem-sedan-datum,
  status (aktiv/inaktiv), **betald medlemsavgift per år**, samt för minderåriga uppgift om att
  vårdnadshavares medgivande finns. Åldern räknas alltid **per sammankomstens datum**. En medlem
  kan ha en eller flera **föreningsroller** över tid (t.ex. ordförande 2025, kassör 2026), som
  används i verksamhetsberättelsen och på hemsidans "Om oss"-sida (endast namn och roll).
- **Användarkonto** — Admin och Dokumenterare. Identitet, lösenordshash, roll, **tokenversion**
  (räknas upp vid innehavarbyte så att gamla sessioner blir ogiltiga) och **innehavare** — en
  länk till vilken medlem som just nu ansvarar för kontot.
- **Dokumenteraruppdrag** — kopplar Dokumenterare-kontot till en medlem under en period
  (startdatum, valfritt slutdatum, typ: ordinarie/tillfällig). Återställs varje årsskifte.

### 7.2 Verksamhet och dokumentation

- **Verksamhet/grupp** — en återkommande verksamhet, t.ex. "Ungdomsträff", "Arabiska nybörjare",
  "Matte åk 7–9", "Koranlektioner". Innehåller namn och beskrivning **på svenska och arabiska**,
  målgrupp, **schema** (veckodag, starttid, sluttid, period), plats, ordinarie ledare,
  **kategori** (se avsnitt 2a), eventuell studieförbundskoppling, en lista med ordinarie
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

### 7.3 Hemsidans innehåll

- **Bönetid** — en rad per datum: fajr, soluppgång (shuruq), dhuhr, asr, maghrib, isha, samt
  valfritt iqama-tider och tid för fredagsbön (jumu'ah). Importeras från fil och kan rättas
  manuellt per dag (se avsnitt 9).
- **Artikel** — titel, ingress, brödtext (sanerad HTML), skribentens namn (text, inget konto),
  publiceringsdatum, **språk** (svenska/arabiska), valfri koppling till en översättning av samma
  artikel, valfri huvudbild, status (utkast/publicerad/avpublicerad).
- **Mediefil** — bilder som används i artiklar eller på hemsidan, med alt-text och
  samtyckesuppgift (se avsnitt 14).

Varje sammankomst, möte och artikel lagrar **vem som registrerade posten** och när. Värdet
hämtas automatiskt från kontots innehavare — aldrig bara "Admin" eller "Dokumenterare" som
etikett.

---

## 8. API-struktur (endpoints, översikt)

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
| `POST /auth/set-password` | öppen (med engångskod) | Ny innehavare sätter eget lösenord. |

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
| `POST /documenter-assignment` | Admin | Utse ordinarie dokumenterare (loggar ut kontot, engångskod för nytt lösenord). |
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

---

## 9. Bönetider: import och visning

Moskén har **egna bönetider för hela året** i en fil (troligen Excel `.xlsx` eller `.csv`).
Systemet beräknar alltså inte bönetider själv, utan importerar moskéns tider.

**Importflöde:**
1. Admin laddar upp årets fil.
2. Systemet läser filen och visar en **förhandsgranskning** innan något sparas.
3. Systemet **validerar**:
   - att varje dag i året finns med exakt en gång,
   - att tiderna är giltiga och i rätt ordning (fajr < shuruq < dhuhr < asr < maghrib < isha),
   - att övergångarna till och från sommartid ser rimliga ut (tiderna ska vara i svensk lokal
     tid, `Europe/Stockholm`),
   - och visar tydligt vilka rader som har fel.
4. Admin bekräftar. Befintliga tider för samma period ersätts; den gamla versionen sparas i
   ändringsloggen.
5. Admin kan i efterhand **rätta enskilda dagar** manuellt.

Systemet påminner Admin under hösten om att nästa års bönetider behöver importeras.

**Kolumnmappning:** exakt format bestäms när föreningens fil finns tillgänglig. Importen ska
klara att kolumnerna har svenska eller arabiska rubriker genom en enkel mappning som sätts en
gång.

**Visning på hemsidan:** dagens tider med markering av nästa bön, en månadstabell, och en
utskriftsvänlig version. Valfritt: hijri-datum bredvid det gregorianska datumet.

---

## 10. Artiklar: arbetsflöde och säker publicering

**Arbetsflöde:**
1. En skribent (vem som helst — inget konto behövs) skriver en krönika eller artikel och
   skickar filen till Admin.
2. Admin skapar ett **utkast** på något av två sätt:
   - klistrar in texten i systemets textredigerare, eller
   - laddar upp Word-filen (`.docx`) och systemet konverterar den automatiskt till ett utkast.
3. Admin fyller i titel, skribentens namn, språk, eventuell huvudbild, och förhandsgranskar.
4. Admin **publicerar**. Artikeln kan senare avpubliceras eller rättas.

**Säker publicering (sanering).** Text som kommer från en fil eller inklistring kan innehålla
dold kod. Systemet skyddar automatiskt mot detta — Admin behöver inte göra något:
- Backend tillåter bara en **vitlista** av säker formatering: rubriker, stycken, fetstil,
  kursiv, listor, citat, länkar och bilder.
- Allt annat (skript, inbäddade objekt, formulär, stilattribut, okända taggar) tas bort
  automatiskt **innan** texten sparas.
- Länkar får bara peka på `http`/`https`, och externa länkar öppnas med säkra attribut.
- Saneringen sker i backend, så den gäller även om någon skulle anropa API:et direkt.

---

## 11. Flerspråkighet: svenska och arabiska

- **Språk:** svenska (standard) och arabiska på den publika hemsidan. Admin-appen är på
  svenska till att börja med.
- **Adresser:** `/sv/...` och `/ar/...`, med en språkväljare i sidhuvudet.
- **Layout:** arabiska visas höger-till-vänster (`dir="rtl"`). Layouten byggs med logiska
  CSS-egenskaper (start/end istället för left/right) så att samma design fungerar i båda
  riktningarna. Ett typsnitt med bra stöd för arabiska väljs.
- **Fasta texter** (menyer, knappar, bönernas namn) ligger i översättningsfiler.
- **Innehåll:**
  - Verksamheter/grupper har namn och beskrivning på båda språken.
  - Artiklar skrivs på ett språk och kan länkas till en översättning om en sådan finns. Om en
    artikel bara finns på ett språk visas den ändå, med en markering om språket.
  - Bönetider är språkoberoende; bara rubrikerna översätts.
- **Datum och siffror** visas enligt valt språk (t.ex. arabiska månadsnamn), men tider visas
  alltid i svensk lokal tid.

---

## 12. PWA- och offline-strategi (admin-appen)

PWA-stödet gäller bara admin-appen. Den publika hemsidan är en vanlig webbplats.

- **Installation:** ett webbmanifest gör att Admin/Dokumenterare kan lägga admin-appen på
  mobilens hemskärm. Det är valfritt — appen fungerar lika bra i webbläsaren via hemsidans
  "Logga in"-länk.
- **Offline-registrering:** nya sammankomster och närvaroposter sparas lokalt först. Finns
  uppkoppling skickas posten direkt; annars läggs den i en lokal kö och synkas automatiskt när
  uppkopplingen återställs. Dagens schema och gruppernas deltagarlistor cachas så att
  dokumenteraren kan bocka av närvaro även utan uppkoppling.
- **Avgränsning:** offline-stödet omfattar närvaro och nya sammankomster. Medlemshantering,
  möten, publicering och rapporter kräver uppkoppling.
- **Dubblettskydd:** varje post får en unik identifierare på klienten innan den skickas.
- **Synk med utgången inloggning:** klienten försöker först förnya token. Lyckas inte det
  ligger posterna kvar i kön tills användaren loggat in igen. Köade poster raderas aldrig från
  enheten förrän servern bekräftat dem.
- **Poster skapade under ett uppdrag som hunnit ändras:** varje offline-post lagrar när den
  skapades och vilket uppdrag som var aktivt då. "Registrerat av" sätts till den dåvarande
  innehavaren. Om kontot har bytt innehavare kan köade poster bara skickas in efter att Admin
  godkänt dem.
- **Årsskifte offline:** en post daterad efter 31 december som skapas utan ett giltigt uppdrag
  för det nya året hamnar i en lista som Admin kan granska och godkänna.

---

## 13. Rapport-/exportfunktion

Rapportfunktionen ska:

- Låta Admin välja ett datumintervall (typiskt ett kalenderår).
- Sammanställa: antal genomförda sammankomster (totalt, per verksamhet och per kategori), antal
  möten, antal bidragsgrundande sammankomster, totalt antal deltagartillfällen, samt
  deltagartillfällen per medlem.
- Tydligt visa **varför** en sammankomst eller ett deltagartillfälle inte räknas, t.ex. "under 60
  minuter", "färre än 3 deltagare 7–25 år", "ingen ledare angiven", "ej bidragsgrundande
  kategori", "studieförbundsverksamhet", "medlem ej boende i Trollhättan", "medlemsavgift ej
  betald", "överskrider 3/vecka" eller "överskrider 120/år". Registreringen i sig blockeras
  aldrig.
- Varna om föreningen har färre än 10 bidragsgrundande medlemmar 7–25 år.
- Lista styrelsens sammansättning under perioden.
- Generera ett PDF-dokument för bidragsansökan/redovisning, formaterat så nära Trollhättans
  mall som möjligt, där sammankomsterna listas med löpnummer.
- Exportera även som CSV/tabell för kommunens e-tjänst.
- Påminna Admin inför ansökningsdatum (31 maj, 31 oktober, samt inför lov).

---

## 14. Säkerhet och dataskydd

**Autentisering och sessioner**
- Lösenord lagras hashat (t.ex. BCrypt), aldrig i klartext.
- JWT i en **HttpOnly, Secure cookie** med `SameSite=Strict`, satt endast för admin-subdomänen.
  Muterande anrop skyddas med CSRF-token.
- Rimlig giltighetstid och förnyelse (refresh) så att användare inte behöver logga in orimligt
  ofta på mobilen.
- **Innehavarbyte ogiltigförklarar sessioner** via tokenversion.
- **Begränsning av inloggningsförsök** (t.ex. tillfällig spärr efter upprepade felaktiga
  försök), eftersom inloggningssidan nu nås från en publik hemsida.
- Valfritt men rekommenderat: **tvåstegsverifiering** för Admin-kontot.

**Den publika delen**
- Det publika API:et är skrivskyddat och returnerar bara publicerade poster och publika fält.
- All artikeltext saneras i backend (avsnitt 10).
- Säkerhetshuvuden (Content Security Policy, HSTS m.m.) på båda domänerna, striktast på
  admin-delen.
- Inga formulär på den publika sidan som samlar in personuppgifter i första versionen.
  ("Stöd moskén" går till FIFS; "Bli medlem" bestäms senare.)

**Övrigt**
- Behörighetskontroll på API-nivå, inte bara i gränssnittet.
- Endast Admin kan skapa användarkonton — ingen självregistrering.
- All trafik över HTTPS.

**Dataskydd (GDPR).** Ett medlemsregister i en moské/islamisk förening avslöjar indirekt
medlemmarnas religiösa övertygelse, vilket är en **känslig personuppgift** enligt
dataskyddsförordningen (artikel 9). En ideell förening med religiöst syfte får behandla sådana
uppgifter om sina medlemmar inom ramen för sin verksamhet, så länge uppgifterna inte lämnas ut
utanför föreningen utan samtycke. I praktiken:
- **Dataminimering:** bara födelsedatum, inte personnummer; adress/hemkommun endast för
  bidragsberäkningen.
- **Begränsad åtkomst:** Dokumenterare ser bara namn och åldersgrupp.
- **Drift inom EU/EES**, krypterade backuper, personuppgiftsbiträdesavtal med
  driftsleverantören.
- **Lagringstid:** medlemsmatrikel och bidragsunderlag sparas minst fyra år efter
  verksamhetsårets slut, därefter anonymiseras eller raderas personuppgifterna enligt en rutin
  som föreningen beslutar.
- **Information till medlemmar** om vilka uppgifter som registreras, varför, och att kommunen
  har rätt att granska medlemsförteckningen.
- **Hemsidan** behöver en integritetspolicy. Om hemsidan bara använder nödvändiga cookies (och
  ingen statistik- eller spårningstjänst) behövs ingen cookie-banner.

**Bilder.** Föreningen har ännu inte bestämt hur bilder från verksamheten ska hanteras. Tills
dess gäller som utgångspunkt att **inga bilder där barn kan identifieras publiceras** på
hemsidan. Varje uppladdad bild har ett fält för samtycke så att en tydligare policy kan läggas
till senare.

---

## 15. Icke-funktionella krav

- **Skala:** en liten förening (tiotals till några hundra medlemmar, ett fåtal inloggade
  användare). Den publika sidan kan få fler besökare, särskilt runt bönetider och högtider, men
  hanteras enkelt med cache.
- **Prestanda:** hemsidan ska ladda snabbt på mobil; bönetidssidan är den viktigaste.
- **Budget:** låga och förutsägbara driftskostnader.
- **Underhåll:** en enda utvecklare, åtminstone inledningsvis — enkelhet och tydlig struktur
  prioriteras.
- **Konfigurerbarhet:** bidragsparametrar ska kunna ändras utan kodändring.
- **Tillgänglighet:** hemsidan bör följa grundläggande tillgänglighetsriktlinjer (WCAG):
  tillräcklig kontrast, alt-text på bilder, fungerande tangentbordsnavigering.
- **Backup:** regelbunden, testad backup av databas och filer — detta *är* föreningens
  officiella dokumentation.

---

## 16. Föreslagen utvecklingsordning (faser)

1. **Grundstruktur:** autentisering (inkl. innehavarbyte och lösenordsbyte), roller,
   medlemsregister (födelsedatum, hemkommun, medlemsavgift), ändringslogg.
2. **Verksamheter och sammankomster (online):** grupper med schema, dagens sammankomster,
   närvaro, kategorier, ledare.
3. **Möten, protokoll och beslut**, bilagor.
4. **Publik hemsida, första version:** bönetidsimport och visning, verksamhetssidan,
   "Stöd moskén"-länk, "Om oss", svenska och arabiska, inloggningslänk.
5. **Artiklar och rapporter:** artikelpublicering (inkl. Word-import och sanering),
   verksamhetsberättelse som PDF, statistikvy, CSV-export, frysta ögonblicksbilder,
   påminnelser.
6. **PWA-paketering av admin-appen:** installationsbarhet, ikon, grundläggande caching.
7. **Offline-stöd** för närvaro och sammankomster, inklusive bakgrundssynk.

Dokumentationsgrunden (fas 1–2) byggs först eftersom den är det som ger underlag för bidrag och
dessutom är datakällan för hemsidans verksamhetssida. Hemsidan kommer därefter tidigt (fas 4)
eftersom den är det föreningen och besökarna ser. Offline-stödet läggs sist eftersom det är
tekniskt mest komplext.

---

## 17. Öppna frågor

**Med Trollhättans stad (Kultur- och fritidsförvaltningen, Föreningsstöd):**
- Gäller riktlinjerna från 2017-02-06 fortfarande, eller finns en nyare version (t.ex. med
  demokrativillkor)?
- Vilka av föreningens verksamheter räknas som bidragsgrundande — t.ex. mattelektioner,
  arabiskalektioner, koranlektioner, ungdomsträffar, föreläsningar?
- Räknas moskéavgift via FIFS/Skatteverket som "betald medlemsavgift" i föreningen?
- Hur ser gränsdragningen ut med tanke på att FIFS får statligt stöd via SST?
- Finns en specifik mall/blankett eller e-tjänst för redovisningen som exporten bör efterlikna?

**Med föreningen:**
- **Medlemskap:** hur ska man bli medlem i föreningen (blankett, formulär på hemsidan, något
  annat)? Vilken medlemsavgift gäller, och hur hanteras barn och ungdomar (vårdnadshavares
  medgivande)? *Användaren återkommer.*
- Får föreningen en medlemslista från FIFS, och i så fall i vilket format?
- **Bönetidsfilen:** exakt filformat och kolumner (ett exempel behövs). Ingår iqama-tider och
  tid för fredagsbön?
- **Bilder:** hur ska bilder från verksamheten hanteras, särskilt bilder på barn? *Användaren
  återkommer.*
- Vilka verksamheter finns idag, med schema, ledare och målgrupp — och bedrivs någon av dem
  tillsammans med ett studieförbund?
- Vilka kategorier ska vara markerade som bidragsgrundande?
- Är 14 dagar ett rimligt redigeringsfönster för dokumenteraren?
- Har föreningen redan en domän och/eller en befintlig hemsida som ska ersättas?
- Var ska driften ske — finns en befintlig leverantör inom EU, eller ska det väljas från
  grunden?
- Ska ekonomisk redovisning vara helt utanför systemet permanent?
- Hur länge ska personuppgifter sparas efter kommunens fyraårskrav, och vem i föreningen
  ansvarar för GDPR-frågor?
