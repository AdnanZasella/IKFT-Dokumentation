---
name: issue
description: Starta arbete med en GitHub-issue enligt projektets arbetsflöde
disable-model-invocation: true
argument-hint: <issue-nummer>
---

Vi ska jobba med issue #$ARGUMENTS.

1. Läs issuen med `gh issue view $ARGUMENTS` och de docs-filer den länkar till. Läs CLAUDE.md om du inte redan gjort det.
2. Kontrollera att vi står på en branch som heter feature/$ARGUMENTS-<kort-namn> eller fix/$ARGUMENTS-<kort-namn>. Om vi står på main: stanna och be mig skapa branchen.
3. Gör en plan i små steg (migrering → entitet → repository → service → controller → tester). För affärsregler och behörigheter: skriv testerna först.
4. Lista oklarheter och antaganden. Gissa inte.
5. Vänta på mitt godkännande innan du ändrar något.

Under genomförandet: ett steg i taget, kör `cd backend && ./mvnw verify` efter varje steg, och sammanfatta vad som ändrats.


// Kommentar under:

Så här använder du den:

Skapa branchen för issuen och starta en ny session:
powershell
git switch main
git pull
git switch -c feature/12-medlemsregister
claude
Tryck Shift+Tab till plan mode.
Skriv:
/issue 12
