from __future__ import annotations

import re


def validate_request_id_component(value: str) -> str:
    """Keep artifact request IDs portable, bounded, and a single path component."""
    if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", value) is None:
        raise ValueError("request_id must be a safe single path component (1-128 characters)")
    return value
