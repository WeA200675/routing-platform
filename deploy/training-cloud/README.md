# Training Center Cloud Deployment

Recommended first target: Oracle Cloud Always Free compute with Docker and the
open-source services in docker-compose.yml.

## 1. Create the VM

Create an Ubuntu VM in the Oracle home region using an Always Free shape.
Allow TCP 80 and 443 in the security list. Keep SSH restricted to your own
public IP.

## 2. Install Docker and clone

    sudo apt update
    sudo apt install -y git docker.io docker-compose-plugin
    sudo usermod -aG docker "$USER"
    git clone https://github.com/WeA200675/routing-platform.git
    cd routing-platform

Log out/in once after changing the Docker group.

## 3. Set secrets before starting

Replace every change-me-before-deploy value in
deploy/training-cloud/docker-compose.yml with long random values. Set the
domain in the environment:

    export TRAINING_DOMAIN=training.example.org

Point the domain's A record to the VM public IP. Caddy will request and renew
the TLS certificate automatically.

## 4. Start

    docker compose -f deploy/training-cloud/docker-compose.yml up -d --build

Open https://training.example.org. The dashboard and /api/training/* share one
origin, so the phone does not need a separate API URL.

## 5. Important current boundary

The stack includes persistent PostgreSQL and MinIO volumes, but the first API
bridge still keeps its operational state in memory. The next backend slice must
replace TrainingState with the PostgreSQL/FIFO ledger and store evidence payloads
in MinIO. Until then, never treat the demo API state as durable training truth.

## 6. Mobile connection

Configure the Android training endpoint to:

    https://training.example.org

The app keeps events locally when the VM or internet is unavailable and retries
later. Navigation remains usable without the cloud.
