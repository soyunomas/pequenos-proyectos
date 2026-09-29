#!/bin/sh
set -eu

GO=${GO:-go}
COUNT=${COUNT:-3}

printf '%s\n' 'Fast-path candidate benchmark (portable UDP Write vs Linux sendmmsg batch via x/net/ipv4).'
printf '%s\n' 'This benchmark is evidence, not an automatic production switch.'
exec "$GO" test -run '^$' -bench 'BenchmarkUDPWrite(Portable|Batch16)$' -benchmem -count="$COUNT" ./internal/fastpath
