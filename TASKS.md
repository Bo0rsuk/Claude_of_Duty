# Podział zadań - Claude of Duty

Plan pracy dla 3 osób na ok. 10 h kodowania + 2 h na prezentację.
Stack: Java 21 (Lambda, bez Springa, AWS SDK v2, Maven), Terraform, TypeScript + React, DynamoDB, Kinesis, SQS, AppSync Events, Cognito, Bedrock.

## Role

| Osoba | Tor | Katalogi, za które odpowiada |
|---|---|---|
| **A** | Infra, ingest, mocki, pomiary | `infra/`, `mocks/`, `loadtest/`, `backend/worker/`, handler ingest |
| **B** | Backend: processor, reguły, dostarczanie, API | `backend/core/`, `backend/lambdas/` |
| **C** | Frontend i UX | `frontend/` |

Wspólne: `docs/contracts/` (schematy i lista endpointów). Zmiana kontraktu po fazie 0 wymaga zgody całej trójki, bo psuje pracę pozostałym torom.

Wpiszcie imiona przy literach, zanim zaczniecie.

## Przepływ danych (dla orientacji)

```
mock VM: HTTP JSON, logi tekstowe, Kafka, (RabbitMQ), webhook
  -> worker z konektorami (pull) / API Gateway + Lambda ingest (push)
  -> Kinesis Data Streams
  -> Lambda processor: parsowanie -> normalizacja -> reguły -> dedup/throttling
  -> DynamoDB (alerty, timeline) + publikacja do AppSync Events -> przeglądarka
  -> SQS per kanał (+ DLQ) -> Lambda notifier: SMS, email, UI, (voice)
```

## Harmonogram

| Czas | Faza | Cel na koniec fazy |
|---|---|---|
| 0:00-0:45 | Faza 0 - kontrakty i ryzyka | Schematy spisane, ryzyka AWS sprawdzone |
| 0:45-3:00 | Blok 1 - cienki przekrój | **M1:** `curl` z eventem daje alert w UI bez odświeżania |
| 3:00-6:30 | Blok 2 - szerokość | **M2:** reguła z UI działa bez restartu, 500 eventów daje 1 alert, email dochodzi |
| 6:30-8:30 | Blok 3 - produkt i wyróżniki | **M3:** feature freeze |
| 8:30-10:00 | Blok 4 - pomiary i szlif | Wyniki p50/p99, README, scenariusz demo |

Zasada dla M1: jeśli o 3:30 przekrój end-to-end nie działa, wszyscy odkładają swoje zadania i go naprawiają. Reszta na tym stoi.

---

## Faza 0 - wspólnie (0:00-0:45)

Pierwsze 30 minut równolegle, ostatnie 15 minut wspólny przegląd i zamrożenie kontraktów.

**A - ryzyka i konto AWS**
- [ ] R1: wybór regionu, backend stanu Terraform (S3), dostępy dla całej trójki
- [ ] R2: SMS - sandbox SNS, zweryfikować numery wszystkich trzech osób
- [ ] R3: email - sandbox SES, zweryfikować adresy nadawcy i odbiorców
- [ ] R4: voice - czy da się zdobyć numer z obsługą połączeń; jeśli nie, kanał voice zostaje mockiem z wpisem w timeline
- [ ] R5: Bedrock - dostęp do małego modelu (Nova Micro albo Claude Haiku) w wybranym regionie
- [ ] R6: limit współbieżności Lambdy na koncie (na świeżych kontach bywa 10) - jeśli niski, od razu wniosek o podniesienie

**B - schematy danych** (`docs/contracts/`)
- [ ] K1: znormalizowany event - `eventId`, `orgId`, `sourceId`, `sourceType`, `eventTime`, `receivedAt`, `message`, `fields` (mapa), `raw`
- [ ] K2: reguła - `ruleId`, `sourceId`, `name`, `enabled`, `conditions` (lista `{field, op, value}` + tryb all/any), `severity`, `routing` (lista `{channel, target}`), `presentation` (szablon per kanał), `groupBy`, `throttle` (`{max, windowSeconds}`), `runbookId`
- [ ] K3: alert i timeline - `alertId`, `fingerprint`, `ruleId`, `status` (OPEN/ACKED), `count`, `firstSeen`, `lastSeen`, wyrenderowany tytuł i treść, przykładowy event; wpis timeline: `type` (CREATED, NOTIFIED, DELIVERY_FAILED, RETRIED, ACKED, NOTE), `actor`, `at`, `detail`
- [ ] K4: tabele DynamoDB i klucze (sources, rules, alerts + timeline, groups, runbooks, org/users, liczniki throttlingu z TTL)
- [ ] K5: stemple czasu do pomiarów - `receivedAt` (ingest), `processedAt` (processor), `publishedAt` (AppSync), `deliveredAt` (notifier)

