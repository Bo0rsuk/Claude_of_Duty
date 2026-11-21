# Lista stron, paneli i widgetów (Alerting platform)

Dokument przedstawia założenia widoków w aplikacji.

## 1. Kontekst i model domenowy

- Aplikacja działa wewnątrz **jednej firmy**. Nie ma pojęcia wielu organizacji.
- **Zespół** to grupa ludzi dowodzona przez Team leada.
- Użytkownik należy do zespołu, którym zarządza Team lead.
- **Konta użytkowników nie są zakładane przez samych użytkowników.** Konto otwiera Admin.
- Admin dodaje zespoły i konta użytkowników do aplikacji.
- Admin przypisuje użytkowników do zespołów albo użytkownik dołącza do zespołu **kodem zespołu**, który Admin wygenerował.
- Team lead tworzy reguły (rules), według których filtrowane jest przesyłanie alertów do użytkowników, i **przypisuje reguły konkretnym użytkownikom** w zespole.
- Team lead zarządza członkami i rangami w swoim zespole (poza dodawaniem i usuwaniem z zespołu).

## 2. Główna idea interfejsu

- Każdy użytkownik aplikacji zarządza własnym widokiem i układem widgetów.
- Układ powstaje przez **drag-and-drop** widgetów wybieranych z drop-down list dostępnych widgetów.
- Aplikacja ma **zwijany sidebar** z wyborem głównych paneli.
- Widgety mogą być **zagnieżdżane** według pochodzenia (np. widgety użytkownika wewnątrz widgetu zarządzania użytkownikami).
- Widgety mogą być **grupowane** według preferencji użytkownika.
- Każdy panel ma pulę własnych widgetów (np. widgety użytkowników, widgety alertów).

## 3. Rangi i dostępność

Rangi są hierarchiczne. Każda wyższa ranga widzi panele wszystkich niższych.

| Ranga | Dostęp |
|---|---|
| **User** | Live feed, acknowledge, resolve, notatki |
| **Team lead** | Wszystko z User + sources, reguły, grupy, runbooki, zarządzanie użytkownikami zespołu |
| **Admin** | Wszystko z Team lead + zespoły, konta użytkowników, kody zespołów |

## 4. Zasada spójności: lista → wybór → szczegóły

Każdy panel działa tak samo:

1. Widget-lista z filtrem i zaznaczeniem (`AlertList`, `RuleList`, `SourceList`, `GroupList`, `RunbookList`, `UserList`, `TeamList`).
2. Wybór elementu ustawia **kontekst wybranego elementu**.
3. Widgety szczegółów słuchają kontekstu i same się odświeżają.

Dzięki temu widgety można dowolnie rozmieszczać, także w panelu `Custom`, bo nie zależą od położenia, tylko od kontekstu.

## 5. Elementy globalne (poza panelami)

- **Sidebar** (zwijany): Live feed, Sources, Alert rules, Groups and runbooks, Team/Users, Admin, Custom. Dostępne pozycje zależą od rangi.
- **Pasek górny**: nazwa użytkownika, ranga, zespół, wskaźnik „Live”.
- **Tryb edycji układu**:
  - drag-and-drop widgetów,
  - katalog widgetów (drop-down) per panel,
  - grupowanie (zakładki/sekcje) i zagnieżdżanie,
  - zapis układu per użytkownik,
  - przywracanie układu domyślnego.
- **Ekran dołączania do zespołu** (poza dashboardem):
  - widoczny dla zalogowanego użytkownika, który nie jest jeszcze przypisany do zespołu,
  - użytkownik wpisuje kod zespołu (albo czeka, aż Admin go przypisze),
  - po dołączeniu kodem użytkownik czeka na nadanie rangi przez Team leada.

## 6. Panele i widgety

### 6.1 Panel `Alerts` (Live feed) – User

Zbiorcza lista alertów z możliwością filtrowania i wyboru zdarzenia, którym zarządza się w widgetach.

