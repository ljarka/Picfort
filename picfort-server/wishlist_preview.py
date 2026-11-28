"""OpenGraph / HTML metadata extractor for wishlist link previews (T-041)."""

from decimal import Decimal, InvalidOperation
from html.parser import HTMLParser
import ipaddress
import json
import re
import socket
import urllib.error
import urllib.parse
import urllib.request

from store import StoreError

MAX_URL_LENGTH = 2048
MAX_PREVIEW_BYTES = 1024 * 1024
PREVIEW_TIMEOUT_SECONDS = 4
MAX_REDIRECTS = 3
MAX_PRICE_MINOR = 100_000_000
MOBILE_USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)


def validate_http_url(url) -> str:
    if not isinstance(url, str) or not url.strip():
        raise StoreError("url must not be empty")
    url = url.strip()
    if len(url) > MAX_URL_LENGTH:
        raise StoreError(f"url must be at most {MAX_URL_LENGTH} characters")
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.netloc or not parsed.hostname:
        raise StoreError("url must be a valid http:// or https:// URL")
    return url


def _is_public_ip(raw_ip: str) -> bool:
    ip = ipaddress.ip_address(raw_ip.split("%", 1)[0])
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped is not None:
        ip = ip.ipv4_mapped
    return ip.is_global


def validate_public_url(url: str, resolve=socket.getaddrinfo) -> str:
    """Reject non-public destinations (loopback, private LAN, link-local, Docker hosts) to prevent SSRF (T-108)."""
    url = validate_http_url(url)
    parsed = urllib.parse.urlparse(url)
    host = parsed.hostname or ""
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    try:
        infos = resolve(host, port, type=socket.SOCK_STREAM)
    except OSError as e:
        raise StoreError(f"Could not resolve host: {e}", status=400)
    if not infos:
        raise StoreError("Could not resolve host", status=400)
    for info in infos:
        addr = info[4][0]
        try:
            if not _is_public_ip(addr):
                raise StoreError("URL points to a non-public address", status=400)
        except ValueError:
            raise StoreError("URL points to an invalid address", status=400)
    return url


def _parse_price_minor(raw: str | None) -> int | None:
    if not raw:
        return None
    cleaned = re.sub(r"[^\d.,]", "", raw.strip())
    if not cleaned:
        return None
    if "," in cleaned and "." in cleaned:
        # Assume comma is thousands separator if it appears before dot, else decimal.
        if cleaned.rfind(",") > cleaned.rfind("."):
            cleaned = cleaned.replace(".", "").replace(",", ".")
        else:
            cleaned = cleaned.replace(",", "")
    elif "," in cleaned:
        cleaned = cleaned.replace(",", ".")
    try:
        amount = Decimal(cleaned)
    except InvalidOperation:
        return None
    minor = int((amount * 100).to_integral_value())
    if not 1 <= minor <= MAX_PRICE_MINOR:
        return None
    return minor


class _OgParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.meta: dict[str, str] = {}
        self._in_title = False
        self._title_parts: list[str] = []
        self._in_ld_json = False
        self._ld_parts: list[str] = []
        self.json_ld_blocks: list[str] = []

    def handle_starttag(self, tag, attrs):
        tag = tag.lower()
        if tag == "title":
            self._in_title = True
            return
        if tag == "script":
            attr_map = {k.lower(): v for k, v in attrs if k and v is not None}
            if "ld+json" in (attr_map.get("type") or "").lower():
                self._in_ld_json = True
                self._ld_parts = []
            return
        if tag != "meta":
            return
        attr_map = {k.lower(): v for k, v in attrs if k and v is not None}
        key = (attr_map.get("property") or attr_map.get("name") or "").strip().lower()
        content = attr_map.get("content", "").strip()
        if key and content and key not in self.meta:
            self.meta[key] = content

    def handle_endtag(self, tag):
        tag = tag.lower()
        if tag == "title":
            self._in_title = False
        elif tag == "script" and self._in_ld_json:
            self._in_ld_json = False
            raw = "".join(self._ld_parts).strip()
            if raw:
                self.json_ld_blocks.append(raw)

    def handle_data(self, data):
        if self._in_title:
            self._title_parts.append(data)
        elif self._in_ld_json:
            self._ld_parts.append(data)

    @property
    def title_tag(self) -> str | None:
        text = " ".join("".join(self._title_parts).split()).strip()
        return text or None


