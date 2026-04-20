"""
Poll the SimplifyJobs/New-Grad-Positions GitHub repo for new job listings.

The README.md contains a markdown table with columns roughly:
    Company | Role | Location | Application/Link | Date Added

We fetch via the GitHub Contents API (returns base64-encoded content), decode,
parse the table rows, deduplicate, and return only new listings.
"""

import base64
import hashlib
import logging
import re
from typing import Optional

import httpx

from config import settings

logger = logging.getLogger(__name__)

GITHUB_API_BASE = "https://api.github.com"
README_PATH = "README.md"


def _headers() -> dict:
    h = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if settings.github_token:
        h["Authorization"] = f"Bearer {settings.github_token}"
    return h


def _fetch_readme_content() -> tuple[str, Optional[str]]:
    """
    Return (decoded_readme_text, commit_sha_or_None).

    Uses the Contents API so we get the commit SHA of the blob's tree for free.
    Falls back to the raw endpoint if the Contents API response is unexpectedly
    shaped.
    """
    url = f"{GITHUB_API_BASE}/repos/{settings.github_repo}/contents/{README_PATH}"
    with httpx.Client(timeout=30) as client:
        resp = client.get(url, headers=_headers())
        resp.raise_for_status()
        data = resp.json()

    sha: Optional[str] = data.get("sha")

    encoding = data.get("encoding", "")
    if encoding == "base64":
        content = base64.b64decode(data["content"]).decode("utf-8", errors="replace")
        return content, sha

    # Fallback: fetch the download_url directly
    download_url = data.get("download_url")
    if not download_url:
        raise ValueError("GitHub Contents API returned no decodable content")

    with httpx.Client(timeout=30) as client:
        resp = client.get(download_url, headers=_headers())
        resp.raise_for_status()
        return resp.text, sha


def _extract_url(cell: str) -> Optional[str]:
    """Pull the first href out of a markdown link, e.g. [text](url)."""
    match = re.search(r"\[.*?\]\((https?://[^)]+)\)", cell)
    if match:
        return match.group(1)
    # bare URL
    bare = re.search(r"https?://\S+", cell)
    if bare:
        return bare.group(0)
    return None


def _clean_cell(cell: str) -> str:
    """Strip markdown formatting and whitespace from a table cell."""
    # Remove markdown links → keep the label text
    cell = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", cell)
    # Remove HTML tags
    cell = re.sub(r"<[^>]+>", "", cell)
    # Collapse whitespace
    cell = " ".join(cell.split())
    return cell.strip()


def _is_header_or_separator(cells: list[str]) -> bool:
    """Return True for divider rows like |---|---|...|."""
    return all(re.fullmatch(r"[-: ]+", c) for c in cells if c)


def _parse_table_rows(readme: str) -> list[dict]:
    """
    Parse all pipe-delimited table rows from the README.

    We do NOT assume fixed column positions — instead we detect the header row
    and map column names to indices, so format drift only affects individual
    rows rather than the whole parse.
    """
    results: list[dict] = []

    lines = readme.splitlines()

    # Find the header row: a pipe-delimited line containing "company" (case-insensitive)
    header_idx: Optional[int] = None
    col_map: dict[str, int] = {}

    for i, line in enumerate(lines):
        if "|" not in line:
            continue
        parts = [p.strip() for p in line.split("|")]
        # Drop empty leading/trailing segments from lines like | A | B |
        parts = [p for p in parts if p != ""]
        lower_parts = [p.lower() for p in parts]
        if any("company" in lp for lp in lower_parts):
            header_idx = i
            for j, lp in enumerate(lower_parts):
                if "company" in lp:
                    col_map["company"] = j
                elif "role" in lp or "position" in lp or "title" in lp:
                    col_map["role"] = j
                elif "location" in lp:
                    col_map["location"] = j
                elif "link" in lp or "apply" in lp or "application" in lp or "url" in lp:
                    col_map["url"] = j
                elif "date" in lp or "added" in lp or "posted" in lp:
                    col_map["date"] = j
            break

    if header_idx is None:
        logger.warning("Could not find job table header in README")
        return results

    # Require at least company + role to proceed
    if "company" not in col_map or "role" not in col_map:
        logger.warning("Header found but missing required columns: %s", col_map)
        return results

    # Parse data rows (skip the separator line immediately after the header)
    for line in lines[header_idx + 1 :]:
        if "|" not in line:
            continue
        parts = [p.strip() for p in line.split("|")]
        parts = [p for p in parts if p != ""]

        if not parts:
            continue
        if _is_header_or_separator(parts):
            continue

        # Skip rows that are too short to contain required columns
        max_needed = max(col_map.values())
        if len(parts) <= max_needed:
            continue

        try:
            raw_company = parts[col_map["company"]]
            raw_role = parts[col_map["role"]]

            company = _clean_cell(raw_company)
            role = _clean_cell(raw_role)

            if not company or not role:
                continue

            # "↳" rows are continuation/sub-role entries — keep them but mark parent
            is_continuation = company.startswith("↳")
            if is_continuation:
                company = company.lstrip("↳").strip()

            location: Optional[str] = None
            if "location" in col_map:
                location = _clean_cell(parts[col_map["location"]]) or None

            url: Optional[str] = None
            if "url" in col_map:
                url = _extract_url(parts[col_map["url"]])
            # Also try to extract URL from the company or role cell as fallback
            if not url:
                url = _extract_url(raw_company) or _extract_url(raw_role)

            results.append(
                {
                    "company": company,
                    "role": role,
                    "location": location,
                    "url": url,
                    "raw_row": line.strip(),
                }
            )
        except Exception as exc:
            logger.debug("Skipping unparseable row %r: %s", line[:80], exc)
            continue

    logger.info("Parsed %d job rows from README", len(results))
    return results


def row_hash(raw_row: str) -> str:
    return hashlib.sha256(raw_row.encode()).hexdigest()


def fetch_readme_listings() -> tuple[list[dict], Optional[str]]:
    """
    Fetch the README, parse the table, and return (rows, commit_sha).

    Each row dict has keys: company, role, location, url, raw_row.
    commit_sha may be None if the API did not return one.
    """
    readme, sha = _fetch_readme_content()
    rows = _parse_table_rows(readme)
    return rows, sha
