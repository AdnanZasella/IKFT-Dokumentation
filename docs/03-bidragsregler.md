# Bidragsregler och medlemskap

Del av [projektspecifikationen](00-oversikt.md).

## Trollhättans bidragsregler — vad som gäller för en moské/islamisk kulturförening

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
| Verksamhetsbidrag (barn/unga) | Minst 10 medlemmar 7–25 år; ledarledda sammankomster enligt [kraven på dokumentationssystemet](01-dokumentationssystem.md#vilka-krav-ska-dokumentationssystemet-stödja) | 31 maj (för nästa år) | Deltagartillfällen per medlem och sammankomst, med åldersfilter och tak |
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

## Medlemskap: föreningen och moskéavgiften via FIFS

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
  kommunens bidrag. Det beror på föreningens stadgar och kommunens bedömning (se [Öppna frågor](06-oppna-fragor.md)).
- **Medlemsregistret i systemet är föreningens eget.** Admin lägger in medlemmar manuellt.
  Om föreningen får en medlemslista från FIFS kan en importfunktion byggas senare, på samma sätt
  som bönetidsimporten.
- På hemsidan visas därför två tydligt skilda knappar: **"Stöd moskén"** (länk till FIFS) och
  **"Bli medlem"** (föreningens egen process, som bestäms senare).
