# Lista stron, paneli i widgetów (Alerting platform)

Dokument przedstawia założenia widoków w aplikacji.

## 1. Główna idea

- Każdy użytkownik aplikacji zarządza własnym widokiem i układem widgetów.
- Układ powstaje przez **drag-and-drop** widgetów wybieranych z drop-down list dostępnych widgetów.
- Aplikacja ma **zwijany sidebar** z wyborem głównych paneli.
- Widgety mogą być **zagnieżdżane** według pochodzenia (np. widgety użytkownika wewnątrz widgetu zarządzania użytkownikami).
- Widgety mogą być **grupowane** według preferencji użytkownika.

## 2. Ranga i dostępność

Ranga są hierarchiczne. Każda wyższa rola widzi panele wszystkich niższych.

| Ranga | Dostęp |
|---|---|
| **User** | Live feed, acknowledge, resolve, notatki |
| **Team lead** | Wszystko z User + sources, reguły, grupy, runbooki, zarządzanie użytkownikami |
| **Admin** | Wszystko z Team lead + kod organizacji |

Dodatkowe ustalenia:
- Użytkownik należy do zespołu, którym zarządza Team lead.
- Team lead tworzy reguły (rules), według których filtrowane jest przesyłanie alertów do użytkowników, i **przypisuje reguły konkretnym użytkownikom** w zespole.
- Team lead zarządza członkami i rolami (poza dodawaniem/usuwaniem z zespołu).
- Admin dodaje zespoły i konta użytkowników do aplikacji.
- Każdy panel ma pulę własnych widgetów (np. widgety użytkowników, widgety alertów).

## 3. Zasada spójności: lista → wybór → szczegóły

Każdy panel działa tak samo:

1. Widget-lista z filtrem i zaznaczeniem (`AlertList`, `RuleList`, `SourceList`, `GroupList`, `RunbookList`, `UserList`).
2. Wybór elementu ustawia **kontekst wybranego elementu**.
3. Widgety szczegółów słuchają kontekstu i same się odświeżają.

Dzięki temu widgety można dowolnie rozmieszczać, także w panelu `Custom`, bo nie zależą od położenia, tylko od kontekstu.

## 4. Elementy globalne (poza panelami)

- **Sidebar** (zwijany): Live feed, Sources, Alert rules, Groups and runbooks, Team/Users, Custom. Dostępne pozycje zależą od rangi.
- **Pasek górny**: nazwa użytkownika, rola, zespół, wskaźnik „Live”.
- **Tryb edycji układu**:
  - drag-and-drop widgetów,
  - katalog widgetów (drop-down) per panel,
  - grupowanie (zakładki/sekcje) i zagnieżdżanie,
  - zapis układu per użytkownik,
  - przywracanie układu domyślnego.
- **Ekran dołączania do zespołu** (poza dashboardem):
  - nowy użytkownik wpisuje kod zespołu lub jest przypisywany przez admina,

## 5. Panele i widgety

### 5.1 Panel `Alerts` (Live feed) – User

Zbiorcza lista alertów z możliwością filtrowania i wyboru zdarzenia, którym zarządza się w widgetach.

| Widget | Zawartość i featery |
|---|---|
| `AlertList` | Feed na żywo; filtry: All open / Critical / Warning / Info / Acknowledged; severity, tytuł, źródło, reguła, czasy first/last; licznik zgrupowanych zdarzeń; szybki Acknowledge; status „Acknowledged by…”; powiadomienia in-app |
| `AlertDetails` | Nagłówek (severity, status, reguła, źródło, czasy) + pola zdarzenia (Event fields) + ostatnie pasujące zdarzenie (JSON) |
| `ManageAlert` | Acknowledge, Resolve, delete, resend, przekaż innemu użytkownikowi |
| `AlertTimeline` | Historia (otwarcie alertu, SMS, połączenia, retry, grupowanie, otwarcie z linku) + dodawanie notatek |
| `Instructions` | Runbook przypisany do reguły: checklist / markdown / wiki / tekst; link do pełnej strony w wiki |

### 5.2 Panel `Rules` – Team lead

Lista reguł z filtrowaniem i wyborem; wybrana reguła zasila widgety edytora.

