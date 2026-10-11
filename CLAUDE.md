# IKFT

Hemsida och internt dokumentationssystem för Islamiska Kulturföreningen i Trollhättan.
Specifikationen finns i [docs/00-oversikt.md](docs/00-oversikt.md), som länkar till
`docs/01-*.md` till `docs/06-*.md`. Läs rätt delfil innan du ändrar något som berör
verksamhetsregler, roller eller API.

## Struktur

- `backend/` – Spring Boot 4.1.1, Java 21, Maven, paket `se.ikft`.
- `docs/` – specifikationen, uppdelad i sju filer.
- Frontend (publik hemsida och admin-app) finns inte än.

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

Docker krävs. Lokalt startar `spring-boot-docker-compose` Postgres 18 från `compose.yaml`,
och testerna använder Testcontainers.

## Konventioner

- Databasändringar görs med Flyway. JPA körs med `open-in-view: false` och
  `ddl-auto: validate`.
- Alla felsvar är RFC 9457 `ProblemDetail` från `common.error.GlobalExceptionHandler`, även 401
  och 403. Kasta `NotFoundException` eller `ConflictException` för att få 404 respektive 409.
- Tidslogik använder den injicerade `Clock` (Europe/Stockholm), aldrig `now()` utan argument.
- API:et är versionerat under `/api/v1`, publikt under `/api/v1/public`. Behörighet kontrolleras
  i backend med Spring Security, aldrig bara i gränssnittet.
- Autentisering är stateless JWT i HttpOnly-cookie.
- Bidragsregler (åldersgränser, minsta längd och deltagarantal, tak) ska vara konfigurerbara,
  inte hårdkodade.
- Dataminimering: lagra födelsedatum, aldrig personnummer. Medlemmar inaktiveras, de raderas inte.
- Artikeltext saneras i backend innan den sparas.

## Docs

- Specifikationen ligger i delfiler. Redigera rätt delfil och länka mellan filer med relativa
  länkar, inte med avsnittsnummer.

## Repo

- Repot är publikt: inga hemligheter och inga riktiga personuppgifter, inte heller i testdata.
- Ingen licensfil ska läggas till.

## Git

- Commitmeddelanden är på svenska med prefix som `docs:` och `chore:`.
