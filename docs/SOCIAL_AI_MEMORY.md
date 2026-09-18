# Social AI Memory

Persistent Social AI memory is separate from the user-profile blob and from model weights.

## Learning

Automatic candidates are accepted only when profile learning is enabled and the configured confidence threshold is met. Explicit memories and user corrections are accepted as user-locked knowledge.

Automatic inference must not silently overwrite user-locked knowledge.

## Recall

Recall is bounded to at most 64 entries. The default is 24. Explicit user-locked entries rank ahead of automatic inferences, followed by confidence and recency. Contextual entries are recalled only for an exact matching context key.

## Forgetting

Entries can be forgotten individually or the complete Social AI memory store can be cleared. Forgetting memory does not mutate routing, navigation state or the explicit profile settings.

## Persistence

Android persistence is synchronous at the repository boundary so an accepted explicit memory is committed before the operation reports success.
