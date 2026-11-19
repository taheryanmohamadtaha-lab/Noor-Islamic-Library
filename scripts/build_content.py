#!/usr/bin/env python3
"""
Builds the content assets of Noor Islamic Library from AUTHENTIC, attributed sources.

Rules (from the project brief):
  * No Arabic text or translation is ever generated or completed by AI.
  * Quran: downloaded from the Tanzil Project (fallback: AlQuran.Cloud, itself derived from Tanzil).
  * Mafatih / Sahifa / Nahj al-Balagha: imported ONLY from licensed files that the project owner
    places in content/sources/<book>.json (see docs/CONTENT-FORMAT.md). If the file is absent the
    book is reported as MISSING and the app shows it as unavailable. Nothing is invented.
  * Every book is checked against reference counts. Mismatch => status "partial" (never silently "complete").
  * If the Quran fails QC the script exits with an error, so no APK with wrong/incomplete Quran is produced.

Outputs:
  app/src/main/assets/content/<book>.json, manifest.json
  qc-report/QC-REPORT.md, qc-report/qc-report.json
"""
import hashlib
import json
import os
import re
import sys
import time
import urllib.request
from datetime import datetime, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "content")
SOURCES = os.path.join(ROOT, "content", "sources")
REPORT = os.path.join(ROOT, "qc-report")
CACHE = os.path.join(ROOT, "content", "cache")

# Reference: number of ayahs per surah (standard Hafs numbering, total 6236).
AYAH_COUNTS = [7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78,
               118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38,
               29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20,
               56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
               11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6]
SURAH_NAMES_FA = ["فاتحه", "بقره", "آل‌عمران", "نساء", "مائده", "انعام", "اعراف", "انفال", "توبه", "یونس", "هود",
                  "یوسف", "رعد", "ابراهیم", "حجر", "نحل", "اسراء", "کهف", "مریم", "طه", "انبیاء", "حج", "مؤمنون",
                  "نور", "فرقان", "شعراء", "نمل", "قصص", "عنکبوت", "روم", "لقمان", "سجده", "احزاب", "سبأ", "فاطر",
                  "یس", "صافات", "ص", "زمر", "غافر", "فصلت", "شوری", "زخرف", "دخان", "جاثیه", "احقاف", "محمد", "فتح",
                  "حجرات", "ق", "ذاریات", "طور", "نجم", "قمر", "الرحمن", "واقعه", "حدید", "مجادله", "حشر", "ممتحنه",
                  "صف", "جمعه", "منافقون", "تغابن", "طلاق", "تحریم", "ملک", "قلم", "حاقه", "معارج", "نوح", "جن",
                  "مزمل", "مدثر", "قیامت", "انسان", "مرسلات", "نبأ", "نازعات", "عبس", "تکویر", "انفطار", "مطففین",
                  "انشقاق", "بروج", "طارق", "اعلی", "غاشیه", "فجر", "بلد", "شمس", "لیل", "ضحی", "شرح", "تین", "علق",
                  "قدر", "بینه", "زلزله", "عادیات", "قارعه", "تکاثر", "عصر", "همزه", "فیل", "قریش", "ماعون", "کوثر",
                  "کافرون", "نصر", "مسد", "اخلاص", "فلق", "ناس"]
assert len(AYAH_COUNTS) == 114 and sum(AYAH_COUNTS) == 6236, "internal reference table is wrong"
assert len(SURAH_NAMES_FA) == 114

TANZIL_TEXT_URLS = [
    "https://tanzil.net/pub/download/index.php?quranType=uthmani&outType=txt-2&agree=true",
]
TANZIL_TRANS_ID = "fa.makarem"
TANZIL_TRANS_URLS = [
    "https://tanzil.net/trans/" + TANZIL_TRANS_ID,
    "https://tanzil.net/trans/?transID=" + TANZIL_TRANS_ID + "&type=txt-2",
]
ALQURAN_CLOUD_TEXT = "https://api.alquran.cloud/v1/quran/quran-uthmani"
ALQURAN_CLOUD_TRANS = "https://api.alquran.cloud/v1/quran/" + TANZIL_TRANS_ID

ARABIC_RE = re.compile(r"[؀-ۿ]")
MARKS_RE = re.compile(r"[ؐ-ًؚ-ٰٟۖ-ۭـ]")

report = {"generatedAt": datetime.now(timezone.utc).isoformat(), "books": {}, "errors": []}


def log(msg):
    print(msg, flush=True)


