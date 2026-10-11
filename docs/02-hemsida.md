# Hemsida

Del av [projektspecifikationen](00-oversikt.md).

## Vilka krav ska hemsidan stödja?

- **Bönetider** för dagen och för hela månaden/året, baserade på moskéns egna bönetider som
  importeras från en fil (se [Bönetider: import och visning](#bönetider-import-och-visning)).
- **Verksamhet** — moskéns återkommande verksamheter med schema (t.ex. "Arabiska, tisdagar
  18:00–19:30") samt enstaka evenemang som föreläsningar.
- **Artiklar** — krönikor och artiklar om händelser i moskén, skrivna av olika skribenter och
  publicerade av Admin (se [Artiklar](#artiklar-arbetsflöde-och-säker-publicering)).
- **Stöd moskén** — en knapp som leder till FIFS sida för moskéavgift via Skatteverket
  (`https://medlem.fifs.se/IKFT`). Hemsidan hanterar inga uppgifter i den processen.
- **Bli medlem** — en separat väg för medlemskap i föreningen. Exakt hur det ska gå till är inte
  bestämt ännu (se [Medlemskap](03-bidragsregler.md#medlemskap-föreningen-och-moskéavgiften-via-fifs) och [Öppna frågor](06-oppna-fragor.md)).
- **Svenska och arabiska**, med korrekt höger-till-vänster-layout för arabiska (se [Flerspråkighet](#flerspråkighet-svenska-och-arabiska)).
- **Logga in** — en diskret länk för Admin och Dokumenterare. Inga andra har konton.

---

## Hemsidan (publik del)

### Sidor

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

### Vad som aldrig visas publikt

Närvarolistor, medlemsregister, protokoll, bidragsstatistik, interna anteckningar och
"Registrerat av"-uppgifter visas aldrig på hemsidan. Varje verksamhet och evenemang har en
**publiceringsflagga**, och bara publicerade poster och de publika fälten (namn, beskrivning,
schema, målgrupp, plats) exponeras via det publika API:et.

### Inloggning från hemsidan

Hemsidan har en länk "Logga in" (t.ex. i sidfoten). Inloggningen och hela det interna systemet
ligger på en **egen subdomän**, t.ex. `admin.<domän>.se` (se [Övergripande arkitektur](04-domanmodell-och-api.md#övergripande-arkitektur)). Efter inloggning
möts Admin/Dokumenteraren av systemets översikt med dokumentationsverktygen, och Admin även av
publiceringsverktygen för hemsidan.

---

## Bönetider: import och visning

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

## Artiklar: arbetsflöde och säker publicering

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

## Flerspråkighet: svenska och arabiska

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
