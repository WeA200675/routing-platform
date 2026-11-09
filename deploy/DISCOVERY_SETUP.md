# Discovery-Endpunkt

Vor dem Start der API auf der VM setzen:

```bash
export TRAINING_DASHBOARD_URL='https://DEINE-TUNNEL-ADRESSE'
export TRAINING_API_URL='https://DEINE-TUNNEL-ADRESSE/api'
export TRAINING_VERSION='1.0.0'
python3 backend/training_api.py
```

Der Endpunkt ist anschlieÃend unter `/connection.json` erreichbar. Nginx muss diesen Pfad an die API weiterleiten:

```nginx
location = /connection.json {
    proxy_pass http://127.0.0.1:8787/connection.json;
    proxy_set_header Host $host;
}
```
