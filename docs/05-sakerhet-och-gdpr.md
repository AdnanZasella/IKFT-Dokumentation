# Säkerhet, dataskydd och icke-funktionella krav

Del av [projektspecifikationen](00-oversikt.md).

## Säkerhet och dataskydd

**Autentisering och sessioner**
- Lösenord lagras hashat (t.ex. BCrypt), aldrig i klartext.
- JWT i en **HttpOnly, Secure cookie** med `SameSite=Strict`, satt endast för admin-subdomänen.
  Muterande anrop skyddas med CSRF-token.
- Rimlig giltighetstid och förnyelse (refresh) så att användare inte behöver logga in orimligt
  ofta på mobilen.
- **Innehavarbyte ogiltigförklarar sessioner** via tokenversion och kräver att lösenordet byts
  vid första inloggningen (se
  [Målgrupp och användarroller](01-dokumentationssystem.md#målgrupp-och-användarroller)). Så länge
  kontot är markerat med "måste byta lösenord" nekas alla anrop med 403 utom byte av lösenord,
  utloggning och `GET /auth/me`. Spärren ligger i backend, inte bara i gränssnittet.
- **Begränsning av inloggningsförsök** (t.ex. tillfällig spärr efter upprepade felaktiga
  försök), eftersom inloggningssidan nu nås från en publik hemsida.
- Valfritt men rekommenderat: **tvåstegsverifiering** för Admin-kontot.
- **Engångskoder** används för ett kontos första lösenord och som reservväg. Admin-kontots
  första kod skrivs i serverns logg när appen startar. Koder för Dokumenterare-kontot, och
  reservkoder, skapas av Admin. Bara en hash av koden lagras. Koden gäller 24 timmar (konfigurerbart) och kan bara användas en gång. Varje
  lösenord som sätts med en kod loggas, så att ett övertagande syns.
- **Återställning av Admin-lösenordet** kräver åtkomst till servern. Den körs som ett separat
  engångskommando, i en enda instans och utan webbserver, till exempel
  `java -jar backend.jar --ikft.auth.reset-admin-password=true --spring.main.web-application-type=none`.
  Lösenordet tas bort, alla sessioner loggas ut, en ny engångskod skrivs i loggen och kommandot
  avslutas. Därefter startas appen som vanligt och Admin sätter lösenordet med koden. Inställningen
  ska inte läggas i tjänstens konfiguration: appen avslutas med kod 0, och en tjänst som startas om
  automatiskt (t.ex. `restart: always` i Docker) skulle återställa lösenordet och ersätta koden vid
  varje omstart. Det finns ingen återställning via e-post, eftersom kontot delas och ingen
  e-postadress lagras. Serverns logg ska därför behandlas som hemlig.

**Den publika delen**
- Det publika API:et är skrivskyddat och returnerar bara publicerade poster och publika fält.
- All artikeltext saneras i backend ([Artiklar](02-hemsida.md#artiklar-arbetsflöde-och-säker-publicering)).
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

## Icke-funktionella krav

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
