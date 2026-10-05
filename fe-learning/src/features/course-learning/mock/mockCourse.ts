import type {
  CourseMaterial,
  CourseOutline,
  MaterialProgress,
  ProgressUpdate,
  TutorSuggestion,
} from '@/features/course-learning/types';

/**
 * Realistic sample course for local development.
 *
 * The DOCUMENT material carries Markdown + LaTeX so the selection -> tutor flow
 * and the KaTeX pipeline can be exercised without any binary assets.
 */
export const MOCK_COURSE: CourseOutline = {
  courseId: 1,
  code: 'CS201',
  title: 'Cấu trúc dữ liệu và Giải thuật',
  term: 'Học kỳ 1 · Năm học 2025–2026',
  modules: [
    {
      moduleId: 1,
      title: 'Mô đun 1 · Độ phức tạp và Đệ quy',
      description: 'Phân tích Big-O, đệ quy và chia để trị.',
      orderIndex: 1,
      materials: [
        {
          materialId: 101,
          title: 'Buổi 2 · Đệ quy và Chia để trị',
          materialType: 'DOCUMENT',
          videoUrl: null,
          documentUrl: null,
          textContent:
            '## Cơ sở của đệ quy\n\n' +
            'Một hàm được gọi là **đệ quy** khi nó tự gọi lại chính nó. Mọi hàm đệ quy đều cần một *điều kiện dừng* ' +
            'kết thúc quá trình gọi và một *trường hợp đệ quy* thu nhỏ dần bài toán về phía điều kiện dừng.\n\n' +
            '### Ngăn xếp lời gọi\n\n' +
            'Mỗi lời gọi sẽ đẩy một khung hình lên ngăn xếp lời gọi. Đoạn mã đệ quy tính giai thừa bên dưới có ' +
            'chi phí $T(n) = n \\cdot T(n-1) + O(1)$, nên với phép thế $T(n-1) \\approx T(n)/n$ ta có ' +
            '$T(n) = O(n!)$ — xem slide bên dưới.\n\n' +
            '### Định lý Master\n\n' +
            'Với bài toán chia để trị dạng $T(n) = aT(n/b) + f(n)$:\n\n' +
            '| Trường hợp | Điều kiện | Kết quả |\n| --- | --- | --- |\n' +
            '| 1 | $f(n) = O(n^{\\log_b a - \\varepsilon})$ | $\\Theta(n^{\\log_b a})$ |\n' +
            '| 2 | $f(n) = \\Theta(n^{\\log_b a})$ | $\\Theta(n^{\\log_b a}\\log n)$ |\n' +
            '| 3 | $f(n) = \\Omega(n^{\\log_b a + \\varepsilon})$ | $\\Theta(f(n))$ |\n\n' +
            '> Chọn nhầm trường hợp là lỗi phổ biến nhất trong kỳ thi. Hãy tính số mũ ' +
            '$\\log_b a$ trước khi so sánh bất cứ điều gì khác.\n\n' +
            '```python\n' +
            'def factorial(n: int) -> int:\n' +
            '    if n <= 1:          # điều kiện dừng\n' +
            '        return 1\n' +
            '    return n * factorial(n - 1)   # trường hợp đệ quy\n' +
            '```\n\n' +
            'Phiên bản đệ quy dễ đọc hơn nhưng tính lại các bài toán con giống nhau; kỹ thuật ghi nhớ ' +
            '(memoisation) đưa chi phí xuống còn $O(n)$.',
          durationSeconds: null,
          totalSlides: null,
        },
        {
          materialId: 102,
          title: 'Buổi 3 · Phân tích tiệm cận',
          materialType: 'DOCUMENT',
          videoUrl: null,
          documentUrl: null,
          textContent:
            '## Ký hiệu Big-O\n\n' +
            'Ta viết $f(n) = O(g(n))$ khi tồn tại các hằng số $c > 0$ và $n_0$ sao cho ' +
            '$f(n) \\le c \\cdot g(n)$ với mọi $n \\ge n_0$.\n\n' +
            'Các tốc độ tăng phổ biến, xếp từ nhỏ đến lớn:\n\n' +
            '1. $O(1)$ — hằng số\n2. $O(\\log n)$ — lôgarit\n3. $O(n)$ — tuyến tính\n' +
            '4. $O(n \\log n)$ — tuyến tính lôgarit\n5. $O(n^2)$ — bậc hai\n6. $O(2^n)$ — mũ\n',
          durationSeconds: null,
          totalSlides: null,
        },
      ],
    },
    {
      moduleId: 2,
      title: 'Mô đun 2 · Cây và Đồ thị',
      description: 'Cây nhị phân, phương pháp duyệt và đường đi ngắn nhất.',
      orderIndex: 2,
      materials: [
        {
          materialId: 201,
          title: 'Duyệt đồ thị (video)',
          materialType: 'VIDEO',
          videoUrl: null, // cung cấp tệp thật để kiểm thử trình phát
          documentUrl: null,
          textContent: null,
          durationSeconds: 2_760,
          totalSlides: null,
        },
      ],
    },
  ],
};

/** Seed progress so the resume marker and completion indicator are visible. */
export const MOCK_PROGRESS: Record<number, MaterialProgress> = {
  101: {
    materialId: 101,
    lastWatchedSeconds: 0,
    percentComplete: 1,
    lastViewedAt: '2026-03-08T09:12:00Z',
  },
  102: {
    materialId: 102,
    lastWatchedSeconds: 0,
    percentComplete: 0.35,
    lastViewedAt: '2026-03-09T14:03:00Z',
  },
};

/**
 * Follow-up chips.
 *
 * In production these come from the FastAPI service alongside the answer; they
 * are static here so the UI can be developed without the tutor running.
 */
export const MOCK_SUGGESTIONS: readonly TutorSuggestion[] = [
  {
    id: 's1',
    label: 'Xem một ví dụ được giải chi tiết',
    prompt: 'Hãy đi từng bước giải một ví dụ về định lý Master.',
  },
  {
    id: 's2',
    label: 'Điều gì dễ sai?',
    prompt: 'Những lỗi sai thường gặp của sinh viên với nội dung này là gì?',
  },
  {
    id: 's3',
    label: 'Ra câu hỏi kiểm tra tôi',
    prompt: 'Hãy đưa cho tôi hai câu hỏi luyện tập về nội dung này.',
  },
] as const;

/**
 * Records a progress update in place.
 *
 * A stand-in for `PUT /learning-progress`; swapping in `learningApi` is a
 * one-line change once the endpoint exists.
 */
export const recordProgress = (
  courseId: number,
  update: ProgressUpdate,
): { courseId: number; update: ProgressUpdate; savedAt: number } => ({
  courseId,
  update,
  savedAt: Date.now(),
});

/** Flattens the outline into the material order used by the sidebar list. */
export const flattenMaterials = (outline: CourseOutline): CourseMaterial[] =>
  outline.modules
    .slice()
    .sort((a, b) => a.orderIndex - b.orderIndex)
    .flatMap((module) => module.materials);