# Podział zadań - Claude of Duty

Plan pracy dla 3 osób na ok. 10 h kodowania + 2 h na prezentację.
Stack: Java 21 (Lambda, bez Springa, AWS SDK v2, Maven), Terraform, TypeScript + React, DynamoDB, Kinesis, SQS, AppSync Events, Cognito, Bedrock.

## Role

| Osoba | Tor | Katalogi, za które odpowiada |
|---|---|---|
| **A** | Infra, ingest, mocki, pomiary | `infra/`, `mocks/`, `loadtest/`, `backend/worker/`, handler ingest |
| **B** | Backend: processor, reguły, dostarczanie, API | `backend/core/`, `backend/lambdas/` |
| **C** | Frontend i UX | `frontend/` |

Wspólne: `Docs/contracts/` (schematy i lista endpointów). Zmiana kontraktu po fazie 0 wymaga zgody całej trójki, bo psuje pracę pozostałym torom.

Wpiszcie imiona przy literach, zanim zaczniecie.

## Stan na 2026-11-20

Zrobione:
- **Mock źródeł danych** - wdrożony pod `https://datasource.waw-out.pl`, Kafka pod `datasource.waw-out.pl:9092`. Opis w `mocks/datasource/README.md`.
- **Infrastruktura aplikacji** - `infra/app/` wdrożona (`terraform apply` przeszedł).
- **Szkic kontraktów K1-K7** - `Docs/contracts/`. Czeka na przegląd całej trójki.
- **Backend: ingest i processor** - webhook -> Kinesis -> parser -> reguły -> dedup -> alert w DynamoDB -> publikacja do AppSync. 43 testy lokalne, w tym "500 identycznych eventów daje 1 alert". Processor czeka na wdrożenie i sprawdzenie na AWS.
- **Dane demo** - `demo/seed/` (źródło webhook i dwie reguły), wgrywane narzędziem `Seed`, dopóki nie ma API.

Najbliższe kroki:
1. Wdrożyć processor (`./mvnw package` + `terraform apply`), wgrać dane demo, sprawdzić alert w `cod-alerts`. Potem ustawić `webhook_url` w `infra/datasource`, żeby mock pchał eventy sam.
2. API: `GET /alerts`, szczegóły i timeline (B5, B13), żeby frontend miał co wyświetlić - to domyka M1 razem z live feedem (C3).
3. Notifier z kanałami i throttlingiem (B8, B9), CRUD reguł i źródeł (B6), worker z konektorami pull i Kafka (A8).

## Przepływ danych (dla orientacji)

