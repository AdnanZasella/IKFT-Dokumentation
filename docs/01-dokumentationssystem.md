# Dokumentationssystem

Del av [projektspecifikationen](00-oversikt.md).

## Vilka krav ska dokumentationssystemet stödja?

Baserat på Trollhättans stads riktlinjer för föreningsbidrag (se [Bidragsregler](03-bidragsregler.md)) behöver
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

## Målgrupp och användarroller

| Roll | Konto | Vad rollen gör |
|---|---|---|
| Besökare | Inget | Läser hemsidan: bönetider, verksamhet, artiklar, stöd/medlemskap. |
| Admin | 1 konto | Allt: medlemsregister, dokumentation, rapporter, publicering på hemsidan (bönetider, verksamhet, artiklar). |
| Dokumenterare | 1 konto, kopplat till ett uppdrag | Registrerar sammankomster, möten och närvaro. |

**Skribenter** (de som skriver krönikor och artiklar) har inga konton. De skickar sin text till
Admin, som publicerar den (se [Artiklar](02-hemsida.md#artiklar-arbetsflöde-och-säker-publicering)). Skribentens namn anges som text på artikeln.

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

**Byte av innehavare kräver lösenordsbyte vid första inloggningen.** Eftersom kontona delas över
tid vet systemet bara vem som *borde* använda kontot, inte vem som faktiskt är inloggad. För att
"Registrerat av" ska vara trovärdigt gäller därför:
- När Dokumenterare-kontot byter innehavare (ordinarie, tillfälligt eller när ett tillfälligt
  uppdrag löper ut) **loggas alla befintliga sessioner för kontot ut** och kontot markeras med
  **"måste byta lösenord"**. Lösenordet behålls: den förra innehavaren lämnar över det till den
  nya, som måste byta det vid första inloggningen. Tills dess kan kontot bara byta lösenord,
  logga ut och läsa sina egna kontouppgifter.
- Dokumenteraren är oftast någon i styrelsen, så föreningen godtar risken att den förra
  innehavaren kan logga in innan den nya har bytt lösenord.
- Byts innehavaren till samma medlem som redan har uppdraget händer ingenting.
- **Admin-kontot byter inte innehavare.**
- Ingen engångskod skapas vid innehavarbyte. Engångskoder används bara för det första
  Admin-lösenordet och som reservväg, t.ex. när ett lösenord har glömts bort (se
  [Säkerhet och dataskydd](05-sakerhet-och-gdpr.md#säkerhet-och-dataskydd)).

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

## PWA- och offline-strategi (admin-appen)

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

## Rapport-/exportfunktion

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
