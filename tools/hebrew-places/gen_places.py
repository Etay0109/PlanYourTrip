"""Looks every Hebrew table entry up on Photon once and checks it (see README.md). Usage: gen_places.py <dir>.
Reads <dir>/countries_source.txt and <dir>/places_source.txt; writes <dir>/results.json (cache), <dir>/table.json and
<dir>/failures.txt; exits 1 when any entry fails."""
import json, os, sys, time, urllib.parse, urllib.request
d = sys.argv[1]
TYPES = {"country","state","region","province","county","city","town","village","municipality","island","archipelago"}
UA = "PlanYourTrip/1.0 (Android; com.example.planyourtrip) table-generator"
cache_path = os.path.join(d, "results.json")
cache = json.load(open(cache_path)) if os.path.exists(cache_path) else {}

def read(name, kind):
    rows = []
    for line in open(os.path.join(d, name), encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"): continue
        he, al, en, cc, *opt = line.split("|")
        # A 5th column "place": a country Photon files as another place type (an archipelago, a state).
        rows.append(dict(he=he, aliases=[a for a in al.split(",") if a], query=en, cc=cc, kind=kind,
                         any_type=opt == ["place"]))
    return rows

def photon(query, tag):
    key = tag + "|" + query
    if key not in cache:
        url = "https://photon.komoot.io/api/?" + urllib.parse.urlencode(
            [("q", query), ("lang", "en"), ("limit", "5"), ("osm_tag", tag)])
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
        cache[key] = json.load(urllib.request.urlopen(req, timeout=20))
        json.dump(cache, open(cache_path, "w"), ensure_ascii=False)
        time.sleep(1)
    return cache[key]

entries = read("countries_source.txt", "country") + read("places_source.txt", "place")
out, failures = [], []
for e in entries:
    tag = "place:country" if e["kind"] == "country" and not e["any_type"] else "place"
    feats = photon(e["query"], tag).get("features", [])
    if not feats:
        failures.append(f"{e['he']} ({e['query']}, expected {e['cc']}): no result"); continue
    f = feats[0]; p = f["properties"]
    problems = []
    if p.get("osm_key") != "place" or p.get("osm_value") not in TYPES: problems.append(f"type {p.get('osm_key')}={p.get('osm_value')}")
    if (p.get("countrycode") or "").upper() != e["cc"]: problems.append(f"country {p.get('countrycode')}")
    if e["kind"] == "country" and not e["any_type"] and p.get("osm_value") != "country": problems.append("not a country")
    if problems:
        failures.append(f"{e['he']} ({e['query']}, expected {e['cc']}): top result {p.get('name')}, {p.get('state')}, {p.get('country')}: " + "; ".join(problems)); continue
    lon, lat = f["geometry"]["coordinates"]
    name = p["name"]; isc = p.get("osm_value") == "country"
    region = None if isc or e["kind"] == "country" else (p.get("state") or p.get("county"))
    if region and region.lower() == name.lower(): region = None
    out.append(dict(he=e["he"], aliases=e["aliases"], name=name, region=region, country=p.get("country"),
                    cc=e["cc"], lat=round(lat, 5), lon=round(lon, 5), kind=e["kind"]))
json.dump(out, open(os.path.join(d, "table.json"), "w"), ensure_ascii=False, indent=1)
open(os.path.join(d, "failures.txt"), "w").write("\n".join(failures) + "\n")
print(f"{len(out)} ok, {len(failures)} failed")
sys.exit(1 if failures else 0)
