import type {
  KnowledgeNodeData,
  KnowledgeNodeStatus,
  RelatedLesson,
} from '@/features/knowledge-graph/types';

/**
 * Sample CS curriculum used for local development.
 *
 * Deliberately exercises the awkward cases rather than a tidy chain:
 *  - shared prerequisites (one skill unlocking several dependents)
 *  - a deep chain, to test rank assignment
 *  - entry points with NO prerequisites
 *  - an orphan referencing a prerequisite id that no longer exists
 *  - a wide fan-out, so `rankdir: 'TB'` is under real pressure
 */

type Seed = [id: string, title: string, topic: string, prereqs: string[], score: number];

const SEEDS: readonly Seed[] = [
  // ---- Nền tảng ----
  ['cs101', 'Tư duy tính toán', 'Nền tảng', [], 92],
  ['math101', 'Toán rời rạc', 'Toán học', [], 88],
  ['cs102', 'Ngôn ngữ lập trình cơ bản', 'Nền tảng', ['cs101'], 85],

  // ---- Khối Toán học ----
  ['math102', 'Giải tích I', 'Toán học', ['math101'], 74],
  ['math103', 'Đại số tuyến tính', 'Toán học', ['math101'], 91],
  ['math104', 'Xác suất và Thống kê', 'Toán học', ['math101', 'math102'], 46],
  ['math105', 'Xác suất rời rạc', 'Toán học', ['math104'], 0],
  ['math106', 'Phương pháp số', 'Toán học', ['math103', 'math104'], 0],

  // ---- Khối Lập trình ----
  ['cs103', 'Thiết kế hướng đối tượng', 'Lập trình', ['cs102'], 79],
  ['cs104', 'Lập trình hàm', 'Lập trình', ['cs102'], 62],
  ['cs105', 'Kỹ thuật phần mềm', 'Lập trình', ['cs103', 'cs104'], 0],

  // ---- Cấu trúc dữ liệu (trung tâm phân nhánh) ----
  ['cs201', 'Mảng và Danh sách liên kết', 'Cấu trúc dữ liệu', ['cs102'], 95],
  ['cs202', 'Ngăn xếp và Hàng đợi', 'Cấu trúc dữ liệu', ['cs201'], 90],
  ['cs203', 'Bảng băm', 'Cấu trúc dữ liệu', ['cs201', 'cs202'], 68],
  ['cs204', 'Cây nhị phân', 'Cấu trúc dữ liệu', ['cs202'], 81],
  ['cs205', 'Cây cân bằng (AVL, Đỏ-Đen)', 'Cấu trúc dữ liệu', ['cs204'], 34],
  ['cs206', 'Heap và Hàng đợi ưu tiên', 'Cấu trúc dữ liệu', ['cs203'], 57],
  ['cs207', 'Đồ thị và Giải thuật trên đồ thị', 'Cấu trúc dữ liệu', ['cs203', 'cs204'], 12],

  // ---- Giải thuật ----
  ['cs301', 'Đệ quy và Chia để trị', 'Giải thuật', ['cs102', 'math101'], 86],
  ['cs302', 'Giải thuật sắp xếp', 'Giải thuật', ['cs206', 'cs301'], 72],
  ['cs303', 'Tìm kiếm và Chọn lọc', 'Giải thuật', ['cs206'], 63],
  ['cs304', 'Giải thuật tham lam', 'Giải thuật', ['cs301', 'cs303'], 41],
  ['cs305', 'Quy hoạch động', 'Giải thuật', ['cs304', 'cs303'], 18],
  ['cs306', 'Duyệt đồ thị (BFS/DFS)', 'Giải thuật', ['cs207'], 55],
  ['cs307', 'Đường đi ngắn nhất (Dijkstra)', 'Giải thuật', ['cs306'], 22],
  ['cs308', 'Cây bao trùm nhỏ nhất', 'Giải thuật', ['cs307'], 0],
  ['cs309', 'Quay lui và Phân nhánh-Cắt', 'Giải thuật', ['cs301'], 39],
  ['cs310', 'Bài toán NP-đầy đủ', 'Giải thuật', ['cs305', 'cs308'], 0],

  // ---- Hệ thống (chuỗi sâu) ----
  ['cs401', 'Tổ chức máy tính', 'Hệ thống', ['cs102'], 77],
  ['cs402', 'Ngôn ngữ assembly', 'Hệ thống', ['cs401'], 58],
  ['cs403', 'Lập trình C', 'Hệ thống', ['cs402', 'cs103'], 44],
  ['cs404', 'Hệ điều hành', 'Hệ thống', ['cs403', 'cs207'], 21],
  ['cs405', 'Lập lịch tiến trình', 'Hệ thống', ['cs404'], 0],
  ['cs406', 'Đồng thời và Đồng bộ', 'Hệ thống', ['cs404', 'cs306'], 8],
  ['cs407', 'Quản lý bộ nhớ', 'Hệ thống', ['cs404', 'cs205'], 0],
  ['cs408', 'Hệ thống tệp', 'Hệ thống', ['cs407'], 0],
  ['cs409', 'Bộ nhớ ảo', 'Hệ thống', ['cs408', 'cs406'], 0],
  ['cs410', 'Hệ phân tán', 'Hệ thống', ['cs409', 'cs307'], 0],

  // ---- Cơ sở dữ liệu ----
  ['cs501', 'Thiết kế Cơ sở dữ liệu và SQL', 'Cơ sở dữ liệu', ['cs102', 'cs104'], 69],
  ['cs502', 'Mô hình quan hệ và Chuẩn hóa', 'Cơ sở dữ liệu', ['cs501'], 83],
  ['cs503', 'Chỉ mục và Tối ưu truy vấn', 'Cơ sở dữ liệu', ['cs502', 'cs206'], 31],
  ['cs504', 'Giao dịch và ACID', 'Cơ sở dữ liệu', ['cs503', 'cs406'], 0],
  ['cs505', 'Cơ sở dữ liệu NoSQL', 'Cơ sở dữ liệu', ['cs502'], 26],

  // ---- Mạng ----
  ['cs601', 'Mạng máy tính', 'Mạng', ['cs401'], 76],
  ['cs602', 'TCP/IP và Lập trình Socket', 'Mạng', ['cs601', 'cs403'], 48],
  ['cs603', 'Định tuyến và Giao thức Internet', 'Mạng', ['cs602'], 19],
  ['cs604', 'An toàn mạng', 'Mạng', ['cs603', 'math104'], 0],
  ['cs605', 'Công nghệ Web', 'Mạng', ['cs602'], 52],

  // ---- Học máy (hội tụ rộng) ----
  ['cs701', 'Nền tảng Học máy', 'Học máy', ['math103', 'math104'], 43],
  ['cs702', 'Hồi quy tuyến tính', 'Học máy', ['cs701'], 67],
  ['cs703', 'Phân loại và Cây quyết định', 'Học máy', ['cs701', 'cs302'], 29],
  ['cs704', 'Mạng nơ-ron', 'Học máy', ['cs703', 'math103'], 11],
  ['cs705', 'Học không giám sát', 'Học máy', ['cs701'], 0],

  // ---- Lý thuyết ----
  ['cs801', 'Lý thuyết ôtômát', 'Lý thuyết', ['cs205'], 36],
  ['cs802', 'Trình biên dịch', 'Lý thuyết', ['cs801', 'cs403'], 0],
  ['cs803', 'Mã hóa học', 'Lý thuyết', ['math103', 'cs604'], 0],

  // ---- Các trường hợp đặc biệt ----
  /* Mã tiền kiến không tồn tại: đã bị xoá ở phía trên, không được khoá vĩnh viễn. */
  ['cs901', 'Bảo trì hệ thống cũ', 'Chuyên nghiệp', ['cs-legacy-deleted'], 40],
  /* Đã thành thạo dù chưa đáp ứng tiền kiến — thành tựu đã đạt không bị thu hồi. */
  ['cs902', 'Đồ án tốt nghiệp', 'Chuyên nghiệp', ['cs310', 'cs410'], 91],
] as const;

