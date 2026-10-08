import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APPROVER_HEADER, ApiError, ApiService } from './api.service';
import { formatReply } from './format';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the approver key header and status filter', () => {
    api.refunds('secret', 'PENDING').subscribe();
    const req = http.expectOne((r) => r.url === '/api/refunds');
    expect(req.request.headers.get(APPROVER_HEADER)).toBe('secret');
    expect(req.request.params.get('status')).toBe('PENDING');
    req.flush([]);
  });

  it('posts decisions with approver name and note', () => {
    api.decide('k', 'abc', 'reject', 'Dana', 'Outside window').subscribe();
    const req = http.expectOne('/api/refunds/abc/reject');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ approverName: 'Dana', note: 'Outside window' });
    req.flush({});
  });

  it('maps problem details to ApiError', () => {
    let error: ApiError | undefined;
    api.orders('a@b.co').subscribe({ error: (e) => (error = e) });
    http
      .expectOne((r) => r.url === '/api/orders')
      .flush({ detail: 'email: must be a well-formed email address' }, { status: 400, statusText: 'Bad Request' });
    expect(error).toBeInstanceOf(ApiError);
    expect(error?.status).toBe(400);
    expect(error?.message).toBe('email: must be a well-formed email address');
  });

  it('explains network failures', () => {
    let error: ApiError | undefined;
    api.customers().subscribe({ error: (e) => (error = e) });
    http.expectOne('/api/customers').error(new ProgressEvent('error'), { status: 0 });
    expect(error?.message).toContain('Cannot reach the SupportPilot API');
  });
});

describe('formatReply', () => {
  it('escapes HTML before applying formatting', () => {
    expect(formatReply('<img src=x onerror=alert(1)> **bold** `ORD-1`')).toBe(
      '&lt;img src=x onerror=alert(1)&gt; <strong>bold</strong> <code class="rounded bg-slate-100 px-1 text-[0.85em]">ORD-1</code>',
    );
  });
});
