from __future__ import annotations

import uuid


def build_request_id() -> str:
    return f"seg-{uuid.uuid4()}"