| Widget | Zawartość i featery |
|---|---|
| `AlertList` | Feed na żywo; filtry: All open / Critical / Warning / Info / Acknowledged; severity, tytuł, źródło, reguła, czasy first/last; licznik zgrupowanych zdarzeń; szybki Acknowledge; status „Acknowledged by…”; powiadomienia in-app |
| `AlertDetails` | Nagłówek (severity, status, reguła, źródło, czasy) + pola zdarzenia (Event fields) + ostatnie pasujące zdarzenie (JSON) |
| `ManageAlert` | Acknowledge, Resolve, delete, resend, przekaż innemu użytkownikowi |
| `AlertTimeline` | Historia (otwarcie alertu, SMS, połączenia, retry, grupowanie, otwarcie z linku) + dodawanie notatek |
| `Instructions` | Runbook przypisany do reguły: checklist / markdown / wiki / tekst; link do pełnej strony w wiki |

### 6.2 Panel `Rules` – Team lead

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

### 6.3 Panel `Sources` – Team lead

| Widget | Zawartość i featery |
|---|---|
| `SourceList` | Lista źródeł; typ i format; status (Receiving / brak zdarzeń od N min); liczba reguł |
| `AddSourceWizard` | Krok 1: typ (HTTP endpoint, Webhook, Kafka topic, RabbitMQ queue), nazwa, adres; krok 2: format (JSON, key=value, własny z przykładu), przykładowe zdarzenie, wykrywanie pól; krok 3: weryfikacja pól (timestamp, level, service, extra, message) |
| `SourceDetails` | Edycja źródła; powiązane reguły (przejście do `RuleList` przefiltrowanej po źródle) |

### 6.4 Panel `Groups and runbooks` – Team lead

| Widget | Zawartość i featery |
|---|---|
| `GroupList` | Grupy odbiorców; liczba reguł, które z nich korzystają |
| `GroupMembers` | Członkowie (telefon, e-mail); dodanie członka; edycja grupy |
| `RunbookList` | Lista runbooków; typ (Checklist / Markdown / Wiki link / Text) |
| `RunbookEditor` | Tytuł, typ, kroki (jeden na linię); zapis |

### 6.5 Panel `Users` (Team) – Team lead

Dotyczy użytkowników zespołu Team leada.

| Widget | Zawartość i featery |
|---|---|
| `UserList` | Filtr, wybór, imię, e-mail, ranga; status „Dołączył kodem, oczekuje na rangę” |
| `ManageUser` | Nadawanie i zmiana rangi, edycja danych użytkownika |
| `AssignRules` | Przypisywanie reguł konkretnemu użytkownikowi (widok od strony użytkownika; ta sama relacja co w `NotificationTargets`) |
| `RolesLegend` | Opis uprawnień rang (widget statyczny) |

### 6.6 Panel `Admin` – Admin

| Widget | Zawartość i featery |
|---|---|
| `TeamList` | Lista zespołów; dodanie zespołu; wybór zespołu |
| `ManageTeam` | Edycja zespołu, wskazanie Team leada, przypisywanie i usuwanie użytkowników z zespołu |
| `TeamCode` | Kod zespołu, Copy, Generate a new code |
| `AccountList` | Lista kont użytkowników; filtr po zespole; użytkownicy bez zespołu |
| `CreateAccount` | Otwieranie konta użytkownika; ranga i przypisanie do zespołu |

### 6.7 Panel `Custom` – wszyscy

- Pusta kanwa, dostęp do wszystkich widgetów dozwolonych dla rangi użytkownika.
- Widgety z różnych paneli można zestawiać obok siebie; współdzielą kontekst wyboru (np. `AlertList` + `AlertDetails` + `Instructions`).

## 7. Macierz uprawnień

| Panel / widget | User | Team lead | Admin |
|---|:-:|:-:|:-:|
| `Alerts`: AlertList, AlertDetails, ManageAlert, AlertTimeline, Instructions | TAK | TAK | TAK |
| `Custom` (widgety dozwolone dla rangi) | TAK | TAK | TAK |
| `Rules` | NIE | TAK | TAK |
| `Sources` | NIE | TAK | TAK |
| `Groups and runbooks` | NIE | TAK | TAK |
| `Users`: UserList, ManageUser, AssignRules, RolesLegend | NIE | TAK | TAK |
| `Admin`: TeamList, ManageTeam, TeamCode, AccountList, CreateAccount | NIE | NIE | TAK |
