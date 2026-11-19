#!/usr/bin/env python3
"""
Builds content/sources/mafatih.json from the owner-provided Mafatih al-Jinan PDF (text layer only; no OCR, no AI text).

Why the app shows PDF pages instead of re-typeset text:
  the PDF's text layer stores Arabic diacritics (harakat) detached from their letters, so re-typesetting it would
  put harakat on wrong letters (a distortion). The PDF pages themselves are shown exactly as printed. The text layer
  is used ONLY for (1) the table of contents and (2) search, with all diacritics/tatweel removed (letters unchanged).

usage: extract_mafatih.py input.pdf output.json
"""
import hashlib
import json
import re
import subprocess
import sys
import unicodedata

SRC, OUT = sys.argv[1], sys.argv[2]

BIDI = re.compile("[‪-‮‎‏⁦-⁩]")
MARKS = re.compile("[ً-ٰٟـ]")

UNITS = ["یکم", "دوم", "سوم", "چهارم", "پنجم", "ششم", "هفتم", "هشتم", "نهم"]
ORDINALS = {"اول", "دوم", "سوم", "چهارم", "پنجم", "ششم", "هفتم", "هشتم", "نهم", "دهم", "یازدهم", "دوازدهم", "سیزدهم",
            "چهاردهم", "پانزدهم", "شانزدهم", "هفدهم", "هیجدهم", "نوزدهم", "بیستم", "سیام"}
CANON = {}
for t in ORDINALS:
    CANON[t] = t
for tens in ("بیست", "سی"):
    for u in UNITS:
        CANON[tens + "و" + u] = f"{tens} و {u}"
CANON["بیستم"] = "بیستم"


def run(*args):
    return subprocess.run(args, capture_output=True, check=True).stdout.decode("utf-8")


def pages_text():
    raw = run("pdftotext", "-enc", "UTF-8", SRC, "-")
    raw = unicodedata.normalize("NFKC", raw)
    return raw.split("\f")[:-1] if raw.endswith("\f") else raw.split("\f")


def clean_lines(page):
    return [BIDI.sub("", l).strip() for l in page.split("\n")]


def is_header(l):
    return (l == "" or l == "www.takbook.com" or l.startswith("مرکز تحقیقات رایانه") or
            re.match(r"^صفحه\s*\d+\s*از\s*\d+$", l) is not None or l in ("مفاتیحالجنان", "مفاتیح الجنان"))


def page_search_text(page):
    lines = [l for l in clean_lines(page) if not is_header(l)]
    t = " ".join(lines)
    t = MARKS.sub("", t)
    return re.sub(r"\s+", " ", t).strip()


def fix_title(t):
    t = re.sub(r"\s+", " ", t).strip()
    joined = t.replace(" ", "")
    return CANON.get(joined, t)


def compact(t):
    return re.sub(r"[\s\u064B-\u065F\u0670\u0640]", "", t)


# Titles whose letters are split by spaces in the PDF and that do not appear verbatim in the page body.
# Only spaces are corrected (letters are identical to the PDF).
TITLE_OVERRIDES = {
    "دع ا ي س م ا ت": "دعاي سمات",
    "دع ا ي ع ش ر ا ت": "دعاي عشرات",
    "دع ا ي و د ا ع": "دعاي وداع",
    "ف ص ل پن ج م در تعیین اسامی نبی و ائمه علیهم السلام به روزهاي هفته و زیارت ایشان در هر روز":
        "فصل پنجم در تعیین اسامی نبی و ائمه علیهم السلام به روزهاي هفته و زیارت ایشان در هر روز",
    "ف ص ل س وم در فضیلت و اعمال ماه مبارك رمضان است": "فصل سوم در فضیلت و اعمال ماه مبارك رمضان است",
}


