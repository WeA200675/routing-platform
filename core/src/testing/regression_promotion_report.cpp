#include "routing/core/testing/regression_promotion_report.hpp"

#include <sstream>

#include "routing/core/testing/route_metric.hpp"

namespace routing::core::testing {

std::string format_regression_promotion_report(
    const std::vector<RegressionPromotionProposal>& proposals) {
  std::ostringstream output;

  output
      << "REGRESSION PROMOTION PROPOSALS\n"
      << "proposals: "
      << proposals.size()
      << "\n\n";

  for (const auto& proposal :
       proposals) {
    output
        << "["
        << regression_promotion_readiness_key(
               proposal.readiness)
        << "] "
        << proposal.proposal_id
        << "\n"
        << "  session: "
        << proposal.session_id
        << "\n"
        << "  issue: "
        << proposal.issue_key
        << "\n"
        << "  title: "
        << proposal.title
        << "\n"
        << "  summary: "
        << proposal.summary
        << "\n"
        << "  observed route: "
        << proposal.observed_route_id
        << "\n";

    if (!proposal
             .preferred_alternative_route_id
             .empty()) {
      output
          << "  preferred alternative: "
          << proposal
                 .preferred_alternative_route_id
          << "\n";
    }

    if (proposal.metric_suggestion
            .has_value()) {
      output
          << "  metric suggestion: "
          << route_metric_key(
                 proposal.metric_suggestion
                     ->metric)
          << " ["
          << metric_improvement_direction_key(
                 proposal.metric_suggestion
                     ->direction)
          << "]"
          << "\n"
          << "  metric reason: "
          << proposal.metric_suggestion
                 ->reason_key
          << "\n"
          << "  threshold: HUMAN REVIEW REQUIRED\n";
    }

    output
        << "  runtime semantics: "
        << (proposal.runtime_semantics_complete
                ? "complete"
                : "INCOMPLETE")
        << "\n";

    if (!proposal.missing_runtime_inputs
             .empty()) {
      output
          << "  missing runtime inputs: ";

      for (std::size_t index = 0;
           index <
               proposal
                   .missing_runtime_inputs
                   .size();
           ++index) {
        if (index != 0) {
          output << ",";
        }

        output
            << proposal
                   .missing_runtime_inputs[
                       index];
      }

      output << "\n";
    }

    output
        << "  human approval: REQUIRED\n\n";
  }

  return output.str();
}

}  // namespace routing::core::testing
