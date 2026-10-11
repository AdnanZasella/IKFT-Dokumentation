# Hemsida och dokumentationssystem för Islamiska Kulturföreningen i Trollhättan

## Bakgrund och syfte

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

## Arkitektur i korthet

Systemet består av fyra delar: en publik hemsida (`<domän>.se`), en admin-app
(`admin.<domän>.se`), ett REST-API i Spring Boot och en PostgreSQL-databas som delas av båda
delarna. Se [Övergripande arkitektur](04-domanmodell-och-api.md#övergripande-arkitektur) för
detaljer.

---

## Föreslagen utvecklingsordning (faser)

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

## Innehåll

- [01 – Dokumentationssystem](01-dokumentationssystem.md): krav, roller, dokumenteraruppdrag, PWA/offline, rapporter
- [02 – Hemsida](02-hemsida.md): sidor, bönetidsimport, artiklar, svenska/arabiska
- [03 – Bidragsregler](03-bidragsregler.md): Trollhättans regler och medlemskap/FIFS
- [04 – Domänmodell och API](04-domanmodell-och-api.md): arkitektur, stack, domänmodell, endpoints
- [05 – Säkerhet och GDPR](05-sakerhet-och-gdpr.md): säkerhet, dataskydd, icke-funktionella krav
- [06 – Öppna frågor](06-oppna-fragor.md): frågor till kommunen och föreningen
