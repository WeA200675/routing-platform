# G6.19 Social Planner Model Card

## Purpose

The built-in SocialAiRuntime is a small deterministic bounded scoring model for presentation behavior. It is not a general language model and it does not modify its weights.

## Inputs

Explicit social levels, warmth/directness inputs, engagement, context confidence and one of Conversation, NavigationStatus or CriticalGuidance.

## Outputs

Bounded 0..100 presentation levels plus allowBanter, allowFlirt and suppressNonessentialSocial flags.

## Safety behavior

Adult flirt requires explicit opt-in. Navigation status sets effective flirt to zero. Critical guidance sets humor, charm, playfulness, proactivity and flirt to zero while increasing minimum directness and warmth.

## Limitations

The planner does not understand arbitrary natural language, establish factual truth or make routing decisions. Optional local text generation remains downstream of this policy layer.
