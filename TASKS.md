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

## Stan na 2026-11-21

Zrobione i sprawdzone na AWS:
- **Źródła danych** - mock pod `https://datasource.waw-out.pl` (JSON API, dwa logi tekstowe, Kafka, webhook) i worker z konektorami HTTP pull i Kafka.
- **Ingest i processor** - Kinesis -> parser -> reguły -> dedup -> alert w DynamoDB -> AppSync.
- **REST API** - organizacja i role, alerty z akcjami i timeline, CRUD źródeł, reguł, grup i runbooków, podgląd parsera i reguły.
- **Asystent reguł** - Google Gemini (`gemini-flash-lite-latest`), klucz w SSM.
- **Powiadomienia** - routing, grupy, szablony per kanał, throttling, SMS i email dochodzą, voice symulowany, ponawianie przez DLQ.
- **Load test** - 200 osobnych alertów/s bez strat (end-to-end p99 574 ms), 5000 identycznych eventów daje 1 alert. Wyniki i ograniczenia: `loadtest/README.md`.
- **Dane demo** - organizacja `demo` (kod `DEMO-2026`): pięć źródeł, osiem reguł, cztery runbooki.

Zrobione, czeka na wdrożenie:
- **Push** - kanał `push` (Web Push, szyfrowanie zgodne z RFC 8291), rejestracja urządzeń, strona testowa.
- **Zamknięte kanały live feedu** - subskrypcja tylko do kanałów własnej organizacji, na podstawie atrybutu `custom:orgId` w tokenie Cognito.
- **Ponowna wysyłka powiadomień** - `POST /alerts/{alertId}/resend`.

Dla frontendu: `Docs/frontend-integration/` - instrukcja, typy TypeScript, klient live feedu, moduł push i service worker.

Najbliższe kroki:
1. `terraform apply`, wygenerować klucze push (`VapidKeys`), sprawdzić izolację kanałów i push na telefonie.
2. Frontend według instrukcji (C1-C13).
3. Końcowe pomiary po decyzji o limicie Lambdy (A12, A14), ewentualnie ingest bez Lambdy.

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
- [x] R2: SMS - sandbox SNS działa, numer zweryfikowany, SMS dochodzi (potwierdzone w logach dostarczenia). Limit wydatków to 1 USD miesięcznie, czyli ok. 31 SMS-ów
- [x] R3: email - SES przyjmuje wysyłkę; nadawca z `gmail.com` może trafiać do spamu, przejście na `alerts@waw-out.pl` z DKIM jest przygotowane w `terraform.tfvars`
- [ ] R4: voice - czy da się zdobyć numer z obsługą połączeń; jeśli nie, kanał voice zostaje mockiem z wpisem w timeline
- [x] R5: model AI - zamiast Bedrocka używamy Google Gemini przez klucz API
- [ ] R6: limit współbieżności Lambdy na koncie wynosi 10 i to on ogranicza przepustowość (503 przy 50 eventach/s). Trzeba zgłosić podniesienie: `aws service-quotas request-service-quota-increase --service-code lambda --quota-code L-B99A9384 --desired-value 1000`
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
- [x] A6: S3 + CloudFront dla frontendu (`infra/app/frontend.tf`), SnapStart na wszystkich funkcjach
- [x] A7: mock VM w docker-compose - JSON API, log aplikacji, access log, Kafka (natywnie na 9092 i REST pod `/kafka`), webhook; scenariusze: zwykły ruch i burst 500 identycznych błędów (`POST /scenarios/burst`)
- [x] A7a: Terraform dla mock VM w `infra/datasource/`
- [x] A7b: mock VM wdrożony pod `https://datasource.waw-out.pl` (Kafka: `datasource.waw-out.pl:9092`), certyfikat działa
- [x] A8: worker z konektorami (`backend/worker`) - HTTP pull z kursorem dla `/api/events`, `/logs`, `/logs/access` i konsument Kafki dla `infra.events`, piszące do Kinesis; źródła odświeżane z DynamoDB co 15 s. Sprawdzony lokalnie na wszystkich czterech źródłach; instancja EC2 w `infra/app/worker.tf` czeka na `terraform apply`
- [x] A9: SQS per kanał + DLQ + mapowania do notifiera (także z DLQ); tożsamość nadawcy SES przez `notification_sender_email`
- [x] A10: skrypt load testu (`loadtest/`) - generator eventów w stałym tempie + klient WebSocket mierzący ingest -> UI; liczy throughput, p50, p90, p99 i zapisuje wyniki do plików