**C - API i ekrany**
- [ ] K6: lista endpointów REST (źródła, reguły, alerty, ack, notatki, grupy, runbooki, organizacja, asystent AI)
- [ ] K7: kanały AppSync Events (np. `/alerts/{orgId}`) i kształt komunikatu
- [ ] K8: lista ekranów i nawigacja (feed, źródła, reguły, grupy, runbooki, organizacja, okno alertu)

**Razem**
- [ ] K9: układ repo i nazwy modułów (`infra/`, `backend/{core,lambdas,worker}`, `frontend/`, `mocks/`, `loadtest/`, `docs/`), nazwy handlerów Lambdy

---

## Osoba A - infra, ingest, mocki, pomiary

### Blok 1 (0:45-3:00)
- [ ] A1: Terraform - Kinesis, tabele DynamoDB z K4, IAM, cztery funkcje Lambda z jednego jara (ingest, processor, notifier, api), HTTP API Gateway
- [ ] A2: event source mapping Kinesis -> processor z enhanced fan-out, mały batch window, bisect przy błędzie
- [ ] A3: AppSync Events API + Cognito (user pool, app client); outputy Terraform jako gotowy `.env` dla frontendu
- [ ] A4: Lambda ingest - `POST /ingest/{sourceId}`, stempel `receivedAt`, zapis do Kinesis
- [ ] A5: jedna komenda do wdrożenia (`mvn package` + `terraform apply`), opisana w README

### Blok 2 (3:00-6:30)
- [ ] A6: S3 + CloudFront dla frontendu, SnapStart na Lambdzie api
- [ ] A7: mock VM w docker-compose - API z JSON-em (pull), endpoint z logami tekstowymi, Kafka z producentem; scenariusze: zwykły ruch i burst 500 identycznych błędów
- [ ] A8: worker z konektorami (Java, to samo repo) - HTTP poller i konsument Kafki piszące do Kinesis; konfiguracja źródeł czytana z DynamoDB
- [ ] A9: SQS per kanał + DLQ + mapowania do notifiera i Lambdy ponawiającej; tożsamości SES, uprawnienia SNS
- [ ] A10: skrypt load testu - generator N eventów/s na webhook + klient WebSocket mierzący ingest -> UI; liczy throughput, p50, p99 i zapisuje wyniki do pliku

### Blok 3 (6:30-8:30)
- [ ] A11: asystent filtra - `POST /ai/rule-suggestion` (Bedrock): przykładowy event + opis celu -> JSON warunków reguły, walidowany względem K2
- [ ] A12: pierwsze pomiary i strojenie (liczba shardów, batch size, parallelization factor, pamięć Lambdy)
- [ ] A13 (jeśli starczy czasu): RabbitMQ jako czwarte źródło, podpowiadanie mapowania custom source przez AI, voice

### Blok 4 (8:30-10:00)
- [ ] A14: finalne load testy, tabela i wykres p50/p99 do prezentacji, opis metodologii pomiaru

---

## Osoba B - backend

### Blok 1 (0:45-3:00)
- [ ] B1: szkielet Maven - `core` (bez zależności od AWS), `lambdas` (handlery, shade jar), `worker`; modele z kontraktów K1-K3
- [ ] B2: interfejs `Parser` + rejestr parserów po typie źródła; parser JSON i normalizacja
- [ ] B3: silnik reguł - operatory `eq`, `neq`, `contains`, `regex`, `gt`, `lt`, `exists`; testy jednostkowe
- [ ] B4: handler processora - batch z Kinesis -> parser -> reguły (cache z TTL ok. 15 s) -> zapis alertu -> publikacja do AppSync Events; stemple czasu z K5
- [ ] B5: minimalne API - `GET /alerts` na start feedu

### Blok 2 (3:00-6:30)
- [ ] B6: CRUD reguł i źródeł; zmiany widoczne w processorze bez restartu
- [ ] B7: dedup - fingerprint `hash(ruleId + wartości groupBy)`, zapis warunkowy do DynamoDB, kolejne eventy tylko podbijają `count`
- [ ] B8: throttling per reguła i per odbiorca - licznik z TTL
- [ ] B9: notifier - interfejs `NotificationChannel`, implementacje email (SES), SMS (SNS), UI (AppSync); każda próba jako wpis w timeline; błąd -> retry z backoffem -> DLQ -> ponowienie
- [ ] B10: szablony prezentacji per kanał (`{{pole}}`) + deep link do alertu w SMS i mailu
- [ ] B11: parser logów tekstowych (regex z nazwanymi grupami) i ręczne mapowanie pól dla custom source
- [ ] B12: podgląd reguły - `POST /rules/preview` zwraca, ile z ostatnich 100 eventów źródła by złapała (wymaga bufora ostatnich eventów z TTL)

