# Google Sheets Setup

## 1. Przygotuj arkusz

Utwórz arkusz Google Sheets i zostaw trzy zakładki:

- `Chorzysci`
- `Stroje`
- `Historia_Wypozyczen`

Nazwy muszą być dokładnie takie same jak wyżej.

## 2. Wdróż Apps Script

1. Otwórz arkusz.
2. Wybierz `Rozszerzenia -> Apps Script`.
3. Wklej zawartość z [Code.gs](C:/Users/igozd/AndroidStudioProjects/Szatnia/google-apps-script/Code.gs).
4. Zapisz projekt.
5. Kliknij `Wdróż -> Nowe wdrożenie`.
6. Wybierz typ `Aplikacja internetowa`.
7. Ustaw dostęp:
   - wykonuj jako: `Ty`
   - kto ma dostęp: `Każdy, kto ma link`
8. Skopiuj adres `Web App URL`.

## 3. Podłącz aplikację

1. Otwórz ekran `SYNC` w aplikacji.
2. Wklej `Web App URL`.
3. Kliknij `ZAPISZ ADRES`.
4. Użyj:
   - `POBIERZ Z GOOGLE SHEETS`, aby wczytać dane z arkusza
   - `ZAPISZ DO GOOGLE SHEETS`, aby nadpisać stan arkusza danymi z aplikacji

## 4. Import CSV

Na ekranie `SYNC` możesz:

- wkleić pełny CSV chórzystów i zaimportować go do bazy,
- wkleić historię jednej kategorii strojów i odbudować aktualny stan wypożyczeń wyłącznie z dat.

Po imporcie aplikacja automatycznie wyśle nowy stan do Google Sheets, jeśli adres Web App jest zapisany.
