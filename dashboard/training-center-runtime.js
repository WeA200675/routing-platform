(function (global) {
  "use strict";

  const DEFAULT_TIMEOUT_MS = 5000;
  const ENDPOINTS = {
    overview: "/api/training/overview",
    knowledge: "/api/training/knowledge",
    experiments: "/api/training/experiments",
    operations: "/api/training/operations",
    history: "/api/training/history",
    dialogue: "/api/training/dialogue",
    control: "/api/training/control",
    trainingJob: "/api/training/training-job"
  };

  function timeoutSignal(timeoutMs) {
    if (typeof AbortController === "undefined") return undefined;
    const controller = new AbortController();
    setTimeout(function () { controller.abort(); }, timeoutMs);
    return controller.signal;
  }

  async function request(path, options) {
    const response = await fetch(path, Object.assign({
      headers: { Accept: "application/json" },
      signal: timeoutSignal(DEFAULT_TIMEOUT_MS),
      cache: "no-store"
    }, options || {}));
    if (!response.ok) {
      throw new Error("Training API returned HTTP " + response.status);
    }
    const body = await response.json();
    if (!body || typeof body !== "object") {
      throw new Error("Training API returned an invalid payload");
    }
    return body;
  }

  function normalizeEnvelope(payload) {
    return Object.assign({
      schema_version: "training-center-v1",
      captured_at: null,
      sample_count: 0,
      data_age_seconds: null,
      provenance: [],
      stale: true,
      offline: false
    }, payload);
  }

  async function loadAll() {
    const entries = Object.entries(ENDPOINTS);
    const settled = await Promise.allSettled(entries.map(function (entry) {
      return request(entry[1]).then(function (payload) {
        return [entry[0], normalizeEnvelope(payload)];
      });
    }));
    const state = {
      source: "api",
      stale: false,
      offline: false,
      sections: {}
    };
    settled.forEach(function (result, index) {
      const key = entries[index][0];
      if (result.status === "fulfilled") {
        state.sections[key] = result.value[1];
      } else {
        state.sections[key] = normalizeEnvelope({
          stale: true,
          error: result.reason instanceof Error ? result.reason.message : "unavailable"
        });
        state.stale = true;
      }
    });
    if (Object.values(state.sections).every(function (section) { return section.error; })) {
      state.source = "demo-fallback";
      state.offline = true;
    }
    return state;
  }

  async function sendDialogue(message, context) {
    if (!message || !message.trim()) throw new Error("message is required");
    return request(ENDPOINTS.dialogue, {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({
        schema_version: "training-dialogue-v1",
        message: message.trim(),
        context: context || {}
      })
    });
  }


  async function control(action) {
    return request(ENDPOINTS.control, { method: "POST", headers: { Accept: "application/json", "Content-Type": "application/json" }, body: JSON.stringify({ action: action }) });
  }

  async function submitTraining(instruction) {
    if (!instruction || !instruction.trim()) throw new Error("instruction is required");
    return request(ENDPOINTS.trainingJob, { method: "POST", headers: { Accept: "application/json", "Content-Type": "application/json" }, body: JSON.stringify({ instruction: instruction.trim() }) });
  }

  function formatTimestamp(value) {
    if (!value) return "nicht verfügbar";
    return new Intl.DateTimeFormat("de-DE", { dateStyle: "medium", timeStyle: "medium" }).format(new Date(value));
  }
\n  global.RoutingTrainingCenter = {
    endpoints: Object.freeze(ENDPOINTS),
    loadAll: loadAll,
    sendDialogue: sendDialogue,
    control: control,
    submitTraining: submitTraining,
    formatTimestamp: formatTimestamp,
    normalizeEnvelope: normalizeEnvelope
  };
})(window);
