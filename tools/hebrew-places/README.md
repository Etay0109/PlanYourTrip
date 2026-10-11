# Hebrew place table generator

Builds `app/src/main/java/com/example/planyourtrip/data/place/HebrewPlaceTable.kt`, the table the destination field
uses for Hebrew text (Photon has no Hebrew names for places abroad). See CLAUDE.md, "Destination suggestions and flags".

## Files
- `places_source.txt` — hand-written cities, islands and regions, most popular first:
  `Hebrew name|other spellings (comma-separated)|English query for Photon|expected country code`.
- `countries_source.txt` — countries, same columns. Started from the JDK's Hebrew country names (`Countries.java`
  prints them), then edited by hand. An optional 5th column `place` searches any place type, for a country Photon files
  as an archipelago or a state (the Bahamas).
- `gen_places.py` — looks every entry up on Photon once (1 s apart, `lang=en`) and checks it.
- `emit_table.py` — writes the Kotlin table from the checked results.

## Regenerate
```
cd tools/hebrew-places
python3 -I gen_places.py .        # exits 1 and lists failures in failures.txt
python3 -I emit_table.py . ../../app/src/main/java/com/example/planyourtrip/data/place/HebrewPlaceTable.kt
cd ../.. && ./gradlew testDebugUnitTest   # HebrewPlacesTest checks every entry
```
- An entry **fails** when Photon's top result has another country code than expected or a type outside the app's
  allowlist (country, state, region, province, county, city, town, village, municipality, island, archipelago).
  Review every failure by hand: fix the English query or leave the entry out. Never commit a table with failures.
- Left out on purpose (2026-10-11 review): overseas territories that Photon files under their parent country's code
  (Aruba, Guam, Hong Kong, Macau, Puerto Rico, Réunion…), Saint Helena (confusing label), Lake Garda, Chalkidiki,
  Paros, Sinai, Lapland, Provence, Alsace, Cusco (no matching Photon place).
- `results.json` caches Photon's answers (delete it to look everything up again); it, `table.json` and
  `failures.txt` are generated and not committed.
- Photon's usage policy: a few hundred requests at most, one per second, with the app's User-Agent. Data © OpenStreetMap
  contributors (ODbL).
