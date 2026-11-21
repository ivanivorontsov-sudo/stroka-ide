# Pонятный build fixture

Minimal program used to smoke-test compilation, standard-library imports, standalone Node output, web packaging, Android WebView packaging, and Bun native cross-target builds.

Workflow expression escaping corrected; the fixture remains the CI entry point.

CI also exercises push-triggered default source selection.

The CI checksum step runs from the repository root so every artifact is included.
