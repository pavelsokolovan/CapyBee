# Repository Workflow

## Specifications

- When the user asks to prepare a specification, create the specification in `docs/specifications/` and follow the folder's existing naming, numbering, and documentation conventions.
- When the user asks to implement a specification, locate and read the corresponding specification in `docs/specifications/` before changing code.
- Implement the complete specification, then run the build and all applicable tests required by `.github/instructions/build-workflow.instructions.md`.
- If a build or test fails because of the implementation, diagnose and fix the failure, then rerun the relevant verification until it passes.
- After the implementation and required verification pass, commit all changes for that specification directly to `main` with this commit-message format: `spec <number> is implemented - <short description>`.
- Do not include unrelated pre-existing working-tree changes in the implementation commit. If unrelated changes prevent a safe complete commit, stop and ask the user how to handle them.
