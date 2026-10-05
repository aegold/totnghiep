import { QuestionType } from '@/features/question-engine/types';
import type {
  AnyQuestionPayload,
  FillInBlankPayload,
  MatchingPayload,
  MultipleChoicePayload,
  OrderingPayload,
  QuestionTypeMap,
} from '@/features/question-engine/types';

/**
 * A payload paired with its discriminator — the shape a `question_versions` row
 * yields once its `content_payload` JSON column is parsed.
 *
 * The mapped type keeps `payload` tied to `questionType`, so a mismatched
 * fixture is a compile error rather than a runtime surprise.
 */
export type MockQuestion<T extends QuestionType = QuestionType> = {
  [K in QuestionType]: {
    id: number;
    title: string;
    /** Mirrors the `questions.question_type` enum value. */
    questionType: K;
    payload: QuestionTypeMap[K]['payload'];
  };
}[T];

/* -------------------------------------------------------------------------- */
/*  1. MULTIPLE_CHOICE                                                          */
/* -------------------------------------------------------------------------- */

/** Trắc nghiệm một đáp án, nhiều công thức LaTeX. */
export const mathSingleChoice: MockQuestion<QuestionType.MULTIPLE_CHOICE> = {
  id: 101,
  title: 'Tổng các nghiệm của phương trình bậc hai (chọn một)',
  questionType: QuestionType.MULTIPLE_CHOICE,
  payload: {
    prompt:
      'Cho $f(x) = 2x^2 - 8x + 6$, **tổng các nghiệm** của $f(x) = 0$ bằng bao nhiêu?\n\n' +
      '> Gợi ý: nhớ lại công thức Viète cho $ax^2 + bx + c = 0$.',
    allowMultiple: false,
    options: [
      { id: 'opt_a', text: '$-\\dfrac{b}{a} = 4$' },
      { id: 'opt_b', text: '$\\dfrac{c}{a} = 3$' },
      { id: 'opt_c', text: '$-\\dfrac{c}{a} = -3$' },
      { id: 'opt_d', text: '$\\dfrac{b}{a} = -4$' },
      { id: 'opt_e', text: 'Phương trình không có nghiệm thực.' },
    ],
    hint: 'Thay $a=2$ và $b=-8$ vào công thức tổng nghiệm.',
  },
};

/** Trắc nghiệm nhiều đáp án về mạng máy tính. */
export const networkMultipleChoice: MockQuestion<QuestionType.MULTIPLE_CHOICE> = {
  id: 102,
  title: 'TCP và UDP (chọn nhiều)',
  questionType: QuestionType.MULTIPLE_CHOICE,
  payload: {
    prompt:
      'Trong các phát biểu về **TCP** và **UDP** dưới đây, phát biểu nào là **đúng**?',
    allowMultiple: true,
    options: [
      { id: 'opt_tcp_1', text: 'TCP cung cấp luồng byte đáng tin cậy và có thứ tự.' },
      { id: 'opt_tcp_2', text: 'UDP không kết nối và không có bước bắt tay (handshake).' },
      { id: 'opt_tcp_3', text: 'UDP bảo đảm việc truyền dữ liệu *và* đúng thứ tự.' },
      { id: 'opt_tcp_4', text: 'TCP gặp hiện ứng chặn đầu hàng (head-of-line blocking) trên luồng byte.' },
      { id: 'opt_tcp_5', text: 'UDP phù hợp với truyền thời gian thực như thoại IP, vì dữ liệu đến muộn là vô ích.' },
    ],
    hint: 'Hãy chọn **tất cả** các phương án đúng.',
  },
};

/* -------------------------------------------------------------------------- */
/*  2. FILL_IN_BLANK                                                            */
/* -------------------------------------------------------------------------- */

/** Ô trống nằm giữa câu. */
export const chemistryFillInBlank: MockQuestion<QuestionType.FILL_IN_BLANK> = {
  id: 201,
  title: 'Công thức phân tử của nước',
  questionType: QuestionType.FILL_IN_BLANK,
  payload: {
    prompt:
      'Công thức phân tử của nước là H{{twoSubscript}}O{{oneSubscript}}, và khối lượng mol của nó xấp xỉ {{molarMass}} $\\text{g/mol}$.',
    blanks: [
      { id: 'twoSubscript', hint: 'số tự nhiên' },
      { id: 'oneSubscript', hint: 'số tự nhiên' },
      { id: 'molarMass', hint: 'ví dụ: 18.015' },
    ],
    hint: 'Nguyên tử khối của hiđro xấp xỉ 1.008 và của oxi xấp xỉ 15.999.',
  },
};

/** Ô trống nằm bên trong một biểu thức LaTeX. */
export const calculusFillInBlank: MockQuestion<QuestionType.FILL_IN_BLANK> = {
  id: 202,
  title: 'Hằng số tích phân bất định',
  questionType: QuestionType.FILL_IN_BLANK,
  payload: {
    prompt:
      'Vì $\\dfrac{d}{dx}\\left[\\frac{x^2}{2}\\right] = x$, suy ra ' +
      '$\\int x\\,dx = \\frac{x^2}{2} + {{constant}}$.',
    blanks: [{ id: 'constant', hint: 'hằng số C' }],
  },
};

/* -------------------------------------------------------------------------- */
/*  3. MATCHING                                                                 */
/* -------------------------------------------------------------------------- */