/** `status` is a display field only; the UI derives the authoritative one. */
const asStatus = (score: number): KnowledgeNodeStatus =>
  score >= 80 ? 'MASTERED' : score > 0 ? 'IN_PROGRESS' : 'LOCKED';

export const MOCK_KNOWLEDGE_NODES: readonly KnowledgeNodeData[] = SEEDS.map(
  ([id, title, topic, prerequisites, score]) => ({
    id,
    title,
    description: `${title} — một năng lực nguyên tử được đánh giá bằng bài kiểm tra chẩn đoán. Đạt thành thạo khi điểm không thấp hơn 80%.`,
    status: asStatus(score),
    masteryScore: score,
    prerequisites: [...prerequisites],
    relatedLessonCount: 1 + ((id.length + score) % 4),
    topic,
  }),
);

/** A couple of lessons per node, so the drawer has real content to list. */
export const MOCK_LESSONS_BY_NODE: Readonly<Record<string, readonly RelatedLesson[]>> =
  Object.fromEntries(
    MOCK_KNOWLEDGE_NODES.map((node) => [
      node.id,
      [
        {
          lessonId: Number.parseInt(node.id.replace(/\D/g, ''), 10) || 0,
          title: `${node.title} — Bài giảng`,
          materialType: 'DOCUMENT' as const,
          /* Kỹ năng đã thành thạo thì đã xem hết bài giảng. */
          percentComplete: node.masteryScore >= 80 ? 1 : (node.masteryScore % 100) / 120,
          durationMinutes: 45,
        },
        {
          lessonId: (Number.parseInt(node.id.replace(/\D/g, ''), 10) || 0) + 500,
          title: `${node.title} — Bài tập`,
          materialType: 'PDF' as const,
          percentComplete: 0,
          durationMinutes: 30,
        },
      ],
    ]),
  );

/** Every prerequisite pair as a `prerequisite -> dependent` edge. */
export const MOCK_PREREQUISITE_EDGES: readonly { source: string; target: string }[] =
  MOCK_KNOWLEDGE_NODES.flatMap((node) =>
    node.prerequisites.map((prerequisiteId) => ({
      source: prerequisiteId,
      target: node.id,
    })),
  );