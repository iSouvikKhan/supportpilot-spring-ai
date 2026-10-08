import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App shell', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('shows the active provider and knowledge-base state', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/info')
      .flush({
        chatProvider: 'ollama',
        chatModel: 'qwen2.5:7b',
        embeddingProvider: 'ollama',
        embeddingModel: 'nomic-embed-text',
        knowledgeBase: { state: 'READY', files: 4, chunks: 27, error: null },
      });
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('ollama · qwen2.5:7b');
    expect(text).toContain('27 chunks');
  });

  it('shows an error banner when the API is unreachable', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/info')
      .error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Cannot reach the SupportPilot API');
  });
});
