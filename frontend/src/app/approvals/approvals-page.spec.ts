import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApproverKeyService } from '../approver-key.service';
import { RefundRequest } from '../models';
import { ApprovalsPage } from './approvals-page';

const PENDING: RefundRequest = {
  id: 'r1',
  orderId: 'ORD-1001',
  customerEmail: 'alice@example.com',
  amount: 129.99,
  reason: 'Left earbud does not charge',
  status: 'PENDING',
  createdAt: '2026-03-15T10:00:00Z',
  decidedAt: null,
  decidedBy: null,
  decisionNote: null,
};

describe('ApprovalsPage', () => {
  let fixture: ComponentFixture<ApprovalsPage>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) =>
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.trim() === label) as HTMLButtonElement;

  async function setup(signedIn: boolean) {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [ApprovalsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    if (signedIn) {
      const auth = TestBed.inject(ApproverKeyService);
      auth.setKey('secret');
      auth.setName('Dana');
    }
    fixture = TestBed.createComponent(ApprovalsPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  it('asks for the approver key first', async () => {
    await setup(false);
    expect(el().textContent).toContain('Approver console');
    expect(el().querySelector('input[type="password"]')).not.toBeNull();
  });

  it('lists pending requests and approves one', async () => {
    await setup(true);
    const list = http.expectOne((r) => r.url === '/api/refunds');
    expect(list.request.headers.get('X-Approver-Key')).toBe('secret');
    list.flush([PENDING]);
    await fixture.whenStable();
    expect(el().textContent).toContain('Left earbud does not charge');

    button('Approve').click();
    const decision = http.expectOne('/api/refunds/r1/approve');
    expect(decision.request.body).toEqual({ approverName: 'Dana', note: null });
    decision.flush({ ...PENDING, status: 'APPROVED', decidedBy: 'Dana' });
    await fixture.whenStable();

    expect(el().textContent).toContain('Refund for ORD-1001 approved.');
    expect(el().textContent).toContain('No refund requests waiting for review.');
  });

  it('requires a note to reject', async () => {
    await setup(true);
    http.expectOne((r) => r.url === '/api/refunds').flush([PENDING]);
    await fixture.whenStable();

    button('Reject').click();
    await fixture.whenStable();
    expect(el().textContent).toContain('Please add a note');
  });

  it('returns to the key form when the key is rejected', async () => {
    await setup(true);
    http
      .expectOne((r) => r.url === '/api/refunds')
      .flush({ detail: 'A valid X-Approver-Key header is required' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    expect(el().textContent).toContain('The approver key was not accepted');
    expect(el().querySelector('input[type="password"]')).not.toBeNull();
  });
});