def _extract_product_json_ld(blocks: list[str]) -> dict:
    """Extract name, image, price from schema.org/Product JSON-LD blocks (common on Allegro/shops)."""
    def walk(node):
        if isinstance(node, list):
            for item in node:
                res = walk(item)
                if res:
                    return res
            return None
        if not isinstance(node, dict):
            return None
        node_type = node.get("@type")
        is_product = node_type == "Product" or (isinstance(node_type, list) and "Product" in node_type)
        if is_product:
            return node
        if "@graph" in node:
            return walk(node["@graph"])
        return None

    for raw in blocks:
        try:
            parsed = json.loads(raw)
        except (ValueError, TypeError):
            continue
        product = walk(parsed)
        if product:
            image = product.get("image")
            if isinstance(image, list):
                image = image[0] if image else None
            elif isinstance(image, dict):
                image = image.get("url")
            offers = product.get("offers")
            if isinstance(offers, list):
                offers = offers[0] if offers else None
            price = None
            if isinstance(offers, dict):
                price = offers.get("price") or offers.get("lowPrice")
            return {
                "title": product.get("name") if isinstance(product.get("name"), str) else None,
                "image": image if isinstance(image, str) else None,
                "price": str(price) if price is not None else None,
            }
    return {}


def parse_link_preview(html_text: str, base_url: str) -> dict:
    parser = _OgParser()
    parser.feed(html_text)
    parser.close()

    ld = _extract_product_json_ld(parser.json_ld_blocks)

    title = parser.meta.get("og:title") or ld.get("title") or parser.title_tag
    if title:
        title = " ".join(title.split())[:200]

    raw_image = parser.meta.get("og:image") or parser.meta.get("og:image:url") or ld.get("image")
    image_url = urllib.parse.urljoin(base_url, raw_image) if raw_image else None
    if image_url:
        parsed_img = urllib.parse.urlparse(image_url)
        if parsed_img.scheme not in ("http", "https") or not parsed_img.netloc:
            image_url = None

    raw_price = (
        parser.meta.get("og:price:amount")
        or parser.meta.get("product:price:amount")
        or ld.get("price")
        or parser.meta.get("twitter:data1")
    )
    price_minor = _parse_price_minor(raw_price)

    host = (urllib.parse.urlparse(base_url).hostname or "").removeprefix("www.")
    shop_name = parser.meta.get("og:site_name") or host or None

    return {
        "title": title,
        "image_url": image_url,
        "price_minor": price_minor,
        "shop_name": shop_name,
    }


class _SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Re-validates every redirect hop against SSRF rules and caps redirect depth (T-108)."""

    def __init__(self, resolve=socket.getaddrinfo, max_redirects: int = MAX_REDIRECTS):
        self._resolve = resolve
        self._max_redirects = max_redirects
        self._redirects = 0

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        self._redirects += 1
        if self._redirects > self._max_redirects:
            raise StoreError(f"Too many redirects (max {self._max_redirects})", status=502)
        target = urllib.parse.urljoin(req.full_url, newurl)
        validate_public_url(target, resolve=self._resolve)
        return super().redirect_request(req, fp, code, msg, headers, target)


def fetch_link_preview(url: str, resolve=socket.getaddrinfo, opener=None) -> dict:
    url = validate_public_url(url, resolve=resolve)
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": MOBILE_USER_AGENT,
            "Accept": "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7",
        },
    )
    if opener is None:
        opener = urllib.request.build_opener(_SafeRedirectHandler(resolve=resolve))
    try:
        with opener.open(req, timeout=PREVIEW_TIMEOUT_SECONDS) as resp:
            raw = resp.read(MAX_PREVIEW_BYTES)
            charset = resp.headers.get_content_charset() or "utf-8"
            final_url = resp.geturl() or url
    except StoreError:
        raise
    except (urllib.error.URLError, TimeoutError, ValueError) as e:
        raise StoreError(f"Could not fetch link preview: {e}", status=502)

    html_text = raw.decode(charset, errors="replace")
    return parse_link_preview(html_text, final_url)