```
mock VM: JSON API, log aplikacji, access log, Kafka, webhook
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
- [ ] R1: konto AWS hackathonu z dostępem dla całej trójki, wybór regionu, backend stanu Terraform (S3). Profil firmowy odpada - SCP blokuje tam `eu-central-1`, więc `terraform plan` nie przechodzi
- [ ] R2: SMS - sandbox SNS, zweryfikować numery wszystkich trzech osób
- [ ] R3: email - sandbox SES, zweryfikować adresy nadawcy i odbiorców
- [ ] R4: voice - czy da się zdobyć numer z obsługą połączeń; jeśli nie, kanał voice zostaje mockiem z wpisem w timeline
- [ ] R5: Bedrock - dostęp do małego modelu (Nova Micro albo Claude Haiku) w wybranym regionie
- [ ] R6: limit współbieżności Lambdy na koncie (na świeżych kontach bywa 10) - jeśli niski, od razu wniosek o podniesienie
- [x] R7: domena `waw-out.pl`, DNS w home.pl, rekord A `datasource` wskazuje na Elastic IP mocka

**B - schematy danych** (`Docs/contracts/`)
Szkice K1-K7 są gotowe. Odhaczcie je po wspólnym przeglądzie.
- [ ] K1: źródło, parser, rekord na Kinesis i znormalizowany event + mapowanie pięciu formatów z mocka - szkic: `Docs/contracts/sources-and-events.md`
- [ ] K2: reguła (warunki, severity, routing, prezentacja, grupowanie, throttling, runbook) - szkic: `Docs/contracts/rules.md`
- [ ] K3: alert, timeline, zadanie powiadomienia, mechanika dedupu i throttlingu - szkic: `Docs/contracts/alerts.md`
- [ ] K4: tabele DynamoDB i klucze - szkic: `Docs/contracts/data-model.md`
- [ ] K5: stemple czasu do pomiarów (`receivedAt`, `processedAt`, `publishedAt`, `deliveredAt`) - szkic: `Docs/contracts/README.md`

**C - API i ekrany**
- [ ] K6: endpointy REST i uprawnienia ról - szkic: `Docs/contracts/api.md`
- [ ] K7: kanały AppSync Events i kształt komunikatów - szkic: `Docs/contracts/api.md`
- [ ] K8: lista ekranów i nawigacja - zaczęte w `Docs/FrontPages/list-of-pages.md`. Do ustalenia: czy dashboard z drag-and-drop widgetów mieści się w czasie

**Razem**
- [ ] K9: układ repo i nazwy modułów (`infra/`, `backend/{core,lambdas,worker}`, `frontend/`, `mocks/`, `loadtest/`, `Docs/`), nazwy handlerów Lambdy

---

## Osoba A - infra, ingest, mocki, pomiary

### Blok 1 (0:45-3:00)
- [ ] A1: Terraform w `infra/app/` - Kinesis, 11 tabel DynamoDB, IAM, cztery funkcje Lambda z jednego jara, HTTP API Gateway, kolejki SQS z DLQ. Napisane i sprawdzone planem na sucho (61 zasobów), czeka na `terraform apply`
- [ ] A2: event source mapping Kinesis -> processor z enhanced fan-out, bisect przy błędzie. Napisane, czeka na `terraform apply`
- [ ] A3: AppSync Events API + Cognito (user pool, app client); output `frontend_env` jako gotowy `.env` dla frontendu. Napisane, czeka na `terraform apply`
- [x] A4: Lambda ingest - `POST /ingest/{orgId}/{sourceId}?token=...`, stempel `receivedAt`, zapis do Kinesis. Po wdrożeniu ustawić jej adres jako `webhook_url` w `infra/datasource`, żeby mock zaczął pchać webhooki
- [x] A5: wdrożenie dwiema komendami (`./mvnw package` + `terraform apply`), opisane w `README.md`

### Blok 2 (3:00-6:30)
- [ ] A6: S3 + CloudFront dla frontendu, SnapStart na Lambdzie api
- [x] A7: mock VM w docker-compose - JSON API, log aplikacji, access log, Kafka (natywnie na 9092 i REST pod `/kafka`), webhook; scenariusze: zwykły ruch i burst 500 identycznych błędów (`POST /scenarios/burst`)
- [x] A7a: Terraform dla mock VM w `infra/datasource/`
- [x] A7b: mock VM wdrożony pod `https://datasource.waw-out.pl` (Kafka: `datasource.waw-out.pl:9092`), certyfikat działa
- [ ] A8: worker z konektorami (Java, to samo repo) - HTTP poller z kursorem (`after` w zapytaniu, następny w `next_after` albo nagłówku `X-Next-After`) dla `/api/events`, `/logs`, `/logs/access` oraz konsument Kafki dla topicu `infra.events`, piszące do Kinesis; konfiguracja źródeł czytana z DynamoDB
- [ ] A9: SQS per kanał + DLQ + mapowania do notifiera i Lambdy ponawiającej; tożsamości SES, uprawnienia SNS
- [ ] A10: skrypt load testu - generator N eventów/s na webhook + klient WebSocket mierzący ingest -> UI; liczy throughput, p50, p99 i zapisuje wyniki do pliku

### Blok 3 (6:30-8:30)
- [ ] A11: asystent filtra - `POST /ai/rule-suggestion` (Bedrock): przykładowy event + opis celu -> JSON warunków reguły, walidowany względem K2
- [ ] A12: pierwsze pomiary i strojenie (liczba shardów, batch size, parallelization factor, pamięć Lambdy)
- [ ] A13 (jeśli starczy czasu): RabbitMQ jako kolejne źródło (mock go nie ma), podpowiadanie mapowania custom source przez AI, voice

