# Production Deployment Guide

This guide deploys the calendar app to its own domain/subdomain with HTTPS.

Recommended URL format:
- `calendar.yourdomain.com` (best and simplest)

## What This Setup Uses

- Docker Compose for app + database + reverse proxy
- Caddy for automatic TLS (HTTPS certificates)
- PostgreSQL for persistence

Deployment files are in `deployment/`:
- `deployment/compose.prod.yaml`
- `deployment/Caddyfile`
- `deployment/.env.prod.example`

## 1) Point DNS to your server

Create an `A` record:
- Host/name: `calendar` (or your preferred subdomain)
- Value: your VPS public IP

Example:
- `calendar.yourdomain.com -> 203.0.113.25`

## 2) Copy project to your Linux VPS

From your local machine:

```bash
git clone <your-repo-url>
cd Volt-Scheduler-Project
```

## 3) Create production env file

```bash
cd deployment
cp .env.prod.example .env.prod
```

Edit `deployment/.env.prod` and set real values:

```env
APP_DOMAIN=calendar.yourdomain.com
POSTGRES_DB=calendar_app
POSTGRES_USER=calendar_user
POSTGRES_PASSWORD=<strong-random-password>
```

## 4) Start the stack

From the `deployment/` directory:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
```

## 5) Verify deployment

```bash
docker compose --env-file .env.prod -f compose.prod.yaml ps
docker compose --env-file .env.prod -f compose.prod.yaml logs -f caddy
docker compose --env-file .env.prod -f compose.prod.yaml logs -f app
```

Open:
- `https://calendar.yourdomain.com`

## 6) Update workflow

After pushing new code on the server:

```bash
git pull
docker compose --env-file deployment/.env.prod -f deployment/compose.prod.yaml up -d --build
```

## Optional: Link from portfolio page

Add a button/link on your portfolio site:

```html
<a href="https://calendar.yourdomain.com" target="_blank" rel="noopener noreferrer">
  Open Live Calendar App
</a>
```

## Notes

- This setup does not expose PostgreSQL publicly.
- Caddy auto-renews certificates.
- Ensure ports `80` and `443` are open in your VPS firewall.

## Azure Option

If you want Azure hosting, use:
- `deployment/azure/AZURE_DEPLOYMENT.md`
- `deployment/azure/deploy-appservice.ps1`
