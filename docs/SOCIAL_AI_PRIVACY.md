# Social AI Privacy

Social AI is local-first. Explicit profile controls and learned memory are separate stores with separate responsibilities.

G6.19 does not require uploading conversation context, learned preferences or model prompts. Optional future network adapters must be explicit and must not silently replace the local path.

Model-facing recall is bounded. Persistent memory supports explicit forgetting. Diagnostics should expose counts and state rather than learned values unless the user explicitly requests to inspect those values.

Adult-flirt opt-in is an explicit setting and must never be inferred from learned memory or conversational behavior.
