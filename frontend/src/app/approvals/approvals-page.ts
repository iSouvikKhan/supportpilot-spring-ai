import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ApiError, ApiService } from '../api.service';
import { ApproverKeyService } from '../approver-key.service';
import { REFUND_STATUS_STYLE } from '../format';
import { RefundRequest } from '../models';

type Tab = 'PENDING' | 'ALL';

@Component({
  selector: 'app-approvals-page',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  templateUrl: './approvals-page.html',
})
export class ApprovalsPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(ApproverKeyService);

  protected readonly statusStyle = REFUND_STATUS_STYLE;
  protected readonly tab = signal<Tab>('PENDING');
  protected readonly requests = signal<RefundRequest[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busyId = signal<string | null>(null);
  protected readonly notes = signal<Partial<Record<string, string>>>({});

  protected keyDraft = '';
  protected nameDraft = this.auth.name();

  protected readonly signedIn = computed(() => !!this.auth.key() && !!this.auth.name());

  ngOnInit(): void {
    if (this.signedIn()) this.load();
  }

  protected signIn(): void {
    if (!this.keyDraft.trim() || !this.nameDraft.trim()) return;
    this.auth.setKey(this.keyDraft);
    this.auth.setName(this.nameDraft);
    this.keyDraft = '';
    this.load();
  }

  protected signOut(): void {
    this.auth.clearKey();
    this.requests.set([]);
    this.error.set(null);
  }

  protected selectTab(tab: Tab): void {
    this.tab.set(tab);
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    const status = this.tab() === 'PENDING' ? 'PENDING' : undefined;
    this.api
      .refunds(this.auth.key(), status)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (list) => this.requests.set(list),
        error: (e: ApiError) => this.handleError(e),
      });
  }

  protected setNote(id: string, value: string): void {
    this.notes.update((n) => ({ ...n, [id]: value }));
  }

  protected decide(request: RefundRequest, decision: 'approve' | 'reject'): void {
    if (decision === 'reject' && !(this.notes()[request.id] ?? '').trim()) {
      this.error.set('Please add a note explaining why the refund is rejected.');
      return;
    }
    this.busyId.set(request.id);
    this.error.set(null);
    this.api
      .decide(this.auth.key(), request.id, decision, this.auth.name(), this.notes()[request.id] ?? '')
      .pipe(finalize(() => this.busyId.set(null)))
      .subscribe({
        next: (updated) => {
          this.notice.set(
            `Refund for ${updated.orderId} ${updated.status === 'APPROVED' ? 'approved' : 'rejected'}.`,
          );
          this.requests.update((list) =>
            this.tab() === 'PENDING'
              ? list.filter((r) => r.id !== updated.id)
              : list.map((r) => (r.id === updated.id ? updated : r)),
          );
        },
        error: (e: ApiError) => this.handleError(e),
      });
  }

  private handleError(e: ApiError): void {
    if (e.status === 401) {
      this.auth.clearKey();
      this.error.set('The approver key was not accepted. Please enter it again.');
      return;
    }
    if (e.status === 409) this.load();
    this.error.set(e.message);
  }
}
