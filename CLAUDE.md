# IKFT

Hemsida och internt dokumentationssystem för Islamiska Kulturföreningen i Trollhättan.
Specifikationen finns i [docs/00-oversikt.md](docs/00-oversikt.md), som länkar till
`docs/01-*.md` till `docs/06-*.md`. Läs rätt delfil innan du ändrar något som berör
verksamhetsregler, roller eller API.

## Struktur

- `backend/` – Spring Boot 4.1.1, Java 21, Maven, paket `se.ikft`.
- `docs/` – specifikationen, uppdelad i sju filer.
- Frontend (publik hemsida och admin-app) finns inte än.

## Kommandon

Kör i `backend/`:

- `./mvnw spring-boot:run` – starta lokalt
- `./mvnw test` – kör tester
- `./mvnw verify` – bygg och testa

Docker krävs. Lokalt startar `spring-boot-docker-compose` Postgres 18 från `compose.yaml`,
och testerna använder Testcontainers.

## Konventioner

- Databasändringar görs med Flyway. JPA körs med `open-in-view: false`.
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

## Git

- Commitmeddelanden är på svenska med prefix som `docs:` och `chore:`.
