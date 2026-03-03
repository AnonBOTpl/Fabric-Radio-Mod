# 📻 Fabric Radio Mod

W pełni funkcjonalny, kliencki mod dodający radio internetowe do Minecraft Fabric (1.21.4). Słuchaj tysięcy prawdziwych stacji radiowych bezpośrednio w grze, bez użycia zewnętrznych programów!

## ✨ Funkcje
* **Wyszukiwarka na żywo:** Zasilana darmowym API *Radio Browser*, dająca dostęp do stacji z całego świata.
* **Silnik w tle:** Odtwarza strumienie MP3 w osobnym wątku Javy. Gwarantuje brak lagów i ścinek w Minecrafcie!
* **Zaawansowane Filtry:** Łatwe sortowanie stacji po Kraju lub Gatunku muzycznym za pomocą rozwijanych menu.
* **System Ulubionych:** Zapisuj swoje stacje jednym kliknięciem (★). Eksportuj i importuj ulubione jako plik JSON.
* **Okładki Albumów:** Mod automatycznie pobiera okładkę aktualnie granej piosenki z iTunes API — widoczna w powiadomieniu i w głównym menu.
* **Interaktywne Powiadomienia (Toasty):** Sprawdzaj, co aktualnie gra w eleganckim, dostosowywalnym okienku (Małe / Średnie / Duże). Kliknij przycisk ⛔, aby trwale zablokować irytujące piosenki lub reklamy.
* **Historia Piosenek:** Przeglądaj ostatnie 20 granych piosenek w dedykowanym ekranie. Kliknij wpis, aby skopiować go do schowka.
* **Dynamiczne Ikony:** Mod pobiera logotypy stacji — obsługuje formaty PNG, JPEG, WEBP i ICO.
* **Pełna Konfiguracja:** Wsparcie dla ModMenu pozwala na zmianę głośności, rozmiaru toastu, czasu wyświetlania i zarządzanie Czarną Listą.

## ⚙️ Instalacja i Wymagania
Jest to modyfikacja działająca **tylko u klienta**. Nie musisz instalować jej na serwerze!
1. Zainstaluj [Fabric Loader](https://fabricmc.net/).
2. Pobierz i wrzuć plik `radio-mod-1.2.0.jar` do folderu `.minecraft/mods`.
3. **Wymagane:** [Fabric API](https://modrinth.com/mod/fabric-api)
4. **Zalecane:** [ModMenu](https://modrinth.com/mod/modmenu) (do otwierania ustawień).

## 🎮 Sterowanie
* Naciśnij **`R`** (Domyślnie) w grze, aby otworzyć główne Menu Radia.
* Użyj suwaka w menu, aby płynnie kontrolować głośność, niezależnie od dźwięków gry.

## 📝 Changelog

### v1.2.0
* **NOWOŚĆ:** Automatyczne pobieranie okładek albumów z iTunes API — widoczne w toaście i w menu
* **NOWOŚĆ:** Historia piosenek (ostatnie 20) — kliknij wpis aby skopiować do schowka
* **NOWOŚĆ:** Wybór rozmiaru toastu w ustawieniach (Mały / Średni / Duży)
* **NOWOŚĆ:** Eksport i import ulubionych stacji jako plik JSON
* **POPRAWKA:** Ikony stacji obsługują teraz JPEG, WEBP i ICO (wcześniej tylko PNG działało)
* **POPRAWKA:** Crash NullPointerException przy błędzie pobierania ikony
* **POPRAWA:** Wyrównany układ przycisków w głównym menu

### v1.0.0
* Pierwsze wydanie

## 📝 Autorzy
* Stworzone przez **AnonBOT**.
* Dekodowanie audio: [MP3SPI / JLayer](https://github.com/tritonus-os/tritonus).
* Baza stacji: [Radio Browser](https://www.radio-browser.info/).
* Okładki albumów: [iTunes Search API](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/).
