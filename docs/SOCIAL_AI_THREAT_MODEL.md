# Social AI Threat Model

## Protected assets

Route truth, route geometry, navigation progress, positioning integrity, permissions, critical guidance, explicit user settings and user-locked memories.

## Trust boundaries

Model output is untrusted presentation text. Learned automatic candidates are untrusted until accepted by the learning policy. Model/runtime artifacts are untrusted until provenance and SHA-256 checks succeed.

## Required controls

- no Social AI route/progress/position/permission mutators
- explicit adult-flirt opt-in; never infer it
- no flirt in navigation status or critical guidance
- critical guidance suppresses nonessential social expression
- bounded memory and prompt inputs
- exact artifact digest verification
- no implicit network fallback
- user-locked memories outrank automatic inference
- explicit forget operations

## Non-goals

G6.19 does not self-modify model weights, autonomously download models, infer adult eligibility, or let generated text become authoritative navigation state.
