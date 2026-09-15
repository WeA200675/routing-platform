#include <cassert>

#include "routing/core/intelligence/neural_social_model.hpp"

namespace {
bool in_level_range(const double value){return value>=0.0&&value<=100.0;}
}

int main(){
  using namespace routing::core::intelligence;
  NeuralSocialModel model;
  SocialNeuralInput social;
  social.humor_level=100.0; social.warmth_level=90.0; social.directness_level=60.0; social.charm_level=100.0; social.playfulness_level=100.0; social.flirt_level=100.0; social.engagement=1.0; social.context_confidence=1.0; social.adult_flirt_opt_in=true;
  const auto expressive=model.infer(social);
  assert(in_level_range(expressive.humor_level)); assert(in_level_range(expressive.warmth_level)); assert(in_level_range(expressive.directness_level)); assert(in_level_range(expressive.charm_level)); assert(in_level_range(expressive.playfulness_level)); assert(in_level_range(expressive.flirt_level));
  assert(expressive.humor_level>0.0); assert(expressive.charm_level>0.0); assert(expressive.playfulness_level>0.0); assert(expressive.flirt_level>0.0);
  social.adult_flirt_opt_in=false; assert(model.infer(social).flirt_level==0.0);
  social.adult_flirt_opt_in=true; social.critical_guidance=true; const auto critical=model.infer(social);
  assert(critical.humor_level==0.0); assert(critical.charm_level==0.0); assert(critical.playfulness_level==0.0); assert(critical.flirt_level==0.0); assert(critical.warmth_level>=70.0); assert(critical.directness_level>=90.0);
  SocialNeuralInput out; out.humor_level=500.0; out.warmth_level=-500.0; out.directness_level=500.0; out.charm_level=500.0; out.playfulness_level=500.0; out.flirt_level=500.0; out.engagement=5.0; out.context_confidence=-5.0; out.adult_flirt_opt_in=true;
  const auto bounded=model.infer(out);
  assert(in_level_range(bounded.humor_level)); assert(in_level_range(bounded.warmth_level)); assert(in_level_range(bounded.directness_level)); assert(in_level_range(bounded.charm_level)); assert(in_level_range(bounded.playfulness_level)); assert(in_level_range(bounded.flirt_level));
  return 0;
}