def repair_title(title, page_no, pages):
    """Titles in the printed TOC sometimes have letters split by spaces ("ه فت م"). If so, take the same heading
    from the body of its own page (exact text from the PDF), matching ignoring spaces. Otherwise keep as is."""
    ck = compact(title)
    for k, v in TITLE_OVERRIDES.items():
        if compact(k) == ck:
            return v
    if "]" in title and len(title) > 60:  # runaway heading that swallowed the first words of the body
        title = title.split("]")[0].strip()
    toks = title.split()
    if sum(1 for w in toks if len(w) == 1 and w != "و") == 0:
        return title
    key = compact(title)
    for pn in (page_no, page_no + 1):
        if 1 <= pn <= len(pages):
            for l in clean_lines(pages[pn - 1]):
                if l and not is_header(l) and compact(l) == key:
                    return re.sub(r"\s+", " ", MARKS.sub("", l)).strip()
    return title


def parse_toc(pages):
    last = 5
    for pn in range(5, 80):
        if "فهرست" in "\n".join(clean_lines(pages[pn - 1])[:12]):
            last = pn
    entries, buf = [], []
    for pn in range(5, last + 1):
        for l in clean_lines(pages[pn - 1]):
            if is_header(l) or l == "فهرست" or "ف ه رس ت" in l:
                continue
            if re.fullmatch(r"\d{1,3}", l):
                if buf:
                    entries.append((repair_title(fix_title(" ".join(buf)), int(l), pages), int(l)))
                buf = []
            else:
                buf.append(l)
    return entries, last


def main():
    pages = pages_text()
    total = len(pages)
    toc, toc_last = parse_toc(pages)
    # keep only entries with a valid, non-decreasing page number
    cleaned, prev = [], 1
    for title, p in toc:
        if 1 <= p <= total and p >= prev:
            cleaned.append((title, p))
            prev = p
    # readable names for bare ordinals / "اشاره" (e.g. "اعمال شب جمعه — اول")
    final, main_title = [], ""
    for title, p in cleaned:
        bare = title in CANON.values() or title == "اشاره"
        if not bare:
            main_title = title
            final.append((title, p))
        else:
            final.append((f"{main_title} — {title}" if main_title else title, p))
    first = [("ابتدای کتاب و فهرست", 1)]
    entries = first + [e for e in final if e[1] >= 1]

    sections = []
    for i, (title, start) in enumerate(entries):
        nxt = entries[i + 1][1] if i + 1 < len(entries) else total + 1
        end = max(start, nxt - 1) if nxt > start else None
        sections.append({"title": title, "start": start, "end": end})

    out_sections = []
    for i, s in enumerate(sections, start=1):
        passages = []
        if s["end"] is None:  # shares its start page with the next entry: pointer only (no duplicate text)
            passages.append({"n": s["start"], "ar": "", "fa": "", "ref": f"page:{s['start']}"})
        else:
            for pn in range(s["start"], s["end"] + 1):
                passages.append({"n": pn, "ar": page_search_text(pages[pn - 1]), "fa": "", "ref": f"page:{pn}"})
        out_sections.append({"type": "section", "number": i, "title": s["title"], "passages": passages})

    with open(SRC, "rb") as f:
        digest = hashlib.sha256(f.read()).hexdigest()[:16]
    covered = {p["n"] for s in out_sections for p in s["passages"] if p["ar"]}
    data = {
        "mode": "pdf",
        "status": "selection",
        "complete": False,
        "edition": "منتخب مفاتیح الجنان (برگزیده)",
        "pdfPages": total,
        "source": "منتخب مفاتیح الجنان، شیخ عباس قمی؛ چاپ برهان؛ نسخه دیجیتال مرکز تحقیقات رایانه‌ای قائمیه اصفهان (Ghaemiyeh.com)",
        "translator": "آداب و توضیحات فارسی از خود کتاب است؛ ترجمه جداگانه ندارد",
        "version": f"PDF {total} صفحه؛ sha256:{digest}",
        "license": "نسخه دیجیتال منتشرشده توسط قائمیه؛ مجوز بازنشر باید توسط مالک پروژه تأیید شود",
        "tocPages": [5, toc_last],
        "sections": out_sections,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    empty_pages = [p for p in range(1, total + 1) if p not in covered]
    print(f"pages={total} toc_entries={len(entries)} sections={len(out_sections)} pages_with_text={len(covered)} "
          f"pages_without_text={empty_pages[:10]}{'...' if len(empty_pages) > 10 else ''}")


main()
