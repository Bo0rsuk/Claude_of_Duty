# Squawk - scenariusz filmu demo (5:00)

Film jest główną podstawą oceny we wszystkich kategoriach, więc każda scena odpowiada na konkretne kryterium z `Docs/requirements/`. Wskazówki dla nagrywającego są po polsku, tekst lektora po angielsku (do wklejenia w ElevenLabs).

- **Limit:** 5:00. Scenariusz celuje w 4:50, żeby został zapas.
- **Lektor:** ok. 640 słów. Przy tempie 150 słów na minutę to ok. 4:15 mowy, przy 140 ok. 4:35; reszta to pauzy na akcje na ekranie. Zmierzcie faktyczne tempo wybranego głosu na scenie 1, zanim wygenerujecie resztę.
- **Zasada montażu:** najpierw wygenerujcie głos dla każdej sceny osobno, potem przytnijcie obraz do długości audio. Odwrotnie się nie da.

## Plan scen

| # | Czas | Scena | Kryterium |
|---|---|---|---|
| 0 | 0:00-0:15 | Hak: problem i obietnica | - |
| 1 | 0:15-1:00 | Burst na żywo, jeden alert, okno alertu | Don't flood people, live feed, UX |
| 2 | 1:00-1:50 | Reguła z asystentem AI, zapis bez restartu | Flexibility (rules), Innovation |
| 3 | 1:50-2:20 | Źródła i kreator nowego źródła | Flexibility (sources, schemas) |
| 4 | 2:20-2:45 | Kanały, ponowienia, DLQ | Reliability, channels |
| 5 | 2:45-3:00 | Grupy, runbooki, role, własny układ | Runbooks, UX |
| 6 | 3:00-3:28 | Architektura | Scalability, Flexibility |
| 7 | 3:28-4:05 | Wyniki load testu | Throughput / Latency |
| 8 | 4:05-4:30 | Bezpieczeństwo i co jest zamockowane | Security, Level of implementation |
| 9 | 4:30-4:50 | AI w developmencie i zamknięcie | AI tools |

## Przed nagraniem

1. **Dane:** organizacja `demo` załadowana z `demo/seed`, konto z rolą team lead lub admin, zalogowane na opublikowanym froncie.
2. **Czysty feed:** zamknijcie stare alerty, żeby burst był widoczny od razu.
3. **Telefon:** numer zweryfikowany w sandboxie SNS. Nagrajcie ekran telefonu osobno i wklejcie jako okienko w rogu (scena 1). Limit wydatków SMS to ok. 31 wiadomości, więc próby róbcie oszczędnie.
4. **Burst:** polecenie gotowe w terminalu:
   ```bash
   curl -X POST 'https://datasource.squawk.com.pl/scenarios/burst?count=500'
   ```
   Uwaga: burst idzie na **wszystkie** źródła naraz (5 x 500 = 2500 zdarzeń), więc powstanie po jednym alercie na regułę, a nie jeden alert łącznie. Tekst lektora to uwzględnia. Sprawdźcie przed nagraniem, ile alertów faktycznie powstaje i czy liczniki pokazują 500.
5. **Scena 4 wymaga przygotowania:** potrzebny alert, w którego historii widać nieudaną próbę, ponowienie i wynik końcowy. Najprościej: reguła z odbiorcą o niezweryfikowanym adresie email, jeden event, odczekać ponowienia.
6. **Slajdy do scen 6-9:** diagram architektury, wykres wyników, lista bezpieczeństwa i mocków, slajd końcowy. Trzy lub cztery proste plansze.
7. **Jeszcze nieprzeklikane na żywym środowisku** (wg `TASKS.md`): kreator źródła, CRUD grup i runbooków, zmiana roli. Przetestujcie je przed nagraniem scen 3 i 5. Każda z tych scen ma plan B poniżej.
8. **Nagrywanie:** okno przeglądarki 1920x1080, powiększenie strony 110-125%, ukryte zakładki i powiadomienia systemowe, kursor powiększony. Każdą scenę nagrajcie jako osobny klip.

---

## Scena 0 - hak (0:00-0:15)

**Ekran:** plansza tytułowa z logo Squawk i hasłem "500 errors. One alert." Po 6 sekundach przejście do live feedu.

**Lektor:**
```text
It's three a.m. A payment service fails five hundred times in one minute. Most alerting tools would send five hundred messages. Squawk sends one, to the right person, on the right channel.
```

## Scena 1 - burst na żywo (0:15-1:00)

