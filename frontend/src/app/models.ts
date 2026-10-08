export type OrderStatus = 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'REFUNDED';
export type RefundStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface Customer {
  email: string;
  name: string;
}

export interface OrderItem {
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  id: string;
  customerName: string;
  status: OrderStatus;
  total: number;
  refundedAmount: number;
  currency: string;
  carrier: string | null;
  trackingNumber: string | null;
  orderedAt: string;
  shippedAt: string | null;
  deliveredAt: string | null;
  estimatedDelivery: string | null;
  items: OrderItem[];
}

export interface RefundRequest {
  id: string;
  orderId: string;
  customerEmail: string;
  amount: number;
  reason: string;
  status: RefundStatus;
  createdAt: string;
  decidedAt: string | null;
  decidedBy: string | null;
  decisionNote: string | null;
}

export type AgentActionType =
  | 'ORDER_LOOKUP'
  | 'ORDERS_LISTED'
  | 'REFUND_REQUESTED'
  | 'REFUND_NOT_ALLOWED'
  | 'REFUND_STATUS_CHECKED';

export interface AgentAction {
  type: AgentActionType;
  orderId: string | null;
  refundRequestId: string | null;
  status: string | null;
  amount: number | null;
  message: string;
}

export interface Source {
  source: string;
  title: string;
  excerpt: string;
}

export interface ChatResponse {
  conversationId: string;
  reply: string;
  actions: AgentAction[];
  sources: Source[];
}

export interface KnowledgeStatus {
  state: 'NOT_LOADED' | 'READY' | 'FAILED';
  files: number;
  chunks: number;
  error: string | null;
}

export interface AppInfo {
  chatProvider: string;
  chatModel: string;
  embeddingProvider: string;
  embeddingModel: string;
  knowledgeBase: KnowledgeStatus;
}

export interface ChatMessage {
  id: number;
  role: 'user' | 'assistant' | 'error';
  text: string;
  actions?: AgentAction[];
  sources?: Source[];
}