/** Mạng máy tính: ghép tầng OSI với thiết bị hoặc giao thức. */
export const networkLayerMatching: MockQuestion<QuestionType.MATCHING> = {
  id: 301,
  title: 'Các tầng OSI và thiết bị tương ứng',
  questionType: QuestionType.MATCHING,
  payload: {
    prompt:
      'Ghép mỗi tầng OSI với thiết bị hoặc giao thức hoạt động **chủ yếu** ở tầng đó.',
    leftItems: [
      { id: 'l1', text: 'Tầng 1 — Vật lý' },
      { id: 'l2', text: 'Tầng 2 — Liên kết dữ liệu' },
      { id: 'l3', text: 'Tầng 3 — Mạng' },
      { id: 'l7', text: 'Tầng 7 — Ứng dụng' },
    ],
    rightItems: [
      { id: 'r_cable', text: 'Cáp đôi xoắn' },
      { id: 'r_switch', text: 'Switch Ethernet / NIC' },
      { id: 'r_router', text: 'Bộ định tuyến IP' },
      { id: 'r_http', text: 'HTTP' },
    ],
    hint: 'Mỗi tầng có đúng một đối tác tương ứng.',
  },
};

/** Lịch sử: ghép sự kiện với năm xảy ra. */
export const historyMatching: MockQuestion<QuestionType.MATCHING> = {
  id: 302,
  title: 'Ghép sự kiện với năm',
  questionType: QuestionType.MATCHING,
  payload: {
    prompt: 'Ghép mỗi sự kiện lịch sử với năm mà nó xảy ra.',
    leftItems: [
      { id: 'l_wwii', text: 'Kết thúc Chiến tranh Thế giới thứ hai' },
      { id: 'l_moon', text: 'Lần đầu tiêu đất Mặt Trăng có người' },
      { id: 'l_berlin', text: 'Sập Bức tường Berlin' },
    ],
    rightItems: [
      { id: 'r_1945', text: '1945' },
      { id: 'r_1969', text: '1969' },
      { id: 'r_1989', text: '1989' },
    ],
  },
};

/* -------------------------------------------------------------------------- */
/*  4. SẮP XẾP                                                                 */
/* -------------------------------------------------------------------------- */

/** Lịch sử: trình tự thời gian. */
export const historyOrdering: MockQuestion<QuestionType.ORDERING> = {
  id: 401,
  title: 'Mốc thời gian Chiến tranh Việt Nam',
  questionType: QuestionType.ORDERING,
  payload: {
    prompt: 'Hãy sắp xếp các sự kiện sau theo **thứ tự thời gian**, sự kiện sớm nhất đứng trước.',
    items: [
      { id: 'i_ho', text: 'Chủ tịch Hồ Chí Minh tuyên bố độc lập' },
      { id: 'i_gulf', text: 'Nghị quyết của Vịnh Bộc Cung' },
      { id: 'i_tet', text: 'Tổng khởi nghĩa Mùa Xuân' },
      { id: 'i_paris', text: 'Hiệp định Paris' },
      { id: 'i_fall', text: 'Giải phóng Sài Gòn' },
    ],
    hint: 'Các sự kiện trải rộng từ năm 1954 đến năm 1975.',
  },
};

/** Mạng máy tính: thứ tự các gói tin. */
export const tcpHandshakeOrdering: MockQuestion<QuestionType.ORDERING> = {
  id: 402,
  title: 'Bắt tay ba bước của TCP',
  questionType: QuestionType.ORDERING,
  payload: {
    prompt: 'Hãy đặt các gói tin thiết lập kết nối TCP theo đúng thứ tự.',
    items: [
      { id: 'p_syn', text: 'SYN' },
      { id: 'p_synack', text: 'SYN + ACK' },
      { id: 'p_ack', text: 'ACK' },
      { id: 'p_fin', text: 'FIN (phía mở đầu kết thúc)' },
      { id: 'p_finack', text: 'FIN + ACK (phía trả lời)' },
    ],
  },
};

/* -------------------------------------------------------------------------- */
/*  Các bộ dữ liệu dùng cho trang xem trước                                      */
/* -------------------------------------------------------------------------- */

/** Toàn bộ câu hỏi mẫu, theo thứ tự cố định, dùng cho trang xem trước. */
export const MOCK_QUESTIONS: readonly MockQuestion[] = [
  mathSingleChoice,
  networkMultipleChoice,
  chemistryFillInBlank,
  calculusFillInBlank,
  networkLayerMatching,
  historyMatching,
  historyOrdering,
  tcpHandshakeOrdering,
] as const;

/**
 * Widened view for the preview, whose dispatcher deliberately handles all
 * types at once and therefore works with the union rather than one member.
 */
export const MOCK_QUESTION_ENTRIES: readonly {
  id: number;
  title: string;
  questionType: QuestionType;
  payload: AnyQuestionPayload;
}[] = MOCK_QUESTIONS.map((question: MockQuestion) => ({
  id: question.id,
  title: question.title,
  questionType: question.questionType,
  payload: question.payload,
}));

/**
 * Payload for a type the engine cannot render, used to preview the
 * `UnsupportedQuestion` fallback.
 */
export const UNSUPPORTED_QUESTION_PAYLOAD: Record<string, unknown> = {
  prompt: 'This question type ships with a newer build of the platform.',
};

/** Type-specific exports for hosts importing a single fixture. */
export const mockPayloads = {
  multipleChoice: mathSingleChoice.payload as MultipleChoicePayload,
  fillInBlank: chemistryFillInBlank.payload as FillInBlankPayload,
  matching: networkLayerMatching.payload as MatchingPayload,
  ordering: historyOrdering.payload as OrderingPayload,
} as const;