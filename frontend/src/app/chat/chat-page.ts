import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  Component,
  ElementRef,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { ApiError, ApiService } from '../api.service';
import { ORDER_STATUS_STYLE, REFUND_STATUS_STYLE, formatReply, newConversationId } from '../format';
import { AgentAction, ChatMessage, Customer, Order, RefundRequest } from '../models';

const SUGGESTIONS = [
  'Where is my most recent order?',
  'I want a refund for ORD-1001, the left earbud does not charge.',
  'What is your return policy?',
  'How long does express shipping take?',
];

@Component({
  selector: 'app-chat-page',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  templateUrl: './chat-page.html',
})
export class ChatPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly scroller = viewChild<ElementRef<HTMLDivElement>>('scroller');

  protected readonly suggestions = SUGGESTIONS;
  protected readonly orderStyle = ORDER_STATUS_STYLE;
  protected readonly refundStyle = REFUND_STATUS_STYLE;
  protected readonly format = formatReply;

  protected readonly customers = signal<Customer[]>([]);
  protected readonly customersError = signal<string | null>(null);
  protected readonly customer = signal<Customer | null>(null);

  protected readonly orders = signal<Order[]>([]);
  protected readonly refunds = signal<RefundRequest[]>([]);
  protected readonly sidebarLoading = signal(false);
  protected readonly sidebarError = signal<string | null>(null);

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly sending = signal(false);
  protected readonly draft = signal('');
  protected readonly conversationId = signal(newConversationId());
  protected readonly showSidebar = signal(false);

  protected readonly canSend = computed(
    () => !!this.customer() && this.draft().trim().length > 0 && !this.sending(),
  );

  private nextId = 1;
  private lastQuestion = '';

  constructor() {
    // Keep the newest message in view.
    effect(() => {
      this.messages();
      this.sending();
      queueMicrotask(() => {
        const el = this.scroller()?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
      });
    });
  }

  ngOnInit(): void {
    this.api.customers().subscribe({
      next: (list) => {
        this.customers.set(list);
        if (list.length) this.selectCustomer(list[0]);
      },
      error: (e: ApiError) => this.customersError.set(e.message),
    });
  }

  protected onCustomerChange(email: string): void {
    const next = this.customers().find((c) => c.email === email);
    if (next) this.selectCustomer(next);
  }

  protected selectCustomer(c: Customer): void {
    this.customer.set(c);
    this.messages.set([]);
    this.conversationId.set(newConversationId());
    this.refreshSidebar();
  }

  protected refreshSidebar(): void {
    const c = this.customer();
    if (!c) return;
    this.sidebarLoading.set(true);
    this.sidebarError.set(null);
    forkJoin({ orders: this.api.orders(c.email), refunds: this.api.myRefunds(c.email) })
      .pipe(finalize(() => this.sidebarLoading.set(false)))
      .subscribe({
        next: ({ orders, refunds }) => {
          if (this.customer()?.email !== c.email) return; // customer switched meanwhile
          this.orders.set(orders);
          this.refunds.set(refunds);
        },
        error: (e: ApiError) => this.sidebarError.set(e.message),
      });
  }

  protected newConversation(): void {
    const c = this.customer();
    if (c) this.api.resetConversation(this.conversationId(), c.email).subscribe({ error: () => {} });
    this.conversationId.set(newConversationId());
    this.messages.set([]);
  }

  protected useSuggestion(text: string): void {
    this.draft.set(text);
    this.send();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send();
    }
  }

  protected retry(): void {
    if (!this.lastQuestion || this.sending()) return;
    this.messages.update((m) => m.filter((x) => x.role !== 'error'));
    this.ask(this.lastQuestion);
  }

  protected send(): void {
    if (!this.canSend()) return;
    const text = this.draft().trim();
    this.draft.set('');
    this.messages.update((m) => [...m, { id: this.nextId++, role: 'user', text }]);
    this.ask(text);
  }

  private ask(text: string): void {
    const c = this.customer();
    if (!c) return;
    this.lastQuestion = text;
    this.sending.set(true);
    this.api
      .chat({
        conversationId: this.conversationId(),
        customerEmail: c.email,
        customerName: c.name,
        message: text,
      })
      .subscribe({
        next: (res) => {
          this.messages.update((m) => [
            ...m,
            {
              id: this.nextId++,
              role: 'assistant',
              text: res.reply || '(The assistant returned an empty reply.)',
              actions: res.actions,
              sources: res.sources,
            },
          ]);
          if (res.actions.some((a) => a.type === 'REFUND_REQUESTED')) this.refreshSidebar();
          this.sending.set(false);
        },
        error: (e: ApiError) => {
          this.messages.update((m) => [...m, { id: this.nextId++, role: 'error', text: e.message }]);
          this.sending.set(false);
        },
      });
  }

  protected actionIcon(a: AgentAction): string {
    switch (a.type) {
      case 'REFUND_REQUESTED':
        return '⏳';
      case 'REFUND_NOT_ALLOWED':
        return '⛔';
      case 'ORDER_LOOKUP':
      case 'ORDERS_LISTED':
        return '📦';
      default:
        return '🔎';
    }
  }
}
