#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from urllib import request, error


def main():
    endpoint = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api/triage-batch"
    dataset_path = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("data/ci_cd_logs_125.json")

    data = json.loads(dataset_path.read_text(encoding="utf-8"))
    payload = {"logs": data}

    req = request.Request(
        endpoint,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with request.urlopen(req, timeout=180) as response:
            body = response.read().decode("utf-8")
            print(f"status={response.status}")
            print(body[:2000])
    except error.HTTPError as exc:
        print(f"HTTP {exc.code}")
        print(exc.read().decode("utf-8", errors="replace")[:2000])
        raise SystemExit(1)


if __name__ == "__main__":
    main()
