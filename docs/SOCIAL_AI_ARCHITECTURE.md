# Social AI Architecture

G6.19 uses layered local-first AI rather than giving a language model direct application authority.

1. Explicit profile settings define user-controlled social levels.
2. SocialAiRuntime creates a deterministic bounded response plan.
3. SocialAiLearningRepository manages guarded persistent knowledge and recall.
4. SocialAiPromptPolicy turns the already-filtered plan into generation instructions.
5. SocialAiTextGenerationCoordinator may call an explicitly registered local backend.
6. Navigation remains outside this dependency direction.

The optional model backend is replaceable. No model or runtime is vendored by G6.19, and there is no mandatory network provider.

The deterministic planner remains useful without a text-generation model, allowing UI, memory, policy and safety behavior to be tested independently of a large model runtime.
