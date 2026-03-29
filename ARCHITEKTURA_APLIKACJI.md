# Architektura aplikacji Szatnia

## Cel

Aplikacja obsługuje szybkie wypożyczenia i zwroty strojów chóralnych podczas prób.
Interfejs jest zoptymalizowany pod:

- duże przyciski,
- krótkie ścieżki akcji,
- globalną datę operacyjną,
- działanie na częściowo niekompletnych danych.

## Warstwy

- `domain/`
  - modele domenowe,
  - logika wypożyczeń i zwrotów,
  - walidacja kaucji dla teczek koncertowych.
- `data/`
  - kontrakt repozytorium,
  - mapowanie arkuszy Google Sheets,
  - parser importu CSV z obsługą historycznych kolumn.
- `ui/`
  - ekran główny,
  - kategorie strojów,
  - lista strojów,
  - lista chórzystów,
  - profil chórzysty.

## Google Sheets jako główna baza

Rekomendowany wariant produkcyjny:

1. Android wysyła operacje do pośredniej warstwy HTTP.
2. Warstwa HTTP wywołuje Google Apps Script albo Sheets API.
3. Arkusze `Chorzysci`, `Stroje`, `Historia_Wypozyczen` są jedynym źródłem prawdy.

Powód:

- bezpieczniejsze przechowywanie poświadczeń,
- prostsze reguły dostępu,
- łatwiejsza walidacja i audyt zmian.

W kodzie kontrakt integracji jest przygotowany przez:

- `GoogleSheetsSchema`
- `GoogleSheetsGateway`
- `GoogleSheetsSnapshotMapper`

## Reguły biznesowe

### Wypożyczenie

- zmiana statusu stroju na `Wypożyczony`,
- zapis bieżącego użytkownika,
- dopisanie wpisu do historii,
- wymuszenie kaucji tylko dla `Teczki koncertowe`.

### Zwrot

- zmiana statusu stroju na `Dostępny`,
- wyczyszczenie bieżącego wypożyczającego,
- uzupełnienie `Data_zwrotu` w ostatnim otwartym wpisie historii dla danego stroju.

## Import CSV

Parser obsługuje:

- separatory `;` i `,`,
- daty `DD.MM.YYYY`, `D.M.YYYY`, `DD-MM-YYYY`, `YYYY-MM-DD`,
- puste pola,
- historyczne kolumny typu `ImieJesliZwrocone`.

## Kolejny krok produkcyjny

Do uruchomienia z prawdziwym Google Sheets trzeba dopisać implementację `GoogleSheetsGateway`,
najlepiej przez własny endpoint lub Google Apps Script.
