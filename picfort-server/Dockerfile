# Picfort server. Install with docker/docker-compose.yml, see README.
FROM python:3.13-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PORT=5000 \
    FRAME_STATE_PATH=/data/slideshow.json \
    FRAME_PREVIEW_PATH=/data/last_frame.png \
    WALL_DB_PATH=/data/wall.db \
    WALL_PHOTOS_PATH=/data/wall_photos \
    SHOPPING_DB_PATH=/data/shopping.db \
    FAMILY_DB_PATH=/data/family.db \
    PROFILE_PHOTOS_PATH=/data/profile_photos \
    EVENTS_DB_PATH=/data/events.db

WORKDIR /app
COPY requirements.txt .
# pillow-heif: HEIC originals from iPhones (optional outside Docker).
RUN pip install --no-cache-dir -r requirements.txt pillow-heif
COPY *.py ./

VOLUME /data
EXPOSE 5000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
    CMD python -c "import os, urllib.request as u; u.urlopen(u.Request('http://127.0.0.1:%s/status' % os.environ.get('PORT', '5000'), headers={'X-Picfort-Key': os.environ.get('PICFORT_API_KEY', '')}), timeout=4)"
CMD ["python", "app.py"]
