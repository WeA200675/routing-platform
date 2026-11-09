# Trainingsverbindung (Android)

`TrainingEndpointDiscovery` lÃ¶st die aktuelle HTTPS-Adresse des Trainingscenters auf.

Der Discovery-Endpunkt muss JSON liefern:

```json
{
  "status": "online",
  "dashboard_url": "https://training.example/",
  "api_url": "https://training.example/api"
}
```

Die Android-App versucht in dieser Reihenfolge:

1. Discovery-Endpunkt
2. zuletzt erfolgreiche Adresse
3. konfigurierte HTTPS-Fallback-Adresse

Bei Nichterreichbarkeit bleibt die lokale FIFO-Trainingswarteschlange aktiv. Es werden keine SSH-SchlÃ¼ssel und keine Cloud-Anmeldedaten in der App gespeichert.