### Blok 3 (6:30-8:30)
- [x] A11: asystent filtra - `POST /ai/rule-suggestion` na Google Gemini (`gemini-flash-lite-latest`, klucz w SSM): cel w języku naturalnym + pola z ostatnich eventów źródła -> warunki reguły, walidowane i od razu sprawdzone na ostatnich eventach. Sprawdzony na prawdziwym modelu, czeka na wdrożenie
- [ ] A12: pomiary i strojenie - pomiar bazowy zrobiony (`loadtest/README.md`); optymalizacje (SnapStart, równoległy zapis alertów, podział współbieżności) czekają na `terraform apply` i ponowny pomiar
- [ ] A13: powiadomienia push (kanał `push`, rejestracja urządzeń, strona testowa `demo/push-test/`) - napisane i przetestowane lokalnie, czeka na wdrożenie i próbę na prawdziwym telefonie. Poza tym, jeśli starczy czasu: prawdziwy kanał voice, RabbitMQ jako kolejne źródło

### Blok 4 (8:30-10:00)
- [ ] A14: finalne load testy, tabela i wykres p50/p99 do prezentacji, opis metodologii pomiaru

---

## Osoba B - backend

### Blok 1 (0:45-3:00)
- [x] B1: szkielet Maven - `core` (bez zależności od AWS) i `lambdas` (handlery, shade jar); modele z kontraktów K1-K3 z testem, który mapuje przykłady z `Docs/contracts/` na klasy. Moduł `worker` powstanie razem z konektorami (A8)
- [x] B2: interfejs `Parser` + rejestr parserów po formacie; parser JSON (spłaszczanie, `split` na tablicy) i normalizacja eventu
- [x] B3: silnik reguł - operatory `eq`, `neq`, `in`, `contains`, `regex`, `gt`, `gte`, `lt`, `lte`, `exists` w rejestrze; testy jednostkowe
- [x] B4: handler processora - batch z Kinesis -> parser -> reguły (cache 15 s) -> zapis alertu -> publikacja do AppSync Events. Logika przetestowana lokalnie, na AWS jeszcze niesprawdzona
- [x] B5: API alertów - `GET /alerts` z filtrami i stronicowaniem, szczegóły z runbookiem

### Blok 2 (3:00-6:30)
- [x] B6: CRUD reguł i źródeł z walidacją (operatory, regexy, parser); zmiany widoczne w processorze bez restartu, najpóźniej po 15 s. Do tego presety źródeł i podgląd parsera (`POST /sources/preview`)
- [x] B7: dedup - fingerprint `sha256(orgId + ruleId + wartości groupBy)`, zapis warunkowy do `cod-dedup`, kolejne eventy tylko podbijają `count`; grupowanie w obrębie batcha przed zapisem
- [x] B8: throttling per reguła i per odbiorca - liczniki z TTL w `cod-counters`, wstrzymane powiadomienie trafia do timeline
- [x] B9: notifier - interfejs `NotificationChannel`, kanały email (SES), SMS (SNS), UI (AppSync) i symulowany voice; każda próba w timeline; błąd -> 3 próby -> DLQ -> kolejne rundy -> wpis końcowy. Na AWS jeszcze niesprawdzone
- [x] B10: szablony prezentacji per kanał (`{{pole}}`) + deep link do alertu w SMS i mailu
- [x] B11: parser logów tekstowych (regex z nazwanymi grupami) dla obu formatów z mocka; custom source to ta sama konfiguracja parsera wypełniona ręcznie
- [x] B12: podgląd reguły - `POST /rules/preview` zwraca, ile z ostatnich 100 eventów źródła by złapała; próbka ostatnich eventów w `cod-recent-events` (do 5 na sekundę na źródło, TTL 1 h) i `GET /sources/{id}/events`

### Blok 3 (6:30-8:30)
- [x] B13: ack, resolve, przypisanie, notatki, odczyt timeline; każda zmiana publikowana do AppSync
- [x] B14: grupy odbiorców - CRUD + rozwijanie grupy przy tworzeniu powiadomień
- [x] B15: runbooki - CRUD w czterech formatach + cztery przykładowe w `demo/seed/runbooks.json`
- [x] B16: organizacja - kod zaproszenia, dołączenie, role (`admin`, `team_lead`, `user`) sprawdzane na każdej trasie API

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
