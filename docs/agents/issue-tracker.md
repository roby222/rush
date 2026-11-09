# Issue tracker

Locale, niente GitHub Issues.

- Spec: `docs/specs/<feature>.md`
- Ticket: `docs/specs/<feature>/issues/<NN>-<slug>.md`, uno per file, numerati in ordine di dipendenza (prima chi blocca).
- Stato nel campo `**Status:**` di ogni ticket: `ready-for-agent` → `in-progress` → `done`. Chi implementa aggiorna stato e checkbox.
- Un ticket è prendibile quando tutti i ticket in "Bloccato da" sono `done`.