def http_get(url, tries=3):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Noor-Islamic-Library-build/1.0"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa
            last = e
            time.sleep(2 * (i + 1))
    raise RuntimeError(f"download failed: {url}: {last}")


def sha(b):
    return hashlib.sha256(b).hexdigest()[:12]


def parse_tanzil_txt(raw):
    """Tanzil txt-2 format: sura|aya|text ; lines starting with # are comments."""
    text = raw.decode("utf-8")
    verses, comments = {}, []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("#"):
            comments.append(line.lstrip("# ").strip())
            continue
        parts = line.split("|", 2)
        if len(parts) != 3:
            continue
        s, a, t = parts
        verses[(int(s), int(a))] = t.strip()
    return verses, comments


def fetch_tanzil():
    text_raw = trans_raw = None
    for u in TANZIL_TEXT_URLS:
        try:
            text_raw = http_get(u)
            break
        except Exception as e:  # noqa
            log(f"  ! {e}")
    for u in TANZIL_TRANS_URLS:
        try:
            trans_raw = http_get(u)
            break
        except Exception as e:  # noqa
            log(f"  ! {e}")
    if not text_raw or not trans_raw:
        return None
    ar, ar_c = parse_tanzil_txt(text_raw)
    fa, fa_c = parse_tanzil_txt(trans_raw)
    version = next((c for c in ar_c if c.lower().startswith("version")), "Tanzil Uthmani")
    meta = {
        "source": f"Tanzil Project (tanzil.net) — متن قرآن به رسم عثمانی، {version}",
        "translator": "ناصر مکارم شیرازی (fa.makarem، از پروژه Tanzil)",
        "license": "متن: Creative Commons Attribution 3.0 (Tanzil) — بدون تغییر در متن؛ ترجمه: شرایط Tanzil",
        "version": f"{version}; sha256 text={sha(text_raw)} trans={sha(trans_raw)}",
        "origin": "tanzil",
    }
    return ar, fa, meta


BISMILLAH_NORM = "بسم الله الرحمن الرحیم"


def norm(s):
    s = MARKS_RE.sub("", s)
    for a, b in (("ي", "ی"), ("ى", "ی"), ("ك", "ک"), ("ٱ", "ا"), ("أ", "ا"), ("إ", "ا"), ("آ", "ا")):
        s = s.replace(a, b)
    return re.sub(r"\s+", " ", s).strip()


def fetch_alquran_cloud():
    try:
        a = http_get(ALQURAN_CLOUD_TEXT)
        t = http_get(ALQURAN_CLOUD_TRANS)
    except Exception as e:  # noqa
        log(f"  ! {e}")
        return None
    ar, fa = {}, {}
    stripped = 0
    for raw, target, is_ar in ((a, ar, True), (t, fa, False)):
        data = json.loads(raw)["data"]["surahs"]
        for s in data:
            for ay in s["ayahs"]:
                txt = ay["text"].strip()
                n = ay["numberInSurah"]
                if is_ar and n == 1 and s["number"] not in (1, 9):
                    words = txt.split(" ")
                    if norm(" ".join(words[:4])) == BISMILLAH_NORM:
                        txt = " ".join(words[4:]).strip()
                        stripped += 1
                target[(s["number"], n)] = txt
    log(f"  fallback: removed leading bismillah prefix from {stripped} first-ayahs (format difference only)")
    meta = {
        "source": "AlQuran.Cloud API (quran-uthmani، مبتنی بر Tanzil)",
        "translator": "ناصر مکارم شیرازی (fa.makarem)",
        "license": "متن: Tanzil CC BY 3.0؛ ترجمه: شرایط Tanzil",
        "version": f"alquran.cloud; sha256 text={sha(a)} trans={sha(t)}; fallback-bismillah-strip={stripped}",
        "origin": "alquran.cloud",
    }
    return ar, fa, meta


