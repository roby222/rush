# Agent knowledge

One file per subject; read the ones your task touches before editing.

- [product.md](product.md): who uses Rush, what it promises, product principles. Read for any change to behavior, copy or notifications.
- [design.md](design.md): visual system and screen patterns. Read for any UI change.
- [architecture.md](architecture.md): data flow, TomTom API facts, storage, scheduling invariants. Read before touching `TomTom.kt`, `CheckWorker.kt`, `Scheduler.kt` or `Prefs.kt`.
- [workflow.md](workflow.md): testing on the phone, git and releases, local issue tracker. Read before running the app, committing, or picking up a ticket.

## House rules

- Rush stays generic: real addresses appear only on the user's device. Code, docs, tests and screenshots use public demo places (Milan, New York).
- Every user-visible text lives in `res/values/strings.xml` (English, the fallback) and `res/values-it/strings.xml` (Italian, picked automatically on Italian devices); add each new string to both. Docs and code comments are English; tickets are Italian (see `workflow.md`).
- Keep each fact in one file here. When you learn something durable about the project, add it to the matching file in the same change.
