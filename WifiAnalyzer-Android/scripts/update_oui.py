#!/usr/bin/env python3
"""Generate offline MAC-prefix vendor index from public IEEE registration CSVs.

Run before Gradle: python3 scripts/update_oui.py
Outputs app/src/main/assets/oui.tsv; normal app runtime performs no remote lookup.
"""
import csv
import io
import pathlib
import re
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/oui.tsv"
REGISTRIES = [
    ("MA-L", "https://standards-oui.ieee.org/oui/oui.csv", 6, 10000),
    ("MA-M", "https://standards-oui.ieee.org/oui28/mam.csv", 7, 100),
    ("MA-S", "https://standards-oui.ieee.org/oui36/oui36.csv", 9, 100),
]

def download(name, url, length, threshold):
    request = urllib.request.Request(url, headers={"User-Agent": "NetScope-Android/2.2 (IEEE public OUI import)"})
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    rows = csv.DictReader(io.StringIO(data.decode("utf-8-sig")))
    if not rows.fieldnames or "Assignment" not in rows.fieldnames or "Organization Name" not in rows.fieldnames:
        raise ValueError(f"{name}: formato de fichero IEEE desconocido: {rows.fieldnames}")
    result = {}
    for row in rows:
        assignment = re.sub(r"[^0-9A-F]", "", row.get("Assignment", "").upper())
        vendor = row.get("Organization Name", "").replace("\t", " ").replace("\r", " ").replace("\n", " ").strip()
        if len(assignment) == length and vendor:
            result[assignment] = vendor
    if len(result) < threshold:
        raise ValueError(f"{name}: registros insuficientes: {len(result)}")
    print(f"{name}: {len(result)} registros")
    return result

def main():
    assignments = {}
    for name, url, length, floor in REGISTRIES:
        try:
            assignments.update(download(name, url, length, floor))
        except Exception as exc:
            if name == "MA-L":
                raise RuntimeError("No se pudo obtener el registro principal IEEE MA-L") from exc
            print(f"AVISO: {name} no disponible, se omite: {exc}")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        "# Índice de titulares de prefijos MAC registrado por IEEE. No identifica modelos.\n"
        + "".join(f"{prefix}\t{company}\n" for prefix, company in sorted(assignments.items())),
        encoding="utf-8",
    )
    print(f"Salida: {OUTPUT}, {len(assignments)} asignaciones")

if __name__ == "__main__":
    main()