### Blok 3 (6:30-8:30)
- [ ] B13: ack, notatki, odczyt timeline; zmiany statusu publikowane do AppSync
- [ ] B14: grupy odbiorców - CRUD + rozwijanie grupy w notifierze
- [ ] B15: runbooki - CRUD w czterech formatach (markdown, link, checklist, tekst) + kilka przykładowych w bibliotece
- [ ] B16: organizacja - kod zaproszenia, dołączenie, role (admin, rule-editor, user), sprawdzanie roli w API

### Blok 4 (8:30-10:00)
- [ ] B17: poprawki błędów z testów, sekcja architektury w README

---

## Osoba C - frontend

### Blok 1 (0:45-3:00)
- [ ] C1: szkielet - Vite + React + TypeScript, routing, layout, klient API z typami z kontraktów
- [ ] C2: logowanie gotowym komponentem Amplify (Cognito)
- [ ] C3: live feed - subskrypcja AppSync Events, lista alertów z severity, licznikiem i czasem; do czasu gotowego backendu na zamockowanych danych w formacie K3

### Blok 2 (3:00-6:30)
- [ ] C4: kreator źródła - przycisk "+", wybór typu (webhook, HTTP pull, logi tekstowe, Kafka), formularz konfiguracji; custom type: wklejona próbka + ręczne mapowanie pól
- [ ] C5: edytor reguły - builder warunków, severity z domyślnym presetem kanałów (CRITICAL: SMS + telefon, WARNING: email, INFO: UI), routing (kanał + odbiorca lub grupa), groupBy, throttle, wybór runbooka
- [ ] C6: prezentacja per reguła - edycja szablonu z podglądem na żywo + wynik "złapałaby X z ostatnich 100 eventów"
- [ ] C7: okno alertu - pola, runbook (render markdown, checklist, link, tekst), timeline, ack, notatki; deep link `/alerts/{id}`; alert INFO otwiera okienko automatycznie

### Blok 3 (6:30-8:30)
- [ ] C8: grupy odbiorców - CRUD i wybór grupy w edytorze reguły
- [ ] C9: biblioteka runbooków - CRUD + dodanie nowego bez wychodzenia z edytora reguły
- [ ] C10: panel asystenta AI w edytorze reguły - przykładowy event + opis celu -> wypełnione warunki do zatwierdzenia
- [ ] C11: organizacja - ekran dołączenia kodem, lista użytkowników i zmiana ról (tylko admin)

### Blok 4 (8:30-10:00)
- [ ] C12: szlif UX dla nowego użytkownika - puste stany, komunikaty błędów, podpowiedzi
- [ ] C13: scenariusz demo przećwiczony na wdrożonym środowisku

---

## Kto na kogo czeka

| Do kiedy | Kto -> komu | Co |
|---|---|---|
| 1:00 | B -> A | Nazwy handlerów i ścieżka do jara (A wpina je w Terraform) |
| 1:30 | A -> B | Wdrożona infra z pustymi Lambdami, B może podmieniać jar |
| 1:30 | A -> C | `.env` z URL API, ID Cognito i endpointem AppSync |
| 2:15 | B -> C | Działające `GET /alerts` i publikacja alertów do AppSync |
| 4:00 | A -> B | Kolejki SQS i DLQ gotowe pod notifier |
| 4:30 | B -> C | CRUD reguł i źródeł |
| 5:30 | B -> C | Podgląd reguły (`/rules/preview`) |
| 7:00 | A -> C | Endpoint asystenta AI |
| 7:15 | B -> C | Ack, notatki, grupy, runbooki |

Jeśli coś się spóźnia, czekający pracuje na mocku zgodnym z kontraktem i nie stoi.

## Co tniemy, gdy brakuje czasu (w tej kolejności)

1. RabbitMQ jako czwarte źródło
2. Voice - zostaje kanał-mock z wpisem w timeline
3. Podpowiadanie mapowania custom source przez AI
4. Role w organizacji - zostaje sam kod zaproszenia
5. Podgląd "X z ostatnich 100 eventów"

Nie tniemy: dedup (500 -> 1), reguły zmieniane w locie, live feed, DLQ z ponawianiem, load test z p50/p99, README. To są rzeczy wprost oceniane.

Poza zakresem od początku: "propose fixes/enhancements", eskalacje.

## Zasady pracy

- Branche `feature/<nazwa>` z `develop`, PR do `develop`, nic bezpośrednio na `master` ani `develop` (szczegóły w `CONTRIBUTING.md`).
- Małe PR-y i merge co 1-2 h, żeby tory się nie rozjechały. Każdy trzyma się swoich katalogów.
- Logika (parsery, silnik reguł, dedup) siedzi w `backend/core` bez zależności od AWS i ma testy jednostkowe.
- Nowe źródło, kanał i operator reguły to nowa klasa za interfejsem (`Parser`, `NotificationChannel`), a nie kolejny `if`. To jest oceniane jako flexibility.
- Krótki sync całej trójki przy każdym kamieniu milowym (3:00, 6:30, 8:30): co działa, co blokuje, co tniemy.
