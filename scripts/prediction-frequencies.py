"""Generate the compact word frequency asset from two pinned FrequencyWords files."""
import hashlib
import sys
import unicodedata
from pathlib import Path

INPUTS = {
    "en": (20000, "5351ff405b1126ef555791dd4d9798a48e3e9a501a9fc481a9da957752cfb458"),
    "bn": (6000, "2fd6c08f29335f26af983c26821537e5ed5e42dfdf43275a0c646a15d18d636e"),
}

def valid_word(language, word):
    if not 1 <= len(word) <= 32 or not word[0].isalpha():
        return False
    if language == "en":
        return all("a" <= char <= "z" or char == "'" for char in word) and not word.endswith("'")
    return all(
        ("\u0980" <= char <= "\u09ff" and unicodedata.category(char)[0] in "LM")
        or char in "\u200c\u200d" for char in word
    )

def rows(folder):
    for language, (limit, checksum) in INPUTS.items():
        data = (folder / f"{language}.txt").read_bytes()
        if hashlib.sha256(data).hexdigest() != checksum:
            raise ValueError(f"Unexpected {language} input checksum")
        counts = {}
        for line in data.decode("utf-8").splitlines():
            token, count = line.rsplit(" ", 1)
            token = unicodedata.normalize("NFC", token.lower().replace("’", "'"))
            if valid_word(language, token):
                counts[token] = counts.get(token, 0) + int(count)
        for word, count in sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:limit]:
            yield f"{language}\t{word}\t{count}\n"

if __name__ == "__main__":
    asset = Path(__file__).resolve().parents[1] / "shared" / "wordfreq.tsv"
    asset.write_text("".join(rows(Path(sys.argv[1]))), encoding="utf-8")