**Ekran:**
1. Live feed, obok mały terminal. Uruchomcie polecenie burst.
2. W feedzie pojawiają się alerty bez odświeżania, liczniki rosną do 500. Zatrzymajcie kadr na liczniku.
3. Okienko z telefonem: przychodzi jeden SMS z linkiem.
4. Otwórzcie alert krytyczny: pola zdarzenia, ostatnie pasujące zdarzenie, runbook z checklistą.
5. Przewińcie do historii: wpisy o dostarczeniu z czasem.
6. Kliknijcie Acknowledge, dodajcie krótką notatkę.

**Lektor:**
```text
This is the live feed for our team. We trigger one incident on every source at once: twenty-five hundred events, in five different formats. Instead of twenty-five hundred notifications, the feed shows one alert per rule, each counting its five hundred events, with no page refresh. The on-call engineer gets a single text message with a link. Opening the alert shows the fields this rule chose to display, the latest matching event, and the attached runbook. The timeline records every delivery attempt with its measured latency. We acknowledge the alert, add a note, and the whole team sees the change instantly.
```

**Plan B:** jeśli SMS nie dojdzie w trakcie nagrania, pokażcie wpis o dostarczeniu SMS w historii alertu i wklejcie nagranie telefonu z wcześniejszej próby.

## Scena 2 - reguła z asystentem AI (1:00-1:50)

**Ekran:**
1. Rules, nowa reguła, wybór źródła "Payments JSON API".
2. W asystencie wpiszcie cel, np. `slow checkout requests on the payment API`. Pokażcie wygenerowane warunki.
3. Kliknijcie test: wynik "złapałaby X z ostatnich 100 zdarzeń".
4. Severity: kliknijcie Critical, potem Warning, żeby było widać zmianę domyślnych kanałów.
5. Odbiorcy: grupa. Grupowanie po `service`, limit powiadomień na osobę.
6. Podgląd wiadomości: zmieńcie szablon tytułu, podgląd aktualizuje się na żywo.
7. Save. Przejdźcie do feedu i pokażcie nowy alert z tytułem z tej reguły.

**Lektor:**
```text
Rules are data, not code. Instead of remembering filter syntax, we describe what we want in plain English. The assistant proposes conditions on real fields from this source, and we test them on the last hundred events before saving. Conditions work on any field, including nested values, with comparisons, set membership, and AND or OR. Severity pre-selects the channels, but every rule can override them. We pick a recipient group, group alerts by service, cap notifications per person, and preview the message. Save. No restart, no deployment: the rule is live within seconds.
```

**Plan B:** jeśli model nie odpowie (darmowy limit), wypełnijcie dwa warunki ręcznie i przytnijcie zdanie o asystencie. Warto mieć jedno udane nagranie asystenta zrobione wcześniej.

## Scena 3 - źródła (1:50-2:20)

**Ekran:**
1. Sources: lista pięciu źródeł z typem, formatem i statusem.
2. Add source: wybór typu, wklejenie przykładowej linii logu, wykrycie pól.
3. Tabela mapowania pól, zatwierdzenie, zapis. Nowe źródło na liście.

**Lektor:**
```text
Events arrive from five sources of three kinds: a webhook that pushes to us, HTTP endpoints we poll, and a Kafka topic. They come in different shapes, from nested JSON to plain text log lines. Adding a source needs no code. We choose the kind, paste one example event, and Squawk shows the fields it found. We confirm the mapping and save. A background worker picks the new source up within fifteen seconds.
```

**Plan B:** jeśli kreator nie działa na żywym środowisku, pokażcie szczegóły istniejącego źródła tekstowego (wyrażenie parsera i wykryte pola) i usuńcie z tekstu trzy zdania od "We choose the kind" do "save".

## Scena 4 - kanały i niezawodność (2:20-2:45)

**Ekran:**
1. Edytor reguły: lista kanałów z szablonem per kanał.
2. Przygotowany alert z nieudaną dostawą: historia z wpisami "failed", "retrying", wynik końcowy.
3. Krótko: ustawienie limitu powiadomień na odbiorcę.

**Lektor:**
```text
Notifications go out by SMS, email, browser push and in-app pop-up, each through its own queue. If a provider fails, the job is retried, moved to a dead-letter queue and retried again, and every attempt lands in the alert's timeline. Nothing is lost while a channel is down. Throttling caps how often one person is notified.
```

## Scena 5 - grupy, runbooki, role, układ (2:45-3:00)

**Ekran:** szybkie cięcia, po 3-4 sekundy: grupa odbiorców z członkami, biblioteka runbooków z czterema typami, lista członków z rolami i kodem zespołu, tryb Customize view z przeciągnięciem jednego widgetu.