def build_quran():
    log("Quran: downloading from Tanzil…")
    got = fetch_tanzil()
    if got is None:
        log("Quran: Tanzil unavailable, trying AlQuran.Cloud…")
        got = fetch_alquran_cloud()
    if got is None:
        raise SystemExit("FATAL: no authentic Quran source reachable. Build stopped (no placeholder content is used).")
    ar, fa, meta = got

    problems = []
    sections = []
    total = 0
    for si, count in enumerate(AYAH_COUNTS, start=1):
        passages = []
        for n in range(1, count + 1):
            a = ar.get((si, n))
            f = fa.get((si, n))
            if not a or not ARABIC_RE.search(a):
                problems.append(f"missing/invalid Arabic {si}:{n}")
                continue
            if not f:
                problems.append(f"missing translation {si}:{n}")
                f = ""
            passages.append({"n": n, "ar": a, "fa": f, "ref": f"{si}:{n}"})
        extra = [k for k in ar if k[0] == si and k[1] > count]
        if extra:
            problems.append(f"surah {si}: unexpected extra ayahs {extra[:3]}")
        total += len(passages)
        sections.append({"type": "surah", "number": si, "title": SURAH_NAMES_FA[si - 1], "passages": passages})
    extra_surahs = {k[0] for k in ar if k[0] < 1 or k[0] > 114}
    if extra_surahs:
        problems.append(f"unexpected surah numbers {sorted(extra_surahs)}")
    if ar.get((1, 1)) and norm(ar[(1, 1)]) != BISMILLAH_NORM:
        problems.append("1:1 is not the Bismillah — numbering/format mismatch")

    ok = not problems and total == 6236 and len(sections) == 114
    status = "complete" if ok else "partial"
    report["books"]["quran"] = {
        "status": status, "surahs": len(sections), "ayahs": total,
        "expectedSurahs": 114, "expectedAyahs": 6236, "source": meta["source"],
        "translator": meta["translator"], "problems": problems[:50], "problemCount": len(problems),
    }
    if not ok:
        for p in problems[:30]:
            log("  QC: " + p)
        report["errors"].append("Quran failed QC")
    return {"status": status, **meta, "sections": sections}, ok


EXPECTED = {
    # section type -> expected count per book (reference edition; adjust in docs/CONTENT-FORMAT.md if a different edition is used)
    "sahifa": {"dua": 54},
    "nahj": {"khutba": 241, "letter": 79, "hikma": 480},
}


def import_user_book(book_id):
    path = os.path.join(SOURCES, f"{book_id}.json")
    if not os.path.exists(path):
        report["books"][book_id] = {"status": "missing", "reason": "no licensed source file provided (content/sources/%s.json)" % book_id}
        log(f"{book_id}: MISSING (no source file)")
        return None
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    problems = []
    for key in ("source", "license", "version", "translator"):
        if not str(data.get(key, "")).strip():
            problems.append(f"metadata field '{key}' is empty — text without attribution is refused")
    sections = data.get("sections", [])
    if data.get("mode") == "pdf":
        return import_pdf_book(book_id, data, sections, problems)
    counts, empty_ar, empty_fa = {}, 0, 0
    for s in sections:
        counts[s["type"]] = counts.get(s["type"], 0) + 1
        for p in s["passages"]:
            if not p.get("ar", "").strip() or not ARABIC_RE.search(p.get("ar", "")):
                empty_ar += 1
            if not p.get("fa", "").strip():
                empty_fa += 1
    if empty_ar:
        problems.append(f"{empty_ar} passages without valid Arabic text")
    if empty_fa:
        problems.append(f"{empty_fa} passages without Persian translation")
    expected = EXPECTED.get(book_id)
    if expected:
        for t, n in expected.items():
            if counts.get(t, 0) != n:
                problems.append(f"expected {n} '{t}' sections, found {counts.get(t, 0)}")
    declared_complete = bool(data.get("complete", False)) if book_id == "mafatih" else True
    meta_missing = any("metadata" in p for p in problems)
    if meta_missing:
        report["books"][book_id] = {"status": "missing", "problems": problems, "reason": "refused: missing attribution metadata"}
        report["errors"].append(f"{book_id} refused")
        return None
    status = "complete" if (not problems and declared_complete) else "partial"
    report["books"][book_id] = {
        "status": status, "sectionCounts": counts, "passages": sum(len(s["passages"]) for s in sections),
        "problems": problems, "source": data["source"], "translator": data["translator"],
    }
    log(f"{book_id}: {status} ({counts})")
    return {
        "status": status, "source": data["source"], "translator": data["translator"],
        "license": data["license"], "version": data["version"], "sections": sections,
    }


