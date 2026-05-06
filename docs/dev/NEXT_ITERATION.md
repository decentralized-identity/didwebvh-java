# Run the Next Iteration

Copy and paste the prompt below into an AI agent (Claude Code, Cursor, etc.) to execute the next iteration. The agent will figure out which iteration to run, do the work, and ask you to review before committing.

---

## Prompt

```
You are working on the didwebvh-java project.

Your goal is to complete the next unfinished iteration safely and incrementally.

Process:

1. Open docs/dev/ITERATIONS.md and identify the first [NOT STARTED] iteration.
   - If any iteration is [IN PROGRESS], stop and report it.
   - If all are [DONE], report that no work remains.

2. Read only the files strictly needed for this iteration.
   - Do not read or summarize entire repository documents unless required, but be aware that docs/AGENTS.md and docs/ARCHITECTURE.md are important and read them if necessary.
   - If the spec PDF is needed, extract only the exact requirement needed and summarize it briefly in your own words.

3. Before code changes, update only the single status line for the target iteration:
   [NOT STARTED] -> [IN PROGRESS]

4. Implement the iteration in small, minimal patches.
   - Work one artifact or concern at a time.
   - Prefer unified diffs.
   - Do not output full files unless creating a new file is unavoidable.
   - Do not reproduce large unchanged code or documentation.
   - Avoid reproducing canonical or published text templates unless absolutely required.

5. For standard boilerplate files such as LICENSE, CI workflow, Checkstyle config, or other template-like artifacts:
   - Use the smallest repository-appropriate content.
   - Avoid large verbatim template reproduction.
   - If a file would require mostly canonical text, stop and report that separately instead of forcing generation.

6. Stay strictly within the current iteration.
   - If a small missing dependency is required, implement the minimal version and note it.
   - If ambiguity or architectural tradeoffs appear, stop and present options.

7. Add or update tests for all new behavior.

8. Assume verification with ./mvnw clean verify is required, and use JDK 21 for that verification when possible.
   SpotBugs is skipped on JDK 22+, so JDK 25 does not provide the same local signal as the Java 11, 17, and
   21 CI jobs.

9. After implementation, update only the target iteration section:
   - [IN PROGRESS] -> [DONE]
   - add short Implementation Notes (brief bullets only)

10. Final output:
   - 3–5 bullet summary
   - list of changed files
   - minimal diffs or concise snippets
   - suggested Conventional Commit message
   - warnings/review notes

Never restate large portions of repository files or the specification.
Never rewrite whole markdown files when a line-level edit is enough.
```
