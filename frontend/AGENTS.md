# FANS

Organizuj kod według funkcji w `src/features/<funkcja>/`. Każdy moduł udostępnia publiczne API w `index.ts`. Z innego modułu importuj je przez `#features/<funkcja>`. Aliasy są zadeklarowane w `package.json`.

- W `components/` trzymaj widoki danej funkcji. Wspólne kontrolki trafiają do `src/components/`, a elementy ramy aplikacji do `src/layouts/`.
- W `domain/` umieszczaj czyste funkcje opisujące zachowanie aplikacji. Przekazuj dane i czas jako argumenty. Testy reguł umieszczaj obok implementacji.
- W `services/` trzymaj dostęp do danych i źródła danych demonstracyjnych. W `utils/` umieszczaj pomocnicze funkcje specyficzne dla modułu.
- Stan współdzielony między widgetami jest w React Context modułu `workspace`. Stan formularza, menu lub interakcji trzymaj przy komponencie, który go używa.
- Używaj nazw plików w kebab-case, nazw komponentów w PascalCase i aliasu `#` dla importów z aplikacji.

Zapis danych demo, roli i układów obsługuje `src/services/local-storage.ts`. Przy zmianach formatu zachowaj obsługę istniejących zapisów `fans:` oraz migrację z `relay:`.

Do weryfikacji służą `npm test`, `npm run lint` i `npm run build`.