| Widget | Zawartość i featery |
|---|---|
| `RuleList` | Filtr, wybór, dodanie reguły |
| `RuleEditor` (kontener) | Nazwa reguły, źródło; zawiera poniższe widgety |
| `FilterAssistant` | Warunki „gdy zdarzenie spełnia wszystkie”, dodawanie warunków; asystent „What do you want to catch?”; przykładowe zdarzenie; test na ostatnich 100 zdarzeniach |
| `SeveritySelector` | Critical / Warning / Info; wstępnie wybiera kanały powiadomień (można dodać/usunąć ręcznie) |
| `NotificationTargets` | Odbiorcy: użytkownicy i grupy; kanały (SMS, połączenie, e-mail, in-app); retry (3 próby) |
| `GroupingAndLimits` | Pola grupowania, czas grupowania, maksymalna liczba powiadomień na odbiorcę |
| `RuleRunbook` | Wybór runbooka lub utworzenie nowego; podgląd jako `Instructions` |
| `MessagePreview` | Szablon tytułu, pola pokazywane w alercie, podgląd SMS oraz feedu i e-maila |
| `RuleActions` | Save (stosowane od razu), Delete |

### 5.3 Panel `Sources` – Team lead

| Widget | Zawartość i featery |
|---|---|
| `SourceList` | Lista źródeł; typ i format; status (Receiving / brak zdarzeń od N min); liczba reguł |
| `AddSourceWizard` | Krok 1: typ (HTTP endpoint, Webhook, Kafka topic, RabbitMQ queue), nazwa, adres; krok 2: format (JSON, key=value, własny z przykładu), przykładowe zdarzenie, wykrywanie pól; krok 3: weryfikacja pól (timestamp, level, service, extra, message) |
| `SourceDetails` | Edycja źródła; powiązane reguły (przejście do `RuleList` przefiltrowanej po źródle) |

### 5.4 Panel `Groups and runbooks` – Team lead

| Widget | Zawartość i featery |
|---|---|
| `GroupList` | Grupy odbiorców; liczba reguł, które z nich korzystają |
| `GroupMembers` | Członkowie (telefon, e-mail); dodanie członka; edycja grupy |
| `RunbookList` | Lista runbooków; typ (Checklist / Markdown / Wiki link / Text) |
| `RunbookEditor` | Tytuł, typ, kroki (jeden na linię); zapis |

### 5.5 Panel `Users` (Team) – Team lead

| Widget | Zawartość i featery |
|---|---|
| `UserList` | Filtr, wybór, imię, e-mail, rola; status „Joined with the code, waiting for a role” |
| `ManageUser` | Nadawanie i zmiana roli, edycja danych użytkownika |
| `AssignRules` | Przypisywanie reguł konkretnemu użytkownikowi (widok od strony użytkownika; ta sama relacja co w `NotificationTargets`) |
| `RolesLegend` | Opis uprawnień ról (widget statyczny) |
| `OrganizationCode` | Kod organizacji, Copy, Generate a new code (tylko Admin) |

### 5.6 Panel `Custom` – wszyscy

- Pusta kanwa, dostęp do wszystkich widgetów dozwolonych dla roli użytkownika.
- Widgety z różnych paneli można zestawiać obok siebie; współdzielą kontekst wyboru (np. `AlertList` + `AlertDetails` + `Instructions`).

### 5.7 Panel Admina

Zakładane: `OrganizationCode`, ewentualnie ustawienia organizacji. Do ustalenia.

## 6. Co zostało połączone z PDF

1. Event fields + Latest matching event → `AlertDetails`.
2. History + Add note → `AlertTimeline`.
3. Runbook + Checklist → jeden widget `Instructions`, używany w `Alerts`, `Rules` i `Groups and runbooks`.
4. Filter assistant + test na 100 zdarzeniach + przykładowe zdarzenie → `FilterAssistant`.
5. Wszystkie listy → wspólny komponent „lista z filtrem i zaznaczeniem”.
6. Członkowie grup (`GroupMembers`) i użytkownicy (`UserList`) → ten sam wzorzec listy użytkowników.

## 7. Macierz uprawnień

| Panel / widget | User | Team lead | Admin |
|---|:-:|:-:|:-:|
| `Alerts`: AlertList, AlertDetails, ManageAlert, AlertTimeline, Instructions | ✔ | ✔ | ✔ |
| `Custom` (widgety dozwolone dla roli) | ✔ | ✔ | ✔ |
| `Rules` | – | ✔ | ✔ |
| `Sources` | – | ✔ | ✔ |
| `Groups and runbooks` | – | ✔ | ✔ |
| `Users`: UserList, ManageUser, AssignRules, RolesLegend | – | ✔ | ✔ |
| `OrganizationCode` | – | – | ✔ |

