# Squawk

Lokalny prototyp operacyjnej platformy alertów w React, TypeScript i Vite. Interfejs jest po angielsku. Do demonstracji wystarcza przeglądarka.

## Uruchomienie

W katalogu projektu, przy użyciu Node.js 22.12+ lub 24 i npm:

```sh
npm ci
npm run dev
```

Otwórz adres podany przez Vite. Polecenia weryfikacyjne to `npm test`, `npm run typecheck`, `npm run lint` i `npm run build`. Gotowe pliki statyczne trafiają do `dist`. Polecenie `npm run preview` pozwala obejrzeć tę wersję.

## Tryb demo i tryb live

Bez pliku `.env` aplikacja działa na danych lokalnych, jak opisano niżej. Z plikiem `.env` łączy się z wdrożonym backendem: logowanie przez Cognito, dane z REST API, feed i powiadomienia w aplikacji przez WebSocket, zapis zmian na serwerze, asystent reguł na prawdziwym modelu, push na urządzenie.

```sh
terraform -chdir=../infra/app output -raw frontend_env > .env
npm run dev
```

Publikacja na S3 i CloudFront: `../scripts/deploy-frontend.sh`. Kod zaproszenia organizacji demo jest w `Docs/frontend-integration/README.md`.

Kod połączenia z backendem jest w `src/services/backend/`: `gateway.ts` (odczyt i zapis), `to-workspace.ts` i `to-backend.ts` (tłumaczenie między kształtem API a danymi interfejsu), `plan-changes.ts` (różnica dwóch stanów jako lista wywołań API), `conditions.ts` (tekst warunków na strukturę reguły i z powrotem). Stan trybu live trzyma `src/features/workspace/services/live-workspace.ts`.

W trybie live nie ma przełącznika ról ani **Prototype controls**: rola pochodzi z serwera. Rzeczy, których backend nie ma, są wyłączone lub zostają lokalne: usunięcie alertu zamyka go (historia zostaje), okna dostępności odbiorców i kanał zapasowy nie są wysyłane, odbiorcą może być osoba lub grupa.

## Pierwsza demonstracja

1. W **Alerts** otwórz **Order settlement failed**. W szczegółach są odbiorca dyżurny, kanały Phone i SMS oraz 500 zdarzeń zgrupowanych w jeden alert.
2. Użyj **Acknowledge alert**, dodaj notatkę w **Timeline** i zaznacz krok w **Runbook**. Kolejna akcja to **Resolve alert**. Menu z trzema kropkami umożliwia zmianę odbiorcy, ponowienie wysyłki i usunięcie alertu. Potwierdzenie lokalnej zmiany zawiera **Undo**.
3. W **Rules** utwórz **Draft rule**, rozwiń **Generate conditions** i podaj cel. Można edytować propozycję i przetestować ją na 100 przykładowych zdarzeniach. Akceptacja zmienia wersję roboczą. **Save rule** zapisuje regułę.
4. W **Sources → Add source** podaj przykładowy adres i zdarzenie. Przed zapisaniem źródła sprawdź i zatwierdź mapowanie pól.
5. W **Customize view** przeciągaj nagłówki widgetów po 12-kolumnowej siatce. Dolny prawy narożnik zmienia szerokość i wysokość. Uchwyty obsługują strzałki klawiatury, a menu panelu pozwala wpisać dokładną pozycję i wymiary. **Undo** cofa ostatni ruch, a **Save layout** zapisuje rozmieszczenie. **Custom view** umożliwia łączenie dozwolonych widgetów z różnych obszarów. Jeśli zabraknie powiązanej listy, pojawi się pole wyboru obiektu.

## Role i stany

Początkową rolą jest **Admin**. Menu profilu na dole nawigacji pozwala zmienić rolę i otworzyć **Prototype controls**.

| Rola | Dostęp |
| --- | --- |
| User | Alerts i Custom view |
| Team lead | Dodatkowo Rules, Sources, Groups, Runbooks oraz Users w obrębie Platform operations |
| Admin | Dodatkowo Admin, wszystkie konta, zespoły i kody dołączenia |

W **Prototype controls → Workspace state** dostępne są ładowanie, błąd z ponowieniem, pusty widok, częściowe dane i odmowa dostępu. **Team access → Without a team** otwiera formularz dołączenia. Początkowy kod zespołu Platform operations to `PLAT-4826`. Aktualny kod znajduje się w panelu Admin oraz w kontrolkach prototypu.

## Dane i zachowanie

Dane startowe są w `src/features/workspace/services/demo-workspace.ts`, zdarzenia do testu reguł w `src/features/rules/services/demo-events.ts`, a profil ruchu w `src/features/alerts/domain/inbound-baseline.ts`. Zmiany danych produktu, rola i układy zapisują się w `localStorage` pod kluczami zaczynającymi się od `fans:`. Układ ma osobny klucz dla każdego panelu i roli. Usunięcie tych kluczy i odświeżenie aplikacji przywraca stan początkowy.

W otwartym feedzie lokalny timer co minutę sygnalizuje nowe alerty. Pojawiają się na liście po wybraniu **Show latest**. W menu profilu wybierz **Open prototype controls → Pause demo feed**, aby wstrzymać dopływ. Wysyłka, potwierdzenia dostarczenia, awarie kanałów, ponowienia oraz sugestie AI są symulowane. Fonty i ikony są dołączone do aplikacji.

## Założenia projektu

Jedna firma i jeden roboczy kontekst operacyjny. Wybrany rekord steruje powiązanymi widgetami również po zmianie ich kolejności. Szeroka kanwa mieści kolejkę i inspektor obok siebie. Przy ograniczonym miejscu szczegóły mają własny widok z powrotem do listy.

Paleta Squawk korzysta z jasnego motywu STAGEUI. Kamiennoszara rama otacza białą przestrzeń roboczą. Asta Sans służy do tekstu i kontrolek, a Lora do tytułów i liczb. Błękit wyróżnia zaznaczenie, fokus i aktywność. Ikony oraz etykiety wskazują ważność alertów.