**Lektor:**
```text
Recipient groups and runbooks are reusable: a checklist, a markdown page, a wiki link or plain text. Admins invite people with a team code and assign roles. And every user can rearrange the workspace by dragging widgets.
```

**Plan B:** to, co nie działa na żywo, nagrajcie w trybie demo frontu (bez `.env`). Wygląda tak samo.

## Scena 6 - architektura (3:00-3:28)

**Ekran:** jeden diagram, elementy podświetlane po kolei: źródła, Kinesis, Lambda processor, DynamoDB, AppSync i przeglądarka, SQS i notifier. Na końcu podświetlcie trzy interfejsy: Parser, NotificationChannel, operator.

**Lektor:**
```text
Under the hood everything is serverless on AWS. Sources write to a Kinesis stream. A Lambda parses each event, evaluates the rules, and groups duplicates with one atomic DynamoDB update. Alerts reach browsers over WebSockets, and notifications travel through SQS. Every stage scales horizontally. A new parser, channel or rule operator is one class behind an interface.
```

## Scena 7 - wyniki (3:28-4:05)

**Ekran:**
1. Trzy sekundy terminala z uruchomionym `loadtest.py` (przyspieszone).
2. Plansza z liczbami: 200 żądań/s, 0 odrzuconych, 0 zgubionych, p50 193 ms, p99 338 ms.
3. Druga plansza: 25 000 zdarzeń, 1000/s, 1 alert, licznik 25 000.
4. Mały dopisek: "Account limit: 10 concurrent Lambdas".

**Lektor:**
```text
Every number here is measured, not estimated. Our load test sends real webhooks and listens on the live feed like a browser. At two hundred requests per second, nothing was rejected and nothing was lost. End-to-end latency, from request to alert on screen, was one hundred ninety-three milliseconds at the median and three hundred thirty-eight at the ninety-ninth percentile. Twenty-five thousand identical events, at a thousand per second, produced exactly one alert counting all twenty-five thousand. The ceiling today is our account's limit of ten concurrent Lambdas, not the design.
```

Liczby pochodzą z sekcji "Final" w `loadtest/README.md` (wiersz 200/s z bezpośrednim ingestem i burst 1000/s). Jeśli powtórzycie pomiary, podmieńcie je w tekście i na planszach.

## Scena 8 - bezpieczeństwo i mocki (4:05-4:30)

**Ekran:** plansza z dwiema kolumnami. Lewa "Security": Cognito sign-in, roles on every route, closed live channels, per-source ingest token, secrets in Parameter Store. Prawa "Mocked": data sources, voice call. Pod spodem: "Real: SMS, email, push (sandbox)".

**Lektor:**
```text
Security. Users sign in through Cognito, roles are checked on every API route, live channels are closed to other organisations, each webhook source has its own token, and secrets live in Parameter Store. Mocked: the data sources and the voice call. SMS, email and push are real, in sandbox mode. The assistant, in this demo, only sees mock data.
```

## Scena 9 - AI w developmencie i zamknięcie (4:30-4:50)

**Ekran:** dwa ujęcia po 4 sekundy: `Docs/contracts/` i `CLAUDE.md` w repo, potem wynik testów. Na końcu plansza z logo, hasłem i adresem repo.

**Lektor:**
```text
We built this in ten hours as a team of three, with Claude as a fourth teammate: contracts first, tests before code, and a load test that caught a real race condition. Squawk. Five hundred errors, one alert.
```

---

## Wskazówki do głosu

- Generujcie każdą scenę jako osobny plik. Łatwiej wtedy poprawić jedno zdanie i dopasować długość klipu.
- Liczby są celowo zapisane słownie, żeby synteza nie czytała ich po swojemu.
- Odsłuchajcie wymowę nazw: Squawk, Kinesis, DynamoDB, Cognito, Lambda. Jeśli któraś brzmi źle, zapiszcie ją fonetycznie tylko w tekście dla syntezy.
- Jeden głos na cały film, spokojne tempo. Jeśli całość przekroczy 4:50, skróćcie scenę 5 albo zdanie o warunkach w scenie 2, a nie przyspieszajcie głosu.

## Jeśli zabraknie czasu na montaż

Kolejność cięcia: scena 5 (15 s), plan B sceny 3 (ok. 10 s), zdanie o cold starcie i limitach na planszach zamiast w głosie. Scen 1, 2 i 7 nie skracajcie, bo niosą najwięcej punktów: deduplikacja na żywo, reguły w locie i zmierzone wyniki.
