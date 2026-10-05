/**
 * Shared Markdown rendering layer.
 *
 * Consumed by both the Question Engine (Stage 2) and the AI Tutor (Stage 4) so
 * prompts and tutor answers render with identical typography, KaTeX, and code
 * highlighting. Sits below `features/` in the dependency graph — neither
 * feature imports the other.
 */

export { markdownComponents } from './markdownComponents';
export { CodeBlock } from './CodeBlock';
export { TutorMarkdown } from './TutorMarkdown';