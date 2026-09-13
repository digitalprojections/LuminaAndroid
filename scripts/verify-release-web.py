"""Verify the deployed GenStudio bundle and Sound Effects API without creating tasks."""
import hashlib
import json
import re
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = 'https://genstudio.web.app'
local_html = (ROOT / 'OneImage/dist/index.html').read_text(encoding='utf-8')
live_html = urllib.request.urlopen(BASE + '/', timeout=30).read().decode()
pattern = r'<script type="module" crossorigin src="([^"]+)"'
local_asset = re.search(pattern, local_html).group(1)
live_asset = re.search(pattern, live_html).group(1)
assert local_asset == live_asset, (local_asset, live_asset)
local_data = (ROOT / 'OneImage/dist' / local_asset.lstrip('/')).read_bytes()
live_data = urllib.request.urlopen(BASE + live_asset, timeout=30).read()
assert hashlib.sha256(local_data).digest() == hashlib.sha256(live_data).digest(), 'Bundle differs'
assert b'sound-effects' in live_data, 'Sound Effects missing'
request = urllib.request.Request(BASE + '/api/sound-effects/generate', data=b'{}', headers={'Content-Type':'application/json'}, method='POST')
try:
    urllib.request.urlopen(request, timeout=60)
    raise AssertionError('Unauthenticated generation was accepted')
except urllib.error.HTTPError as error:
    body = json.loads(error.read())
    assert error.code == 401, (error.code, body)
print(json.dumps({'site': BASE, 'asset': live_asset, 'sha256': hashlib.sha256(live_data).hexdigest(), 'soundEffectsApi': '401 authentication required'}, indent=2))
