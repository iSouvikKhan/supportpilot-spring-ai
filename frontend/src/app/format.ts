import { OrderStatus, RefundStatus } from './models';

const ESCAPES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

/**
 * Minimal, safe formatting for assistant replies: HTML is escaped first, then **bold** and
 * `code` are rendered. Line breaks are kept by CSS (whitespace-pre-wrap).
 */
export function formatReply(text: string): string {
  return text
    .replace(/[&<>"']/g, (c) => ESCAPES[c])
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code class="rounded bg-slate-100 px-1 text-[0.85em]">$1</code>');
}

export const ORDER_STATUS_STYLE: Record<OrderStatus, string> = {
  PROCESSING: 'bg-amber-50 text-amber-700 ring-amber-200',
  SHIPPED: 'bg-sky-50 text-sky-700 ring-sky-200',
  DELIVERED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  CANCELLED: 'bg-slate-100 text-slate-600 ring-slate-200',
  REFUNDED: 'bg-violet-50 text-violet-700 ring-violet-200',
};

export const REFUND_STATUS_STYLE: Record<RefundStatus, string> = {
  PENDING: 'bg-amber-50 text-amber-700 ring-amber-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  REJECTED: 'bg-rose-50 text-rose-700 ring-rose-200',
};

export function newConversationId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `conv-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}
