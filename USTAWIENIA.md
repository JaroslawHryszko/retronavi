# RetroNavi - zalecane ustawienia dla slabego sprzetu

Poradnik dla telefonow z Android 2.3-4.x, 512 MB RAM, wolnym procesorem ARM.
Ustawienia dostepne w menu: overflow (trzy kropki) -> Settings.


## Wydajnosc mapy (najwazniejsze)

### Faster mapdrawing -> WLACZ
Klucz: c_linedrawing
Domyslnie: wylaczone

Przelacza rysowanie linii z Javy (setki przejsc JNI na klatke) na natywny C
(bezposredni zapis pikseli do bitmapy). Duza roznica na slabym sprzecie.
Linie moga wygladac troche gorzej - ale mapa rysuje sie wyraznie szybciej.

### Antialias -> WYLACZ
Klucz: use_anti_aliasing
Domyslnie: wlaczone

Wygladzanie krawedzi. Na starych GPU (Adreno 200 i podobne) to znaczne obciazenie.
Wylaczenie daje szybsze rysowanie kosztem lekko postrzepionych linii.

### Map filtering -> WYLACZ
Klucz: use_map_filtering
Domyslnie: wlaczone

Filtrowanie bilinearne bitmap. Ladniejsze, ale wolniejsze.
Na malym ekranie roznica w jakosci prawie niezauwazalna.

### Streets only -> do rozważenia
Klucz: streets_only
Domyslnie: wylaczone

Rysuje tylko ulice, bez terenow, budynkow, lasow.
Mapa wyglada uboziej, ale renderuje sie znacznie szybciej.
Przydatne gdy nawet z powyzszymi zmianami mapa jest zbyt wolna.


## Pamiec

### Cache for map -> very small (lowmem devices)
Klucz: mapcache
Domyslnie: small (7168)

Dostepne wartosci: very small (1024), small (7168), normal (11264),
large (20480), extra large (81920), super large (204800).

Na 512 MB RAM ustaw "very small" lub "small". Wyzsze wartosci moga powodowac
OutOfMemory i crashe, szczegolnie przy duzych mapach (Polska = 635 MB).


## Kompas

### Compass -> WYLACZ (jesli nie potrzebujesz)
Klucz: use_compass_heading_base
Domyslnie: wylaczone

Kompas zuzywa CPU - na slabym telefonie odczuwalnie. Kierunek jazdy
jest i tak brany z GPS (co wystarcza przy predkosci > 5 km/h).

### Fast compass -> NIE WLACZAJ
Klucz: use_compass_heading_fast
Domyslnie: wylaczone

Opis w apce mowi wprost: "will eat all your CPU". Na slabym sprzecie
zamrozi aplikacje.


## Rysowanie

### Smooth drawing -> WYLACZ na bardzo slabym sprzecie
Klucz: use_smooth_drawing
Domyslnie: wlaczone

Plynniejsze przesuwanie mapy. Na starych telefonach moze powodowac
wieksze opoznienia zamiast je zmniejszac.

### Even smoother drawing -> NIE WLACZAJ
Klucz: use_more_smooth_drawing
Domyslnie: wylaczone

Jak wyzej ale gorzej. "will eat all your CPU".

### 3D map -> WYLACZ
Klucz: show_3d_map
Domyslnie: wylaczone

Wymaga dodatkowej bitmapy w pamieci i transformacji perspektywy.
Na 512 MB RAM to strata pamieci i CPU.

### Multipolygons -> do rozważenia
Klucz: show_multipolygons
Domyslnie: wlaczone

Rysuje dodatkowe linie i obszary z multipolygonow (np. granice lasow).
Wylaczenie przyspiesza renderowanie.

### More map detail -> domyslne (0)
Klucz: more_map_detail
Domyslnie: 0

Nie zwiekszaj - wiecej detali = wolniej. Domyslne jest OK.

### Oneway arrows -> do rozważenia
Klucz: gui_oneway_arrows
Domyslnie: wlaczone

Strzalki jednokierunkowe na ulicach. Wylaczenie zmniejsza ilosc
rysowanych elementow, ale strzalki sa przydatne przy nawigacji.


## GPS

### Follow GPS -> WLACZ
Klucz: follow_gps
Domyslnie: wlaczone

Potrzebne do nawigacji. Nie wylaczaj.

### Lock on roads -> WLACZ (dla samochodu/roweru)
Klucz: use_lock_on_roads
Domyslnie: wlaczone

Przyciaga pozycje do najblizszej drogi. Wylacz tylko gdy chodzisz pieszo.

### AGPS -> WLACZ
Klucz: use_agps
Domyslnie: wlaczone

Szybszy fix GPS. Wymaga internetu, ale tylko na chwile.

### Sat status -> WYLACZ
Klucz: show_sat_status
Domyslnie: wlaczone

Wyswietlanie statusu satelitow. Drobna oszczednosc.


## Interfejs

### Hide top bar / bottom bar -> wedlug preferencji
Klucz: hide_top_bar, hide_bottom_bar

Na malym ekranie (3.2" jak Wildfire S) ukrycie barkow daje duzo wiecej
miejsca na mape. Przydatne szczegolnie na rowerze.

### Vehicle in center -> wedlug preferencji
Klucz: show_vehicle_in_center
Domyslnie: wylaczone (pojazd w dolnej czesci)

Na malym ekranie warto sprobowac obu ustawien.


## Podsumowanie: profil "slaby telefon"

Wlacz: c_linedrawing, follow_gps, lock_on_roads, agps, autozoom
Wylacz: use_anti_aliasing, use_map_filtering, use_compass_heading_base,
        show_3d_map, use_more_smooth_drawing, show_sat_status
Cache: very small (1024) lub small (7168)
Opcjonalnie wylacz: use_smooth_drawing, show_multipolygons, gui_oneway_arrows
