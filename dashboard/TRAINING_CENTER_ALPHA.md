# Training Center Alpha

The static prototype in training-center-alpha.html is the first visual shell for the AI Training Center.

## Design contract

- Asymmetric panels with non-repeating organic geometry.
- Dark mission-control theme with cyan, violet, green, amber and red semantic accents.
- Performance evolution is the visual center.
- Every quality value is shown with status and context.
- Live, analysis and control are visually separated.
- The shell remains usable on desktop, tablet and mobile.

## Runtime integration points

The prototype is intentionally dependency-free. Replace its sample values with these API resources:

- GET /api/training/status
- GET /api/training/performance?from=&to=
- GET /api/training/quality
- GET /api/training/pipeline
- GET /api/training/regressions
- POST /api/training/dialogue

The production API must return sample count, data age, schema version and provenance with every metric. A missing API or cloud service must leave navigation unaffected; the UI should show stale/offline state instead of inventing values.
