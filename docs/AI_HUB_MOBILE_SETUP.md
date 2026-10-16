# Mobile KI-Hub und Training einrichten

Die Android-App kann sich unter **Einstellungen → Soziale KI → KI-Hub** mit diesem Hub verbinden. Dort lassen sich Gespräche mit dem konfigurierten Modell senden sowie echte LoRA-Läufe auf dem Trainingsrechner starten und wieder aufnehmen. Der Hub antwortet nur für Unterhaltung; Navigation, Spuren, Abbiegungen und Verkehrslage bleiben bei den geprüften Routing- und Datenquellen.


> Der KI-Hub ist optional und extern: Gespräche oder LoRA-Läufe nutzen ihn nur über die ausdrücklich getrennten Hub-Aktionen. Die App verwendet keinen automatischen Gesprächs-Fallback zum Hub. Für lokale KI-Inferenz und lokales Präferenzlernen auf dem Pixel siehe [Lokale KI und Pixel-10-Pro-Test](ON_DEVICE_AI_PIXEL10.md).
## 1. Hub sicher erreichbar machen

Den Hub auf einem dauerhaften Rechner/Server starten und per Reverse Proxy mit einem gültigen TLS-Zertifikat über HTTPS veröffentlichen. Für externen Zugriff braucht der Hub eine mindestens 32 Zeichen lange Zufallskennung und einen Wildcard-Bind:

```sh
export HUB_API_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
export HUB_BIND_HOST=0.0.0.0
export HUB_PORT=8090
python3 -m backend.traffic_intelligence_hub
```

Der Proxy muss HTTPS terminieren und Anfragen an den Hub weiterleiten. Den Token nicht in Git, Screenshots oder Supportmeldungen ablegen. In der App wird er verschlüsselt mit Android Keystore gespeichert. In der App ist die externe Adresse als `https://…` einzutragen; unverschlüsselte externe HTTP-Verbindungen werden abgelehnt.

## 2. Gesprächsmodell auf dem Hub konfigurieren

Der Hub erwartet eine OpenAI-kompatible Chat-Completions-Schnittstelle. Zugangsdaten bleiben ausschließlich als Server-Umgebungsvariablen:

```sh
export AI_API_BASE_URL="https://<anbieter>/v1"
export AI_MODEL_ID="<freigeschaltete-modell-id>"
export AI_API_KEY="<serverseitiger-schlüssel>"
export AI_TIMEOUT_SECONDS=25
```

Für einen lokalen Ollama- oder anderen OpenAI-kompatiblen Dienst auf demselben Trainingsrechner kann die Basisadresse beispielsweise `http://127.0.0.1:11434/v1` sein; Loopback-HTTP ist die einzige erlaubte HTTP-Ausnahme. Ohne diese Einstellungen zeigt die App den Hub als erreichbar, aber die KI als nicht konfiguriert.

Die Chat-Anfrage ist ein einzelner Gesprächsbeitrag. Prompts und Antworten werden vom Hub nicht gespeichert; nur Modell-ID, Aufgabentyp, Ergebnis und Laufzeit gehen in die Laufzeitstatistik ein. Die KI wird nicht für Navigationsentscheidungen verwendet.

## 3. Echte Trainingsläufe aktivieren

LoRA läuft auf dem Hub-Rechner, nicht auf dem Handy. Der Rechner braucht eine getestete CUDA-GPU, PyTorch und die exakt installierte LLaMA-Factory-Version. Mindestens 20 geprüfte Beispiele sind für das ausgewählte Thema erforderlich. Dann auf dem Hub setzen:

```sh
export HUB_LORA_ENABLED=true
export LORA_MODEL_ID="<Modell-Repository>"
export MODEL_REVISION="<unveränderlicher 40- bis 64-stelliger Modell-Commit>"
export TRAINER_EXPECTED_VERSION="<installierte LLaMA-Factory-Version>"
```

Beispiele und freigegebene Wissensquellen vorher im Trainings-Dashboard kuratieren. In der Android-App das gewünschte Thema auswählen und **LoRA-Training starten** antippen. Die App zeigt den persistenten Laufstatus erneut an, wenn die Verbindung kurz unterbrochen wird. Ein Vorabfehler bleibt als fehlgeschlagener Lauf sichtbar; dabei werden keine Modellgewichte als geändert ausgewiesen.

## Verhalten bei Verbindungsabbruch

Solange die KI-Einstellungen geöffnet sind, prüft die App die Verbindung regelmäßig und versucht nach einem Ausfall erneut zu verbinden. Android kann Netzwerkzugriff im Hintergrund beenden; nach Rückkehr zu den Einstellungen verbindet sich die App erneut. Der Hub speichert Trainingsläufe, damit ein laufender Job beim erneuten Öffnen abgefragt werden kann. Für dauerhaft erreichbare Gespräche muss der Hub im Internet beziehungsweise im eigenen VPN verfügbar sein.
