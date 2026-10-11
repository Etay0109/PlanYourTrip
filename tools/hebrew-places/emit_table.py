"""Writes HebrewPlaceTable.kt from <dir>/table.json. Usage: emit_table.py <dir> <output.kt>"""
import json, sys
rows = json.load(open(sys.argv[1] + "/table.json", encoding="utf-8"))
rows = [r for r in rows if r["kind"] == "place"] + [r for r in rows if r["kind"] == "country"]
def k(v):
    if v is None: return "null"
    return '"' + v.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'
import re
FINALS = str.maketrans("ךםןףץ", "כמנפצ")
def norm(t):  # the same rules as HebrewPlaces.normalize
    t = re.sub("[\u05BE\\-\u2010-\u2015]", " ", t)
    t = re.sub("[\u0591-\u05BD\u05BF-\u05C7]", "", t)
    t = re.sub("[\u05F3\u05F4'\"`\u2019\u2018\u201C\u201D]", "", t)
    return re.sub("\\s+", " ", t.translate(FINALS)).strip().lower()
lines = []
for r in rows:
    # Spellings that normalize to a name the entry already has match anyway: left out.
    seen, kept = {norm(r["he"])}, []
    for a in r["aliases"]:
        if norm(a) not in seen: seen.add(norm(a)); kept.append(a)
    r["aliases"] = kept
    aliases = "listOf(" + ", ".join(k(a) for a in r["aliases"]) + ")" if r["aliases"] else "emptyList()"
    lines.append(f'    p({k(r["he"])}, {aliases}, {k(r["name"])}, {k(r["region"])}, {k(r["country"])}, {k(r["cc"])}, {r["lat"]}, {r["lon"]}),')
out = '''package com.example.planyourtrip.data.place

// GENERATED once on 2026-10-11 from a hand-written list (Hebrew name, other spellings, expected country code):
// every entry was looked up on Photon (lang=en) and kept only when its top result had the expected country code and
// a place type the app offers. Names, regions and coordinates are Photon's (OpenStreetMap data, © OpenStreetMap
// contributors). Popular cities, islands and regions first, then the countries (Hebrew names from the JDK's CLDR
// data, checked by hand). Edit by hand only to fix a mistake; HebrewPlacesTest checks every entry.

private fun p(
    hebrew: String,
    aliases: List<String>,
    name: String,
    region: String?,
    country: String?,
    countryCode: String,
    latitude: Double,
    longitude: Double,
) = HebrewPlace(hebrew, aliases, PlaceSuggestion(name, region, country, countryCode, latitude, longitude))

/** The places Hebrew text finds (see [HebrewPlaces]); the order is the order of equally good matches. */
val HEBREW_PLACE_TABLE: List<HebrewPlace> = listOf(
''' + "\n".join(lines) + "\n)\n"
open(sys.argv[2], "w", encoding="utf-8").write(out)
print(len(rows), "entries")
