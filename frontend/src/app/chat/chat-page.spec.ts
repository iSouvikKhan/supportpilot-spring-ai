import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatPage } from './chat-page';

const ALICE = { email: 'alice@example.com', name: 'Alice Johnson' };

describe('ChatPage', () => {
  let fixture: ComponentFixture<ChatPage>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;

  async function setup() {
    TestBed.configureTestingModule({
      imports: [ChatPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ChatPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/customers').flush([ALICE]);
    http.expectOne((r) => r.url === '/api/orders').flush([]);
    http.expectOne((r) => r.url === '/api/refunds/mine').flush([]);
    await fixture.whenStable();
  }

  async function sendMessage(text: string) {
    const textarea = el().querySelector('textarea')!;
    textarea.value = text;
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    el().querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('shows the empty state with suggestions for the first customer', async () => {
    await setup();
    expect(el().textContent).toContain('Hi Alice, how can we help?');
    expect(el().textContent).toContain('No orders for this customer.');
  });

  it('sends a message and renders the reply with a pending refund card', async () => {
    await setup();
    await sendMessage('Refund ORD-1001 please');

    const req = http.expectOne('/api/chat');
    expect(req.request.body).toMatchObject({
      customerEmail: ALICE.email,
      customerName: ALICE.name,
      message: 'Refund ORD-1001 please',
    });
    expect(el().querySelector('[aria-label="Assistant is typing"]')).not.toBeNull();

    req.flush({
      conversationId: req.request.body.conversationId,
      reply: 'I submitted your refund request for review.',
      actions: [
        {
          type: 'REFUND_REQUESTED',
          orderId: 'ORD-1001',
          refundRequestId: 'r1',
          status: 'PENDING',
          amount: 129.99,
          message: 'Refund request submitted for human approval',
        },
      ],
      sources: [{ source: 'returns-and-refunds.md', title: 'Refunds - Window', excerpt: '30 days' }],
    });
    // A created refund refreshes the sidebar.
    http.expectOne((r) => r.url === '/api/orders').flush([]);
    http.expectOne((r) => r.url === '/api/refunds/mine').flush([]);
    await fixture.whenStable();

    const text = el().textContent!;
    expect(text).toContain('I submitted your refund request for review.');
    expect(text).toContain('waiting for a human approver');
    expect(text).toContain('Based on 1 knowledge-base article');
  });

  it('shows an error with retry when the AI provider is down', async () => {
    await setup();
    await sendMessage('hello');
    http
      .expectOne('/api/chat')
      .flush(
        { detail: 'The AI provider is unavailable: Connection refused' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    await fixture.whenStable();
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Connection refused');

    (Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Try again')) as HTMLButtonElement).click();
    await fixture.whenStable();
    http.expectOne('/api/chat').flush({ conversationId: 'x', reply: 'Hi!', actions: [], sources: [] });
    await fixture.whenStable();
    expect(el().textContent).toContain('Hi!');
    expect(el().querySelector('[role="alert"]')).toBeNull();
  });
});
