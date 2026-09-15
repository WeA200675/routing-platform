#include <cassert>
#include <cmath>
#include <stdexcept>

#include "routing/core/intelligence/learning_memory.hpp"

namespace {
bool nearly_equal(const double a,const double b,const double epsilon=1e-9){return std::abs(a-b)<=epsilon;}
}

int main(){
  using namespace routing::core;
  using namespace routing::core::intelligence;
  LearningMemory memory;
  LearningObservation first; first.id="drive-1"; first.attribute=Attribute::SpeedLimitKmh; first.preference_signal=0.8; first.confidence=1.0; first.scope=MemoryScope::LongTerm; memory.add(first);
  LearningObservation second; second.id="drive-2"; second.attribute=Attribute::SpeedLimitKmh; second.preference_signal=0.2; second.confidence=0.5; second.scope=MemoryScope::LongTerm; memory.add(second);
  const auto long_term=memory.summarize(Attribute::SpeedLimitKmh,MemoryScope::LongTerm);
  assert(long_term.observation_count==2); assert(nearly_equal(long_term.confidence_weighted_signal,0.6)); assert(nearly_equal(long_term.mean_confidence,0.75));
  LearningObservation trailer; trailer.id="drive-trailer"; trailer.attribute=Attribute::UrbanScore; trailer.preference_signal=-0.9; trailer.confidence=0.95; trailer.scope=MemoryScope::Contextual; trailer.context_key="vehicle:trailer"; memory.add(trailer);
  assert(memory.summarize(Attribute::UrbanScore,MemoryScope::Contextual,"vehicle:trailer").observation_count==1);
  assert(memory.summarize(Attribute::UrbanScore,MemoryScope::Contextual,"vehicle:solo").observation_count==0);
  bool duplicate_threw=false; try{memory.add(first);}catch(const std::invalid_argument&){duplicate_threw=true;} assert(duplicate_threw);
  bool invalid_signal_threw=false; try{LearningObservation invalid; invalid.id="invalid"; invalid.preference_signal=1.5; memory.add(invalid);}catch(const std::invalid_argument&){invalid_signal_threw=true;} assert(invalid_signal_threw);
  assert(memory.erase("drive-1")); assert(!memory.erase("does-not-exist")); assert(memory.size()==2);
  LearnedKnowledge social; social.id="social-humor-1"; social.kind=LearnedKnowledgeKind::SocialPreference; social.key="social.humor.style"; social.value="dry"; social.confidence=1.0; social.scope=MemoryScope::LongTerm; social.source="explicit-user"; social.user_locked=true; memory.add_knowledge(social);
  assert(memory.knowledge_size()==1); const auto* stored=memory.find_knowledge("social-humor-1"); assert(stored!=nullptr); assert(stored->value=="dry"); assert(stored->user_locked);
  assert(memory.find_knowledge_by_key(LearnedKnowledgeKind::SocialPreference,"social.humor.style",MemoryScope::LongTerm).size()==1);
  LearnedKnowledge contextual; contextual.id="social-context-1"; contextual.kind=LearnedKnowledgeKind::InteractionPattern; contextual.key="conversation.energy"; contextual.value="quiet"; contextual.confidence=0.8; contextual.scope=MemoryScope::Contextual; contextual.context_key="drive:night"; contextual.source="conversation"; memory.add_knowledge(contextual);
  assert(memory.find_knowledge_by_key(LearnedKnowledgeKind::InteractionPattern,"conversation.energy",MemoryScope::Contextual,"drive:night").size()==1);
  assert(memory.find_knowledge_by_key(LearnedKnowledgeKind::InteractionPattern,"conversation.energy",MemoryScope::Contextual,"drive:day").empty());
  bool duplicate_global=false; try{LearnedKnowledge d; d.id="drive-2"; d.key="duplicate"; d.value="duplicate"; d.confidence=1.0; d.source="test"; memory.add_knowledge(d);}catch(const std::invalid_argument&){duplicate_global=true;} assert(duplicate_global);
  bool missing_source=false; try{LearnedKnowledge i; i.id="knowledge-invalid"; i.key="test"; i.value="value"; i.confidence=0.5; memory.add_knowledge(i);}catch(const std::invalid_argument&){missing_source=true;} assert(missing_source);
  bool missing_context=false; try{LearnedKnowledge i; i.id="knowledge-context-invalid"; i.key="test"; i.value="value"; i.confidence=0.5; i.scope=MemoryScope::Contextual; i.source="test"; memory.add_knowledge(i);}catch(const std::invalid_argument&){missing_context=true;} assert(missing_context);
  assert(memory.erase_knowledge("social-context-1")); assert(!memory.erase_knowledge("does-not-exist")); assert(memory.knowledge_size()==1);
  memory.clear(); assert(memory.size()==0); assert(memory.knowledge_size()==0);
  return 0;
}
