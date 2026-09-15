#include <cassert>

#include "routing/core/intelligence/social_response_planner.hpp"

int main(){
  using namespace routing::core::intelligence;
  SocialResponsePlanner planner;
  SocialNeuralOutput expressive;
  expressive.humor_level=80.0; expressive.warmth_level=80.0; expressive.directness_level=60.0; expressive.charm_level=80.0; expressive.playfulness_level=80.0; expressive.flirt_level=80.0;
  const auto conversation=planner.plan(SocialResponseContext::Conversation,expressive,true);
  assert(conversation.allow_banter); assert(conversation.allow_flirt); assert(conversation.flirt_level>0.0); assert(!conversation.suppress_nonessential_social);
  const auto no_opt=planner.plan(SocialResponseContext::Conversation,expressive,false);
  assert(!no_opt.allow_flirt); assert(no_opt.flirt_level==0.0);
  const auto navigation=planner.plan(SocialResponseContext::NavigationStatus,expressive,true);
  assert(navigation.humor_level<conversation.humor_level); assert(navigation.charm_level<conversation.charm_level); assert(navigation.playfulness_level<conversation.playfulness_level); assert(navigation.flirt_level==0.0); assert(!navigation.allow_flirt);
  const auto critical=planner.plan(SocialResponseContext::CriticalGuidance,expressive,true);
  assert(critical.humor_level==0.0); assert(critical.charm_level==0.0); assert(critical.playfulness_level==0.0); assert(critical.flirt_level==0.0); assert(critical.warmth_level>=70.0); assert(critical.directness_level>=90.0); assert(!critical.allow_banter); assert(!critical.allow_flirt); assert(critical.suppress_nonessential_social);
  return 0;
}
