# URL Shortener — Scalable, Cached, Rate-Limited

A Spring Boot REST service that shortens URLs, built to demonstrate distributed-systems
decisions rather than just CRUD: cache-aside reads, an atomic write path that needs no
distributed ID generator, and a rate limiter — deployed as a container on Cloud Run.

## Why this design

| Concern | Decision | Reasoning |
|---|---|---|
| ID generation | Postgres auto-increment → base62 encode | No coordination needed across replicas; a Redis `INCR` or Snowflake ID would work too, but the DB's own counter is simpler and already strongly consistent. |
| Read path | Cache-aside with Redis, 10 min TTL | Shortener traffic is power-law distributed — a small number of links get most of the clicks. Caching the hot set keeps Postgres load flat as traffic grows. |
| Redirect status | 302, not 301 | A 301 gets cached by browsers indefinitely, which would make click tracking and TTL expiry silently stop working. |
| Click counting | Atomic `UPDATE ... SET count = count + 1` | Avoids a read-modify-write race when many requests hit the same code concurrently — the DB does the increment, not the app. |
| Rate limiting | Token bucket (Bucket4j), per API key or IP | Protects the backend and the Gemini/downstream calls (if extended) from being overwhelmed by one client. |

## Known limitation (intentional, documented)

The rate limiter in `RateLimitFilter` is **in-memory per instance**. That's correct for one
container but wrong once you run N replicas behind a load balancer — each instance enforces
the limit independently, so the effective global limit becomes N× what's configured. The fix
is a Redis-backed bucket (`bucket4j-redis`) so all instances share one counter. Left as-is
here to keep the demo runnable with zero extra setup, but this is the first thing to fix
before calling it production-ready — and a good thing to bring up proactively in an interview.

## Run locally

```bash
docker-compose up --build
```

Then:

```bash
# Shorten a URL
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://example.com/some/very/long/path"}'

# -> {"shortCode":"1","shortUrl":"http://localhost:8080/1","longUrl":"...","expiresAt":null}

# Follow it
curl -L http://localhost:8080/1
```

## Run tests

```bash
mvn test
```

## Deploy to Google Cloud Run

```bash
# 1. Build and push the container
gcloud builds submit --tag gcr.io/YOUR_PROJECT_ID/url-shortener

# 2. Provision managed Postgres (Cloud SQL) and Memorystore (Redis) separately,
#    or point at any reachable instance for a quick demo.

# 3. Deploy
gcloud run deploy url-shortener \
  --image gcr.io/YOUR_PROJECT_ID/url-shortener \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars DB_URL=jdbc:postgresql://<CLOUD_SQL_IP>:5432/urlshortener,DB_USER=postgres,DB_PASSWORD=<pw>,REDIS_HOST=<MEMORYSTORE_IP>,REDIS_PORT=6379,BASE_URL=https://<your-cloud-run-url>
```

Cloud Run reads `PORT` automatically; `application.properties` is already wired to it.
`/actuator/health` is exposed for Cloud Run's health checks.

## Load testing

Once deployed (or running locally), get a real throughput/latency number for your resume
bullet with a quick k6 script:

```javascript
// loadtest.js
import http from 'k6/http';
export const options = { vus: 50, duration: '30s' };
export default function () {
  http.get('http://localhost:8080/1');
}
```

```bash
k6 run loadtest.js
```

Report the p95 latency and requests/sec from the summary — e.g. "sustained ~450 req/sec
at 50 concurrent users with p95 latency under 40ms, cache hit ratio ~92%." That's a concrete,
defensible number you can put on a resume and explain in an interview, versus an unverified claim.
