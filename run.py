"""Development entry point.

    python run.py                # http://0.0.0.0:5000
    FLASK_ENV=production ...      # use a real WSGI server (see README)

For production, serve the WSGI callable with gunicorn:

    gunicorn "app:create_app()" --bind 0.0.0.0:8000 --workers 3
"""

from __future__ import annotations

import os

from app import create_app

app = create_app()

if __name__ == "__main__":
    port = int(os.getenv("PORT", "5000"))
    host = os.getenv("HOST", "0.0.0.0")
    app.run(host=host, port=port, debug=app.config.get("DEBUG", False))
