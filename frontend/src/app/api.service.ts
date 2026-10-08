import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import {
  AppInfo,
  ChatResponse,
  Customer,
  KnowledgeStatus,
  Order,
  RefundRequest,
  RefundStatus,
} from './models';

export const APPROVER_HEADER = 'X-Approver-Key';

/** Error with a message that is safe to show to the user. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export function toApiError(err: HttpErrorResponse): ApiError {
  if (err.status === 0) {
    return new ApiError(0, 'Cannot reach the SupportPilot API. Is the backend running on port 8080?');
  }
  const detail = typeof err.error?.detail === 'string' ? err.error.detail : null;
  return new ApiError(err.status, detail ?? `Request failed (${err.status})`);
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  info(): Observable<AppInfo> {
    return this.wrap(this.http.get<AppInfo>(`${this.base}/info`));
  }

  customers(): Observable<Customer[]> {
    return this.wrap(this.http.get<Customer[]>(`${this.base}/customers`));
  }

  orders(email: string): Observable<Order[]> {
    return this.wrap(this.http.get<Order[]>(`${this.base}/orders`, { params: { email } }));
  }

  myRefunds(email: string): Observable<RefundRequest[]> {
    return this.wrap(this.http.get<RefundRequest[]>(`${this.base}/refunds/mine`, { params: { email } }));
  }

  chat(body: {
    conversationId: string;
    customerEmail: string;
    customerName: string;
    message: string;
  }): Observable<ChatResponse> {
    return this.wrap(this.http.post<ChatResponse>(`${this.base}/chat`, body));
  }

  resetConversation(conversationId: string, email: string): Observable<void> {
    return this.wrap(
      this.http.delete<void>(`${this.base}/chat/${conversationId}`, { params: { email } }),
    );
  }

  refunds(key: string, status?: RefundStatus): Observable<RefundRequest[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.wrap(
      this.http.get<RefundRequest[]>(`${this.base}/refunds`, { params, headers: this.approver(key) }),
    );
  }

  decide(
    key: string,
    id: string,
    decision: 'approve' | 'reject',
    approverName: string,
    note: string,
  ): Observable<RefundRequest> {
    return this.wrap(
      this.http.post<RefundRequest>(
        `${this.base}/refunds/${id}/${decision}`,
        { approverName, note: note || null },
        { headers: this.approver(key) },
      ),
    );
  }

  reloadKnowledge(key: string): Observable<KnowledgeStatus> {
    return this.wrap(
      this.http.post<KnowledgeStatus>(`${this.base}/admin/knowledge/reload`, null, {
        headers: this.approver(key),
      }),
    );
  }

  private approver(key: string): HttpHeaders {
    return new HttpHeaders({ [APPROVER_HEADER]: key });
  }

  private wrap<T>(source: Observable<T>): Observable<T> {
    return source.pipe(catchError((err: HttpErrorResponse) => throwError(() => toApiError(err))));
  }
}
