#!/bin/sh
# Writes /iroh.json, which tells the app (src/app/core/iroh.ts) which iroh-gateway to reach the backend through.
#
#   IROH_ENDPOINT_ID         the gateway's endpoint id. If unset, it is read from IROH_GATEWAY_STATUS_URL.
#   IROH_GATEWAY_STATUS_URL  the gateway's admin /status (e.g. http://gateway:9090/status), when the gateway runs
#                            next to this container, as in docker-compose.yml. Also supplies the relay hint.
#   IROH_GATEWAY_RELAY_URL   the relay the gateway is on (optional hint; speeds up the first connection).
#   IROH_RELAY_URL           relay(s) the browser should use instead of n0's public relays (optional).
#   IROH_RELAY_TOKEN         token for that relay (optional; it ends up in a public file, see the README).
set -eu

out=/usr/share/nginx/html/iroh.json
id=${IROH_ENDPOINT_ID:-}
relay=${IROH_GATEWAY_RELAY_URL:-}

if [ -z "$id" ] && [ -n "${IROH_GATEWAY_STATUS_URL:-}" ]; then
  echo "iroh: reading the gateway endpoint id from $IROH_GATEWAY_STATUS_URL"
  i=0
  while [ $i -lt 60 ]; do
    status=$(wget -qO- "$IROH_GATEWAY_STATUS_URL" 2>/dev/null || true)
    # Wait until the gateway is online on its relay, so the relay hint is known too.
    case $status in *'"online":true'*) break ;; esac
    i=$((i + 1))
    sleep 1
  done
  id=$(printf '%s' "$status" | sed -n 's/.*"endpoint_id":"\([0-9a-f]*\)".*/\1/p')
  [ -n "$relay" ] || relay=$(printf '%s' "$status" | sed -n 's/.*"home_relays":\[{[^]]*"url":"\([^"]*\)".*/\1/p' | sed 's#\./$##; s#/$##')
fi

if [ -z "$id" ]; then
  echo "iroh: no gateway endpoint id (set IROH_ENDPOINT_ID or IROH_GATEWAY_STATUS_URL); the app will not reach the backend" >&2
  rm -f "$out"
  exit 0
fi

json_str() { printf '"%s"' "$(printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g')"; }
{
  printf '{"endpointId":%s' "$(json_str "$id")"
  [ -z "$relay" ] || printf ',"relayUrl":%s' "$(json_str "$relay")"
  [ -z "${IROH_RELAY_URL:-}" ] || printf ',"clientRelayUrl":%s' "$(json_str "$IROH_RELAY_URL")"
  [ -z "${IROH_RELAY_TOKEN:-}" ] || printf ',"relayToken":%s' "$(json_str "$IROH_RELAY_TOKEN")"
  printf '}\n'
} > "$out"
echo "iroh: backend gateway $id${relay:+ on $relay}"
