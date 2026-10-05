/**
 * Public surface of the Question Engine.
 *
 * A host (exam runner, practice mode, AI tutor) should import from this barrel
 * only. Internal modules are not part of the contract and may change freely.
 */

/* Contracts */
export type {
  AnyQuestionPayload,
  AnyQuestionResponse,
  BlankDefinition,
  ChoiceOption,
  FillInBlankPayload,
  FillInBlankResponse,
  LabeledItem,
  MatchItem,
  MatchingPayload,
  MatchingResponse,
  MultipleChoicePayload,
  MultipleChoiceResponse,
  OrderedItem,
  OrderingPayload,
  OrderingResponse,
  QuestionMetadata,
  QuestionProps,
  QuestionTypeMap,
} from './types';
export { QuestionType, SUPPORTED_QUESTION_TYPES } from './types';

/* Dispatcher */
export {
  DynamicQuestionRenderer,
  UnsupportedQuestion,
  canRender,
  resolveRenderer,
} from './DynamicQuestionRenderer';
export type { DynamicQuestionRendererProps } from './DynamicQuestionRenderer';

/* Individual renderers, for hosts that want to bypass the registry. */
export { MultipleChoiceRenderer } from './renderers/MultipleChoiceRenderer';
export { FillInBlankRenderer, parsePromptSegments } from './renderers/FillInBlankRenderer';
export { MatchingRenderer } from './renderers/MatchingRenderer';
export { OrderingRenderer, moveItem } from './renderers/OrderingRenderer';

/* Helpers */
export { RichText } from './components/RichText';
export { createEmptyResponse, hasAnswer, parseResponse } from './factories';

/* Fixtures */
export * from './mockQuestions';