### Blok 4 (8:30-10:00)
- [ ] A14: finalne load testy, tabela i wykres p50/p99 do prezentacji, opis metodologii pomiaru

---

## Osoba B - backend

### Blok 1 (0:45-3:00)
- [x] B1: szkielet Maven - `core` (bez zależności od AWS) i `lambdas` (handlery, shade jar); modele z kontraktów K1-K3 z testem, który mapuje przykłady z `Docs/contracts/` na klasy. Moduł `worker` powstanie razem z konektorami (A8)
- [x] B2: interfejs `Parser` + rejestr parserów po formacie; parser JSON (spłaszczanie, `split` na tablicy) i normalizacja eventu
- [x] B3: silnik reguł - operatory `eq`, `neq`, `in`, `contains`, `regex`, `gt`, `gte`, `lt`, `lte`, `exists` w rejestrze; testy jednostkowe
- [x] B4: handler processora - batch z Kinesis -> parser -> reguły (cache 15 s) -> zapis alertu -> publikacja do AppSync Events. Logika przetestowana lokalnie, na AWS jeszcze niesprawdzona
- [ ] B5: minimalne API - `GET /alerts` na start feedu

### Blok 2 (3:00-6:30)
- [ ] B6: CRUD reguł i źródeł; zmiany widoczne w processorze bez restartu
- [x] B7: dedup - fingerprint `sha256(orgId + ruleId + wartości groupBy)`, zapis warunkowy do `cod-dedup`, kolejne eventy tylko podbijają `count`; grupowanie w obrębie batcha przed zapisem
- [ ] B8: throttling per reguła i per odbiorca - licznik z TTL
- [ ] B9: notifier - interfejs `NotificationChannel`, implementacje email (SES), SMS (SNS), UI (AppSync); każda próba jako wpis w timeline; błąd -> retry z backoffem -> DLQ -> ponowienie
- [ ] B10: szablony prezentacji - `title`, `body` i `fields` działają; zostały nadpisania per kanał i deep link w SMS i mailu (razem z notifierem)
- [x] B11: parser logów tekstowych (regex z nazwanymi grupami) dla obu formatów z mocka; custom source to ta sama konfiguracja parsera wypełniona ręcznie
- [ ] B12: podgląd reguły - `POST /rules/preview` zwraca, ile z ostatnich 100 eventów źródła by złapała (wymaga bufora ostatnich eventów z TTL)

### Blok 3 (6:30-8:30)
- [ ] B13: ack, notatki, odczyt timeline; zmiany statusu publikowane do AppSync
- [ ] B14: grupy odbiorców - CRUD + rozwijanie grupy w notifierze
- [ ] B15: runbooki - CRUD w czterech formatach (markdown, link, checklist, tekst) + kilka przykładowych w bibliotece
- [ ] B16: organizacja - kod zaproszenia, dołączenie, role (`admin`, `team_lead`, `user`), sprawdzanie roli w API

### Blok 4 (8:30-10:00)
- [ ] B17: poprawki błędów z testów, sekcja architektury w README

---

## Osoba C - frontend

### Blok 1 (0:45-3:00)
- [ ] C1: szkielet - Vite + React + TypeScript, routing, layout, klient API z typami z kontraktów
- [ ] C2: logowanie gotowym komponentem Amplify (Cognito)
- [ ] C3: live feed - subskrypcja AppSync Events, lista alertów z severity, licznikiem i czasem; do czasu gotowego backendu na zamockowanych danych w formacie K3

### Blok 2 (3:00-6:30)
- [ ] C4: kreator źródła - przycisk "+", wybór typu (webhook, JSON API, log aplikacji, access log, Kafka), formularz konfiguracji; custom type: wklejona próbka + ręczne mapowanie pól
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
| gotowe | A -> B, C | Formaty źródeł i przykładowe payloady: `mocks/datasource/README.md` |
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

1. RabbitMQ jako kolejne źródło (już pominięty w mocku)
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