def import_pdf_book(book_id, data, sections, problems):
    """Book shown as the original PDF pages (text layer used only for TOC/search). See scripts/extract_mafatih.py."""
    import shutil
    pdf_src = os.path.join(SOURCES, f"{book_id}.pdf")
    if not os.path.exists(pdf_src):
        problems.append("PDF file content/sources/%s.pdf is missing" % book_id)
    pages_expected = int(data.get("pdfPages", 0))
    covered = {p["n"] for s in sections for p in s["passages"] if p.get("ar")}
    nums = [p["n"] for s in sections for p in s["passages"]]
    if not sections:
        problems.append("no table-of-contents sections")
    if nums and (min(nums) < 1 or max(nums) > pages_expected):
        problems.append("page numbers outside the PDF range")
    if pages_expected and len(covered) < pages_expected - 10:
        problems.append(f"only {len(covered)} of {pages_expected} pages have searchable text")
    if any(not str(data.get(k, "")).strip() for k in ("source", "license", "version", "translator")):
        report["books"][book_id] = {"status": "missing", "problems": ["missing attribution metadata"]}
        report["errors"].append(f"{book_id} refused")
        return None
    if problems:
        status = "partial"
    else:
        status = data.get("status") or ("complete" if data.get("complete") else "partial")
    if not problems and os.path.exists(pdf_src):
        os.makedirs(ASSETS, exist_ok=True)
        shutil.copyfile(pdf_src, os.path.join(ASSETS, f"{book_id}.pdf"))
    report["books"][book_id] = {
        "status": status, "mode": "pdf", "edition": data.get("edition", ""), "pdfPages": pages_expected,
        "sectionCounts": {"toc_entries": len(sections)}, "passages": len(nums),
        "problems": problems, "source": data["source"], "translator": data["translator"],
    }
    log(f"{book_id}: {status} (pdf mode, {len(sections)} TOC entries, {len(covered)}/{pages_expected} pages with text)")
    return {
        "status": status, "mode": "pdf", "source": data["source"], "translator": data["translator"],
        "license": data["license"], "version": data["version"], "sections": sections,
    }


def write_json(name, obj):
    os.makedirs(ASSETS, exist_ok=True)
    with open(os.path.join(ASSETS, name), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))


def main():
    os.makedirs(REPORT, exist_ok=True)
    quran, ok = build_quran()
    write_json("quran.json", quran)
    written = ["quran"]
    for b in ("mafatih", "sahifa", "nahj"):
        data = import_user_book(b)
        if data:
            write_json(f"{b}.json", data)
            written.append(b)

    digest = hashlib.sha256()
    for b in written:
        with open(os.path.join(ASSETS, f"{b}.json"), "rb") as f:
            digest.update(f.read())
    manifest = {
        "contentVersion": datetime.now(timezone.utc).strftime("%Y%m%d") + "-" + digest.hexdigest()[:10],
        "books": written,
    }
    write_json("manifest.json", manifest)

    # ---- report
    lines = ["# گزارش کنترل کیفیت محتوا", "", f"زمان تولید: {report['generatedAt']}", f"نسخه محتوا: `{manifest['contentVersion']}`", ""]
    fa_status = {"complete": "کامل ✅", "selection": "منتخب ℹ️", "partial": "ناقص ⚠️", "missing": "موجود نیست ❌"}
    lines += ["| کتاب | وضعیت | جزئیات |", "|---|---|---|"]
    names = {"quran": "قرآن کریم", "mafatih": "مفاتیح الجنان", "sahifa": "صحیفه سجادیه", "nahj": "نهج‌البلاغه"}
    for b in ("quran", "mafatih", "sahifa", "nahj"):
        r = report["books"].get(b, {"status": "missing"})
        if b == "quran":
            det = f"{r.get('surahs')}/114 سوره، {r.get('ayahs')}/6236 آیه؛ منبع: {r.get('source')}؛ مترجم: {r.get('translator')}"
        elif r["status"] == "missing":
            det = r.get("reason", "فایل منبع معتبر ارائه نشده است")
        else:
            det = f"{r.get('sectionCounts')}؛ مشکلات: {len(r.get('problems', []))}"
        lines.append(f"| {names[b]} | {fa_status[r['status']]} | {det} |")
    lines += ["", "## مشکلات یافت‌شده", ""]
    any_problem = False
    for b, r in report["books"].items():
        for p in r.get("problems", []):
            lines.append(f"- {names.get(b, b)}: {p}")
            any_problem = True
    if not any_problem:
        lines.append("- موردی یافت نشد.")
    with open(os.path.join(REPORT, "QC-REPORT.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    with open(os.path.join(REPORT, "qc-report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print("\n".join(lines))

    if not ok:
        raise SystemExit("FATAL: Quran failed QC — build stopped.")


if __name__ == "__main__":
    sys.exit(main())
